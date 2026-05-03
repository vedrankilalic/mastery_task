package com.masteryapi.masteryapi.service;

import com.masteryapi.masteryapi.entity.LineItems;
import com.masteryapi.masteryapi.entity.ParsedDocumentData;
import com.masteryapi.masteryapi.types.DocumentsType;
import com.masteryapi.masteryapi.types.FileTypes;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileParserService {
    private static final Logger log = LoggerFactory.getLogger(FileParserService.class);

    private static final Pattern TXT_TOTAL_PATTERN = Pattern.compile("Total:\\s*(\\d+(?:\\.\\d+)?)\\s*([A-Za-z]{3})",
            Pattern.CASE_INSENSITIVE);

    @Value("${ocr.tesseract.path:tesseract}")
    private String tesseractCommand;

    public ParsedDocumentData parse(MultipartFile file, FileTypes fileType) {
        try {
            return switch (fileType) {
                case PDF -> parsePdf(file);
                case CSV -> parseCsv(file);
                case TXT -> parseTxt(file);
                case IMAGE -> parseImage(file);
            };
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse file: " + file.getOriginalFilename(), e);
        }
    }

    private ParsedDocumentData parsePdf(MultipartFile file) throws IOException {
        String text;
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            text = new PDFTextStripper().getText(document);
        }
        return InvoiceOcrTextParserService.parseFlattenedDocumentText(toLines(text));
    }

    private ParsedDocumentData parseImage(MultipartFile file) throws IOException {
        Path temp = writePreparedImageTemp(file);
        try {
            String text = runTesseractBestEffort(temp, file.getOriginalFilename());
            if (text == null || text.isBlank()) {
                log.warn("OCR returned no readable text for file {}", file.getOriginalFilename());
                return ParsedDocumentData.builder()
                        .lineItems(new ArrayList<>())
                        .build();
            }
            String[] lines = toLines(text);
            log.info("OCR (Tesseract) extracted {} lines for file {}", lines.length, file.getOriginalFilename());
            if (lines.length > 0) {
                log.info("OCR preview: {}", String.join(" | ", java.util.Arrays.stream(lines).limit(8).toList()));
            } else {
                log.warn("OCR returned no readable text for file {}", file.getOriginalFilename());
            }
            return InvoiceOcrTextParserService.parseFlattenedDocumentText(lines);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private Path writePreparedImageTemp(MultipartFile file) throws IOException {
        byte[] raw = file.getBytes();
        String suffix = file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")
                ? file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf('.'))
                : ".png";
        BufferedImage src;
        try {
            src = ImageIO.read(new ByteArrayInputStream(raw));
        } catch (Exception e) {
            src = null;
        }
        if (src == null) {
            Path fallback = Files.createTempFile("ocr-raw-", suffix);
            Files.write(fallback, raw);
            return fallback;
        }
        BufferedImage rgb = upscaleIfNarrowForOcr(toRgb(src));
        BufferedImage prepared = grayscaleWithContrastForOcr(rgb);
        Path temp = Files.createTempFile("ocr-", ".png");
        ImageIO.write(prepared, "png", temp.toFile());
        return temp;
    }

    private static BufferedImage grayscaleWithContrastForOcr(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) {
            return src;
        }
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        double sum = 0;
        int n = w * h;
        int[] lum = new int[n];
        int i = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                int l = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
                lum[i++] = l;
                sum += l;
            }
        }
        double mean = sum / n;
        double var = 0;
        for (int v : lum) {
            double d = v - mean;
            var += d * d;
        }
        double std = Math.sqrt(var / Math.max(1, n - 1));
        double alpha = std < 18 ? 1.45 : std < 35 ? 1.2 : 1.05;
        i = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = lum[i++];
                int adj = (int) Math.round((v - mean) * alpha + mean);
                if (adj < 0) {
                    adj = 0;
                } else if (adj > 255) {
                    adj = 255;
                }
                out.getRaster().setSample(x, y, 0, adj);
            }
        }
        return out;
    }

    private static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static BufferedImage upscaleIfNarrowForOcr(BufferedImage src) {
        int w = src.getWidth();
        if (w <= 0) {
            return src;
        }
        if (w >= 1400) {
            return src;
        }
        double scale = Math.min(3.0, 2400.0 / w);
        if (scale <= 1.05) {
            return src;
        }
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(src.getHeight() * scale));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(src, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private String runTesseractBestEffort(Path imagePath, String fileLabel) throws IOException {
        String primary = runTesseractStdout(imagePath, List.of("--oem", "1", "--psm", "3"));
        int s1 = ocrTextStrength(primary);
        if (s1 >= 100) {
            return primary;
        }
        String sparse = runTesseractStdout(imagePath, List.of("--oem", "1", "--psm", "11"));
        int s2 = ocrTextStrength(sparse);
        if (s2 > s1) {
            log.info("OCR PSM 11 improved extraction for {} ({} -> {} alphanumeric chars)", fileLabel, s1, s2);
            return sparse;
        }
        if (s1 < 50) {
            String block = runTesseractStdout(imagePath, List.of("--oem", "1", "--psm", "6"));
            int s3 = ocrTextStrength(block);
            if (s3 > s1) {
                log.info("OCR PSM 6 improved extraction for {} ({} -> {} alphanumeric chars)", fileLabel, s1, s3);
                return block;
            }
        }
        return primary != null && !primary.isBlank() ? primary : sparse;
    }

    private static int ocrTextStrength(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int n = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                n++;
            }
        }
        return n;
    }

    private String runTesseractStdout(Path imagePath, List<String> extraTesseractArgs) throws IOException {
        String language = readEnv("TESSERACT_LANGUAGE");
        if (language == null || language.isBlank()) {
            language = "eng";
        }

        List<String> cmd = new ArrayList<>(8 + extraTesseractArgs.size());
        cmd.add(tesseractCommand);
        cmd.add(imagePath.toAbsolutePath().toString());
        cmd.add("stdout");
        cmd.add("-l");
        cmd.add(language.trim());
        cmd.addAll(extraTesseractArgs);

        ProcessBuilder pb = new ProcessBuilder(cmd);

        String tessdata = readEnv("TESSDATA_PREFIX");
        if (tessdata != null && !tessdata.isBlank()) {
            pb.environment().put("TESSDATA_PREFIX", tessdata.trim());
        }

        pb.redirectErrorStream(true);

        Process process = pb.start();

        try {
            String output;
            try (InputStream inputStream = process.getInputStream()) {
                output = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new IOException("Tesseract command failed (" + exitCode + "): " + output);
            }

            return output;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Tesseract process was interrupted", e);
        }
    }

    private static String readEnv(String key) {
        String fromOs = System.getenv(key);
        if (fromOs != null && !fromOs.isBlank()) {
            return fromOs;
        }
        String fromProps = System.getProperty(key);
        return (fromProps != null && !fromProps.isBlank()) ? fromProps : null;
    }

    private String[] toLines(String text) {
        return text.lines()
                .map(InvoiceOcrTextParserService::normalizeTypographicDashes)
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toArray(String[]::new);
    }

    private ParsedDocumentData parseCsv(MultipartFile file) throws IOException {
        List<LineItems> items = new ArrayList<>();

        try (StringReader reader = new StringReader(new String(file.getBytes(), StandardCharsets.UTF_8));
                BufferedReader bufferedReader = new BufferedReader(reader)) {

            String header = bufferedReader.readLine();
            if (header == null) {
                return ParsedDocumentData.builder()
                        .documentType(DocumentsType.INVOICE)
                        .lineItems(items)
                        .build();
            }

            String line;
            int lineNo = 1;
            BigDecimal subtotal = BigDecimal.ZERO;
            while ((line = bufferedReader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length < 4) {
                    continue;
                }

                BigDecimal qty = InvoiceOcrTextParserService.parseNumber(parts[1]);
                BigDecimal unitPrice = InvoiceOcrTextParserService.parseNumber(parts[2]);
                BigDecimal lineTotal = InvoiceOcrTextParserService.parseNumber(parts[3]);
                subtotal = subtotal.add(lineTotal);

                items.add(LineItems.builder()
                        .lineNo(lineNo++)
                        .description(parts[0].trim())
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .lineTaxAmount(BigDecimal.ZERO)
                        .lineTotal(lineTotal)
                        .build());
            }

            return ParsedDocumentData.builder()
                    .documentType(DocumentsType.INVOICE)
                    .lineItems(items)
                    .subtotal(subtotal)
                    .taxAmount(BigDecimal.ZERO)
                    .discountAmount(BigDecimal.ZERO)
                    .totalAmount(subtotal)
                    .build();
        }
    }

    private ParsedDocumentData parseTxt(MultipartFile file) throws IOException {
        String text = new String(file.getBytes(), StandardCharsets.UTF_8).trim();
        String[] lines = text.split("\\R");

        ParsedDocumentData.ParsedDocumentDataBuilder builder = ParsedDocumentData.builder();

        if (lines.length > 0) {
            builder.documentType(DocumentsType.INVOICE);
            builder.documentNumber(lines[0].replace("Invoice", "").trim());
        }

        for (String line : lines) {
            Matcher matcher = TXT_TOTAL_PATTERN.matcher(line);
            if (matcher.find()) {
                BigDecimal total = InvoiceOcrTextParserService.parseNumber(matcher.group(1));
                builder.totalAmount(total);
                builder.subtotal(total);
                builder.taxAmount(BigDecimal.ZERO);
                builder.discountAmount(BigDecimal.ZERO);
                builder.currencyCode(matcher.group(2).toUpperCase(java.util.Locale.ROOT));
            }
        }

        return builder.lineItems(new ArrayList<>()).build();
    }
}

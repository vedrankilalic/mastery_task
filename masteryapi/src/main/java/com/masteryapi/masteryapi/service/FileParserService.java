package com.masteryapi.masteryapi.service;

import com.masteryapi.masteryapi.entity.LineItems;
import com.masteryapi.masteryapi.types.DocumentsType;
import com.masteryapi.masteryapi.types.FileTypes;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileParserService {
    private static final Logger log = LoggerFactory.getLogger(FileParserService.class);
    @Value("${ocr.tesseract.path:tesseract}")
    private String tesseractCommand;

    private static final Pattern SUPPLIER_PATTERN = Pattern.compile(
            "(?i)\\b(supplier|vendor|company)(?:\\s*[:\\-]\\s*|\\s+)(.+?)(?=\\s+\\bTotal\\s*[:\\-]?|\\z)");
    private static final Pattern COMPANY_NAME_LINE_PATTERN = Pattern
            .compile("(?i).*(ltd\\.?|llc|inc\\.?|d\\.o\\.o\\.?|gmbh).*");
    private static final Pattern NUMBER_PATTERN = Pattern
            .compile("(?i)(number|no\\.?|invoice\\s*no|invoice\\s*#|po\\s*no|po\\s*#)\\s*[:\\-]?\\s*([A-Za-z0-9\\-_/#]+)");
    private static final Pattern INVOICE_CODE_PATTERN = Pattern.compile("(?i)\\bINV[\\s\\-]*[A-Z0-9]{3,}\\b");
    private static final Pattern INV_NUMBER_FALLBACK_PATTERN = Pattern
            .compile("(?i)\\binv\\s*[#:\\-]?\\s*([A-Z0-9]{5,})\\b");
    private static final Pattern COMPANY_NAME_EXTRACT_PATTERN = Pattern
            .compile("(?i)([A-Za-z][A-Za-z\\s,&.-]{2,}(?:ltd\\.?|llc|inc\\.?|gmbh|d\\.o\\.o\\.?))");
    private static final Pattern DUE_DATE_PATTERN = Pattern
            .compile("(?i)due\\s*date\\s*[:\\-]?\\s*([A-Za-z0-9/\\- ]+)");
    private static final Pattern ISSUE_DATE_PATTERN = Pattern
            .compile("(?i)(issue\\s*date|invoice\\s*date)\\s*[:\\-]?\\s*([A-Za-z0-9/\\- ]+)");
    private static final Pattern GENERIC_DATE_LINE_PATTERN = Pattern
            .compile("(?i)^date\\s*[:\\-]?\\s*([A-Za-z0-9/\\- ]+)$");
    private static final Pattern SUBTOTAL_PATTERN = Pattern.compile(
            "(?i)sub\\s*total(?:\\s+without\\s+vat)?\\s*(?:[:\\-])?\\s*([$€£]?[0-9][0-9,\\.]*)(?:\\s*[A-Z]{3})?");
    private static final Pattern TAX_PATTERN = Pattern.compile(
            "(?i)(tax|vat(?:\\s*\\d+%\\s*of\\s*[$€£]?[0-9][0-9,\\.]*)?)\\s*(?:[:\\-])?\\s*([$€£]?[0-9][0-9,\\.]*)");
    private static final Pattern TAX_PCT_PARENS_PATTERN = Pattern
            .compile("(?i)tax\\s*\\([^)]*\\)\\s*[$€£]?\\s*([0-9][0-9,\\.]*)");
    private static final Pattern TAX_AMOUNT_AFTER_PERCENT_PATTERN = Pattern
            .compile("(?i)\\btax\\b[^\\n]*?\\d+\\s*%\\s*[$€£]?\\s*([0-9][0-9,\\.]+)");
    private static final Pattern VAT_PCT_THEN_AMOUNT_PATTERN = Pattern
            .compile("(?i)\\bvat\\s*\\d+%\\s*[$€£]?\\s*([0-9][0-9,\\.]+)");
    private static final Pattern TOTAL_PATTERN = Pattern
            .compile("(?i)total(?:\\s+[A-Z]{3})?\\s*(?:[:\\-])?\\s*([$€£]?[0-9][0-9,\\.]*)(?:\\s*[A-Z]{3})?");
    private static final Pattern TOTAL_DUE_PATTERN = Pattern
            .compile("(?i)total\\s*due\\s*[:\\-]?\\s*[$€£]?\\s*([0-9][0-9,\\.]*)");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("(?i)\\b(EUR|USD|BAM|GBP)\\b");
    private static final Pattern STRICT_ITEM_PATTERN = Pattern.compile(
            "^(.+?)\\s+[$€£]?(\\d+(?:\\.\\d+)?)\\s+(\\d+(?:\\.\\d+)?)\\s+[$€£]?(\\d+(?:\\.\\d+)?)$");
    private static final Pattern NUMERIC_TOKEN_PATTERN = Pattern.compile("[$€£]?\\d[\\d,]*(?:\\.\\d+)?");
    private static final Pattern TXT_TOTAL_PATTERN = Pattern.compile("Total:\\s*(\\d+(?:\\.\\d+)?)\\s*([A-Za-z]{3})",
            Pattern.CASE_INSENSITIVE);
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
    private static final Pattern OCR_INVOICE_NO_PATTERN = Pattern
            .compile("(?i)invoice\\s*no\\.?\\s*[:\\-]?\\s*([A-Za-z0-9\\s\\-_/#]+)");

    private static final Pattern INVOICE_INLINE_NUMBER_PATTERN = Pattern.compile("(?i)\\binvoice\\s*(\\d+)\\b");

    private static final Pattern INV_REF_PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])INV(?:\\s*#\\s*|\\s*-\\s*|\\s+)([A-Z0-9]{3,})(?![A-Za-z0-9])");

    private static final Pattern OCR_DUE_DATE_PATTERN = Pattern.compile(
            "(?i)due\\s*date\\s*[:\\-]?\\s*(\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4}|\\d{1,2}/\\d{1,2}/\\d{4}|\\d{4}-\\d{1,2}-\\d{1,2})");

    private static final Pattern GRAND_TOTAL_PATTERN = Pattern
            .compile("(?i)grand\\s*total\\s*[$€£]?\\s*([0-9][0-9,\\.]+)");

    private static final Pattern OCR_SUBTOTAL_PATTERN = Pattern
            .compile("(?i)sub\\s*total\\s*[$€£]?\\s*([0-9][0-9,\\.]+)");

    private static final Pattern OCR_TAX_AMOUNT_PATTERN = Pattern
            .compile("(?i)tax\\s*\\d+%\\s*[$€£]?\\s*([0-9][0-9,\\.]+)");

    private static final Pattern COMPANY_BLOCK_PATTERN = Pattern
            .compile("(?i)([A-Za-z][A-Za-z\\s,&.-]{2,}(?:ltd\\.?|llc|inc\\.?|gmbh|d\\.o\\.o\\.?))");

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
        return buildParsedDocumentFromLines(toLines(text));
    }

    private ParsedDocumentData parseImage(MultipartFile file) throws IOException {
        Path temp = writePreparedImageTemp(file);
        try {
            String text = runTesseractStdout(temp);
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
            return buildParsedDocumentFromLines(lines);
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
        BufferedImage prepared = upscaleIfNarrowForOcr(toRgb(src));
        Path temp = Files.createTempFile("ocr-", ".png");
        ImageIO.write(prepared, "png", temp.toFile());
        return temp;
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

    private String runTesseractStdout(Path imagePath) throws IOException {
        String language = readEnv("TESSERACT_LANGUAGE");
        if (language == null || language.isBlank()) {
            language = "eng";
        }

        ProcessBuilder pb = new ProcessBuilder(
                tesseractCommand,
                imagePath.toAbsolutePath().toString(),
                "stdout",
                "-l",
                language.trim());

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
                .map(FileParserService::normalizeTypographicDashes)
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toArray(String[]::new);
    }

    private static String normalizeTypographicDashes(String line) {
        return line.replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace('\u2212', '-');
    }

    private static String[] mergeLabelContinuationLines(String[] lines) {
        if (lines.length < 2) {
            return lines;
        }
        List<String> out = new ArrayList<>(lines.length);
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (i + 1 < lines.length && isBareDocumentNumberLabel(trimmed)) {
                String next = lines[i + 1].trim();
                if (plausibleDocumentIdContinuation(next)) {
                    out.add(trimmed + " " + next);
                    i++;
                    continue;
                }
            }
            if (i + 1 < lines.length && isBareTaxWithPercentLabel(trimmed)) {
                String next = lines[i + 1].trim();
                if (next.matches("(?i)^[$€£]?\\d[\\d,\\.]*$")) {
                    out.add(trimmed + " " + next);
                    i++;
                    continue;
                }
            }
            out.add(lines[i]);
        }
        return out.toArray(String[]::new);
    }

    private static boolean isBareDocumentNumberLabel(String t) {
        return t.matches("(?i)^(number|no\\.?|invoice\\s*no\\.?|invoice\\s*#)\\s*[:\\-]?\\s*$");
    }

    private static boolean isBareTaxWithPercentLabel(String t) {
        return t.matches("(?i)^tax\\s*\\([^)]*\\)\\s*$");
    }

    private static boolean plausibleDocumentIdContinuation(String next) {
        if (next.length() < 2) {
            return false;
        }
        if (next.matches("^\\d+$")) {
            return false;
        }
        String lower = next.toLowerCase(Locale.ROOT);
        if (lower.startsWith("date") || lower.startsWith("due") || lower.startsWith("supplier")
                || lower.startsWith("total") || lower.startsWith("sub") || lower.startsWith("tax")) {
            return false;
        }
        return next.matches("(?i)^[A-Za-z0-9][A-Za-z0-9\\-._/]*$");
    }

    private ParsedDocumentData buildParsedDocumentFromLines(String[] lines) {
        String[] merged = mergeLabelContinuationLines(lines);
        ParsedDocumentData.ParsedDocumentDataBuilder builder = parseHeaderAndTotals(merged);
        builder.lineItems(parsePdfLineItems(merged));
        return builder.build();
    }

    private ParsedDocumentData.ParsedDocumentDataBuilder parseHeaderAndTotals(String[] lines) {
        ParsedDocumentData.ParsedDocumentDataBuilder builder = ParsedDocumentData.builder();
        DocumentsType resolvedType = null;
        String supplierName = null;
        String documentNumber = null;
        LocalDate issueDate = null;
        LocalDate dueDate = null;
        String currencyCode = null;
        BigDecimal subtotalAmount = null;
        BigDecimal taxAmount = null;
        BigDecimal totalAmount = null;

        String joinedText = String.join(" ", lines)
                .replaceAll("\\s+", " ")
                .trim();

        String joinedLower = joinedText.toLowerCase(Locale.ROOT);

        if (joinedLower.contains("invoice")) {
            resolvedType = DocumentsType.INVOICE;
        } else if (joinedLower.contains("purchase order") || joinedLower.matches(".*\\bpo\\b.*")) {
            resolvedType = DocumentsType.PURCHASE_ORDER;
        }

        Matcher invoiceNoMatcher = OCR_INVOICE_NO_PATTERN.matcher(joinedText);
        if (invoiceNoMatcher.find()) {
            documentNumber = invoiceNoMatcher.group(1).trim().replaceAll("\\s+", "");
        }
        if (documentNumber == null) {
            Matcher inlineInvoiceNo = INVOICE_INLINE_NUMBER_PATTERN.matcher(joinedText);
            if (inlineInvoiceNo.find()) {
                documentNumber = inlineInvoiceNo.group(1).trim();
            }
        }
        if (documentNumber == null) {
            Matcher invRef = INV_REF_PATTERN.matcher(joinedText);
            if (invRef.find()) {
                documentNumber = "INV-" + invRef.group(1).toUpperCase(Locale.ROOT);
            }
        }

        Matcher dueDateMatcher = OCR_DUE_DATE_PATTERN.matcher(joinedText);
        if (dueDateMatcher.find()) {
            try {
                dueDate = parseDate(dueDateMatcher.group(1).trim());
            } catch (IllegalArgumentException ignored) {
            }
        }

        Matcher companyMatcher = COMPANY_BLOCK_PATTERN.matcher(joinedText);
        if (companyMatcher.find()) {
            supplierName = companyMatcher.group(1).trim();
        }

        Matcher subtotalOcrMatcher = OCR_SUBTOTAL_PATTERN.matcher(joinedText);
        if (subtotalOcrMatcher.find()) {
            subtotalAmount = parseNumber(subtotalOcrMatcher.group(1));
        }

        Matcher taxAfterPctJoin = TAX_AMOUNT_AFTER_PERCENT_PATTERN.matcher(joinedText);
        if (taxAfterPctJoin.find()) {
            taxAmount = parseNumber(taxAfterPctJoin.group(1));
        }
        if (taxAmount == null) {
            Matcher vatPctJoin = VAT_PCT_THEN_AMOUNT_PATTERN.matcher(joinedText);
            if (vatPctJoin.find()) {
                taxAmount = parseNumber(vatPctJoin.group(1));
            }
        }
        Matcher taxOcrMatcher = OCR_TAX_AMOUNT_PATTERN.matcher(joinedText);
        if (taxAmount == null && taxOcrMatcher.find()) {
            taxAmount = parseNumber(taxOcrMatcher.group(1));
        }
        if (taxAmount == null) {
            Matcher taxParensJoined = TAX_PCT_PARENS_PATTERN.matcher(joinedText);
            if (taxParensJoined.find()) {
                taxAmount = parseNumber(taxParensJoined.group(1));
            }
        }

        Matcher grandTotalMatcher = GRAND_TOTAL_PATTERN.matcher(joinedText);
        if (grandTotalMatcher.find()) {
            totalAmount = parseNumber(grandTotalMatcher.group(1));
        }

        for (String line : lines) {
            String normalizedLine = line.toLowerCase(Locale.ROOT);
            if (resolvedType == null) {
                if (normalizedLine.contains("invoice")) {
                    resolvedType = DocumentsType.INVOICE;
                } else if (normalizedLine.contains("purchase order") || normalizedLine.matches(".*\\bpo\\b.*")) {
                    resolvedType = DocumentsType.PURCHASE_ORDER;
                }
            }

            Matcher number = NUMBER_PATTERN.matcher(line);
            Matcher subtotalMatcher = SUBTOTAL_PATTERN.matcher(line);
            Matcher tax = TAX_PATTERN.matcher(line);
            Matcher total = TOTAL_PATTERN.matcher(line);
            Matcher currency = CURRENCY_PATTERN.matcher(line);

            if (supplierName == null) {
                Matcher supplier = SUPPLIER_PATTERN.matcher(line);
                if (supplier.find()) {
                    String candidate = supplier.group(2).trim();
                    if (candidate.length() >= 2 && !candidate.matches("^\\d+$")) {
                        supplierName = candidate;
                    }
                }
            }
            if (supplierName == null && COMPANY_NAME_LINE_PATTERN.matcher(line).matches()) {
                String candidate = line.trim();
                if (candidate.length() >= 4 && !candidate.matches(".*\\d{2,}.*")) {
                    supplierName = candidate;
                }
                Matcher companyNameMatcher = COMPANY_NAME_EXTRACT_PATTERN.matcher(candidate);
                if (companyNameMatcher.find()) {
                    supplierName = companyNameMatcher.group(1).trim();
                }
            }
            if (documentNumber == null && number.find()) {
                String parsedNumber = number.group(2).trim();
                if (!parsedNumber.equalsIgnoreCase("invoice")
                        && !parsedNumber.equalsIgnoreCase("date")
                        && !parsedNumber.equalsIgnoreCase("account")) {
                    documentNumber = parsedNumber;
                }
            }
            if (documentNumber == null) {
                Matcher invoiceCodeMatcher = INVOICE_CODE_PATTERN.matcher(line);
                if (invoiceCodeMatcher.find()) {
                    documentNumber = invoiceCodeMatcher.group().replaceAll("\\s+", "");
                }
            }
            if (documentNumber == null) {
                Matcher invFallbackMatcher = INV_NUMBER_FALLBACK_PATTERN.matcher(line);
                if (invFallbackMatcher.find()) {
                    documentNumber = "INV" + invFallbackMatcher.group(1);
                }
            }
            if (documentNumber == null) {
                Matcher inlineInv = INVOICE_INLINE_NUMBER_PATTERN.matcher(line);
                if (inlineInv.find()) {
                    documentNumber = inlineInv.group(1).trim();
                }
            }
            if (documentNumber == null) {
                Matcher invRefLine = INV_REF_PATTERN.matcher(line);
                if (invRefLine.find()) {
                    documentNumber = "INV-" + invRefLine.group(1).toUpperCase(Locale.ROOT);
                }
            }

            if (dueDate == null) {
                Matcher dueM = DUE_DATE_PATTERN.matcher(line);
                if (dueM.find()) {
                    try {
                        dueDate = parseDate(dueM.group(1).trim());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            if (issueDate == null) {
                Matcher issueM = ISSUE_DATE_PATTERN.matcher(line);
                if (issueM.find()) {
                    try {
                        issueDate = parseDate(issueM.group(2).trim());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            if (issueDate == null) {
                Matcher genericDate = GENERIC_DATE_LINE_PATTERN.matcher(line);
                if (genericDate.find() && !line.toLowerCase(Locale.ROOT).contains("due")) {
                    try {
                        issueDate = parseDate(genericDate.group(1).trim());
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            if (issueDate == null
                    && !normalizedLine.contains("due date")
                    && !normalizedLine.contains("due")) {
                issueDate = tryExtractStandaloneDate(line);
            }

            if (subtotalAmount == null && subtotalMatcher.find()) {
                subtotalAmount = parseNumber(subtotalMatcher.group(1));
            }
            if (taxAmount == null) {
                Matcher taxAfterPct = TAX_AMOUNT_AFTER_PERCENT_PATTERN.matcher(line);
                if (taxAfterPct.find()) {
                    taxAmount = parseNumber(taxAfterPct.group(1));
                }
            }
            if (taxAmount == null) {
                Matcher vatPctLine = VAT_PCT_THEN_AMOUNT_PATTERN.matcher(line);
                if (vatPctLine.find()) {
                    taxAmount = parseNumber(vatPctLine.group(1));
                }
            }
            if (taxAmount == null && tax.find()) {
                taxAmount = parseNumber(tax.group(2));
            }
            if (taxAmount == null) {
                Matcher taxParens = TAX_PCT_PARENS_PATTERN.matcher(line);
                if (taxParens.find()) {
                    taxAmount = parseNumber(taxParens.group(1));
                }
            }
            Matcher totalDueM = TOTAL_DUE_PATTERN.matcher(line);
            if (totalDueM.find()) {
                totalAmount = parseNumber(totalDueM.group(1));
            } else if (totalAmount == null
                && !normalizedLine.contains("sub total")
                && !normalizedLine.contains("subtotal")
                && total.find()) {
                totalAmount = parseNumber(total.group(1));
            }
            if (currencyCode == null && currency.find()) {
                currencyCode = currency.group(1).toUpperCase(Locale.ROOT);
            }
        }

        if (subtotalAmount == null && totalAmount != null && taxAmount != null) {
            subtotalAmount = totalAmount.subtract(taxAmount);
        }
        if (subtotalAmount != null && totalAmount != null
                && subtotalAmount.compareTo(totalAmount.multiply(BigDecimal.TEN)) > 0) {
            subtotalAmount = null;
        }
        if (taxAmount != null && totalAmount != null
                && taxAmount.compareTo(totalAmount.multiply(BigDecimal.TEN)) > 0) {
            taxAmount = null;
        }

        if (currencyCode == null) {
            currencyCode = inferCurrencyFromLines(lines);
        }

        if (documentNumber != null) {
            String dn = documentNumber.trim().toLowerCase(Locale.ROOT);
            if (dn.equals("invoice") || dn.equals("inv")) {
                documentNumber = null;
            }
        }
        if (supplierName != null) {
            String s = supplierName.toLowerCase(Locale.ROOT);
            if (s.contains("invoice no") || s.contains("invoice date") || s.contains("account")) {
                supplierName = null;
            }
        }
        if (supplierName != null && looksLikeBoilerplateOrTerms(supplierName.toLowerCase(Locale.ROOT))) {
            supplierName = null;
        }

        builder.documentType(resolvedType);
        builder.supplierName(supplierName);
        builder.documentNumber(documentNumber);
        builder.issueDate(issueDate);
        builder.dueDate(dueDate);
        builder.currencyCode(currencyCode);
        builder.subtotal(subtotalAmount);
        builder.taxAmount(taxAmount);
        builder.totalAmount(totalAmount);

        return builder;
    }

    private String inferCurrencyFromLines(String[] lines) {
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (line.contains("£") || lower.contains("gbp")) {
                return "GBP";
            }
            if (line.contains("€") || lower.contains("eur")) {
                return "EUR";
            }
            if (lower.contains("usd") || (line.contains("$") && !lower.contains("aud") && !lower.contains("cad"))) {
                return "USD";
            }
            if (lower.contains("bam") || lower.contains(" convertible mark")) {
                return "BAM";
            }
        }
        return null;
    }

    private List<LineItems> parsePdfLineItems(String[] lines) {
        List<LineItems> items = new ArrayList<>();

        int lineNo = 1;
        String pendingDescription = null;
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("terms") && lower.contains("condition")) {
                pendingDescription = null;
            }
            if (looksLikeBoilerplateOrTerms(lower)) {
                pendingDescription = null;
                continue;
            }
            if (lower.contains("category") && lower.contains("rate")
                    && (lower.contains("qty") || lower.contains("quantity") || lower.contains("price"))) {
                continue;
            }
            if (lower.startsWith("description")
                    || lower.startsWith("subtotal")
                    || lower.startsWith("tax")
                    || lower.startsWith("total")
                    || lower.contains("unit price")
                    || lower.contains("qty")
                    || lower.contains("quantity")) {
                continue;
            }

            if (lower.equals("invoice") || lower.equals("purchase order")) {
                continue;
            }
            if (looksLikeHeaderNoise(lower)) {
                continue;
            }

            Matcher strictMatcher = STRICT_ITEM_PATTERN.matcher(line);
            if (strictMatcher.matches()) {
                String description = strictMatcher.group(1).trim();
                BigDecimal n1 = parseNumber(strictMatcher.group(2));
                BigDecimal n2 = parseNumber(strictMatcher.group(3));
                BigDecimal n3 = parseNumber(strictMatcher.group(4));
                Optional<ResolvedLineAmounts> resolved = resolveQtyUnitLineTotal(n1, n2, n3);
                if (resolved.isPresent() && description.length() >= 2
                        && !looksLikeHeaderNoise(description.toLowerCase(Locale.ROOT))) {
                    ResolvedLineAmounts r = resolved.get();
                    items.add(LineItems.builder()
                            .lineNo(lineNo++)
                            .description(description)
                            .quantity(r.quantity())
                            .unitPrice(r.unitPrice())
                            .lineTotal(r.lineTotal())
                            .lineTaxAmount(BigDecimal.ZERO)
                            .build());
                    pendingDescription = null;
                    continue;
                }
            }

            Matcher tokenMatcher = NUMERIC_TOKEN_PATTERN.matcher(line);
            List<String> tokens = new ArrayList<>();
            int firstNumberIndex = -1;
            while (tokenMatcher.find()) {
                if (firstNumberIndex == -1) {
                    firstNumberIndex = tokenMatcher.start();
                }
                tokens.add(tokenMatcher.group());
            }

            if (tokens.size() < 3 || tokens.size() > 4) {
                if (tokens.size() < 3 && lineHasLetters(line) && !looksLikeHeaderNoise(lower)) {
                    pendingDescription = mergePendingDescription(pendingDescription, line.trim());
                }
                continue;
            }

            String description;
            if (firstNumberIndex > 0) {
                description = mergePendingDescription(pendingDescription, line.substring(0, firstNumberIndex).trim());
            } else if (pendingDescription != null && !pendingDescription.isBlank()) {
                description = pendingDescription.trim();
            } else {
                continue;
            }

            if (description.length() < 2 || looksLikeHeaderNoise(description.toLowerCase(Locale.ROOT))) {
                pendingDescription = null;
                continue;
            }

            BigDecimal quantity;
            BigDecimal unitPrice;
            BigDecimal lineTax = BigDecimal.ZERO;
            BigDecimal lineTotal;

            if (tokens.size() >= 4) {
                int size = tokens.size();
                quantity = parseNumber(tokens.get(size - 4));
                unitPrice = parseNumber(tokens.get(size - 3));
                lineTax = parseNumber(tokens.get(size - 2));
                lineTotal = parseNumber(tokens.get(size - 1));
            } else {
                BigDecimal t0 = parseNumber(tokens.get(0));
                BigDecimal t1 = parseNumber(tokens.get(1));
                BigDecimal t2 = parseNumber(tokens.get(2));
                Optional<ResolvedLineAmounts> resolved = resolveQtyUnitLineTotal(t0, t1, t2);
                if (resolved.isEmpty()) {
                    continue;
                }
                ResolvedLineAmounts r = resolved.get();
                quantity = r.quantity();
                unitPrice = r.unitPrice();
                lineTotal = r.lineTotal();
            }

            if (quantity.compareTo(BigDecimal.ZERO) <= 0
                    || quantity.compareTo(BigDecimal.valueOf(1000)) > 0
                    || lineTotal.compareTo(BigDecimal.ZERO) < 0) {
                continue;
            }

            items.add(LineItems.builder()
                    .lineNo(lineNo++)
                    .description(description)
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .lineTaxAmount(lineTax)
                    .lineTotal(lineTotal)
                    .build());
            pendingDescription = null;
        }

        return items;
    }

    private static String mergePendingDescription(String pending, String part) {
        if (pending == null || pending.isBlank()) {
            return part;
        }
        if (part == null || part.isBlank()) {
            return pending;
        }
        return pending.trim() + " " + part.trim();
    }

    private static boolean lineHasLetters(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (Character.isLetter(line.codePointAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static Optional<ResolvedLineAmounts> resolveQtyUnitLineTotal(BigDecimal a, BigDecimal b, BigDecimal lineTotal) {
        if (lineTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        BigDecimal prod = a.multiply(b);
        if (!amountsClose(prod, lineTotal)) {
            return Optional.empty();
        }
        BigDecimal quantity;
        BigDecimal unitPrice;
        if (a.compareTo(BigDecimal.valueOf(200)) >= 0 && b.compareTo(BigDecimal.valueOf(200)) <= 0) {
            quantity = b;
            unitPrice = a;
        } else if (b.compareTo(BigDecimal.valueOf(200)) >= 0 && a.compareTo(BigDecimal.valueOf(200)) <= 0) {
            quantity = a;
            unitPrice = b;
        } else if (a.compareTo(BigDecimal.valueOf(1000)) <= 0 && b.compareTo(BigDecimal.valueOf(1000)) <= 0) {
            quantity = a;
            unitPrice = b;
        } else {
            quantity = a.min(b);
            unitPrice = a.max(b);
        }
        if (quantity.compareTo(BigDecimal.ZERO) <= 0 || quantity.compareTo(BigDecimal.valueOf(1000)) > 0) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedLineAmounts(quantity, unitPrice, lineTotal));
    }

    private static boolean amountsClose(BigDecimal a, BigDecimal b) {
        BigDecimal diff = a.subtract(b).abs();
        BigDecimal scale = a.abs().max(b.abs());
        BigDecimal relativeTol = scale.multiply(new BigDecimal("0.02"));
        BigDecimal tol = relativeTol.max(new BigDecimal("0.05"));
        return diff.compareTo(tol) <= 0;
    }

    private record ResolvedLineAmounts(BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
    }

    private boolean looksLikeHeaderNoise(String lower) {
        return lower.contains("invoice no")
                || lower.contains("invoice date")
                || lower.contains("account")
                || lower.contains("invoice to")
                || lower.contains("payment method")
                || lower.contains("bank")
                || lower.contains("accnumber")
                || lower.contains("address")
                || lower.contains("street")
                || lower.contains("united state")
                || lower.contains("phone")
                || lower.contains("www.")
                || lower.contains("cash payment");
    }

    private static boolean looksLikeBoilerplateOrTerms(String lower) {
        return lower.contains("lorem")
                || lower.contains("ipsum")
                || lower.contains("adipiscing")
                || lower.contains("condimentum")
                || lower.contains("sollicitudin")
                || (lower.contains("terms") && lower.contains("condition"));
    }

    private LocalDate tryExtractStandaloneDate(String line) {
        Matcher matcher = Pattern.compile("(?i)\\b(\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4})\\b").matcher(line);
        if (!matcher.find()) {
            return null;
        }
        try {
            return parseDate(matcher.group(1));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private ParsedDocumentData parseCsv(MultipartFile file) throws IOException {
        List<LineItems> items = new ArrayList<>();

        try (StringReader reader = new StringReader(new String(file.getBytes(), StandardCharsets.UTF_8));
                BufferedReader bufferedReader = new java.io.BufferedReader(reader)) {

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

                BigDecimal qty = parseNumber(parts[1]);
                BigDecimal unitPrice = parseNumber(parts[2]);
                BigDecimal lineTotal = parseNumber(parts[3]);
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
                BigDecimal total = parseNumber(matcher.group(1));
                builder.totalAmount(total);
                builder.subtotal(total);
                builder.taxAmount(BigDecimal.ZERO);
                builder.currencyCode(matcher.group(2).toUpperCase(Locale.ROOT));
            }
        }

        return builder.lineItems(new ArrayList<>()).build();
    }

    private LocalDate parseDate(String raw) {
        String normalized = raw.trim().replaceAll("\\s+", " ");
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(normalized, formatter);
            } catch (DateTimeException ignored) {
            }
        }
        throw new IllegalArgumentException("Unsupported date format: " + raw);
    }

    private BigDecimal parseNumber(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Number is null");
        }

        String cleaned = raw.trim()
                .replaceAll("[^0-9,\\.\\-]", "")
                .replace(",", "");

        if (cleaned.isBlank() || cleaned.equals("-") || cleaned.equals(".")) {
            throw new IllegalArgumentException("Invalid number: " + raw);
        }

        return new BigDecimal(cleaned);
    }

    @Builder
    public record ParsedDocumentData(
            DocumentsType documentType,
            String supplierName,
            String documentNumber,
            LocalDate issueDate,
            LocalDate dueDate,
            String currencyCode,
            BigDecimal subtotal,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            List<LineItems> lineItems) {
    }
}

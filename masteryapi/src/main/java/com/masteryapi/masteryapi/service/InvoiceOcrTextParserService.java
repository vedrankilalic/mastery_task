package com.masteryapi.masteryapi.service;

import com.masteryapi.masteryapi.entity.LineItems;
import com.masteryapi.masteryapi.entity.ParsedDocumentData;
import com.masteryapi.masteryapi.types.DocumentsType;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InvoiceOcrTextParserService {

    private InvoiceOcrTextParserService() {
    }

    public static ParsedDocumentData parseFlattenedDocumentText(String[] rawLines) {
        String[] merged = mergeLabelContinuationLines(rawLines);
        ParsedDocumentData.ParsedDocumentDataBuilder builder = parseHeaderAndTotals(merged);
        return builder.lineItems(parseLineItems(merged)).build();
    }

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
    /** "Discount 20% $1080" or "Discount $1080" */
    private static final Pattern DISCOUNT_AMOUNT_PATTERN = Pattern.compile(
            "(?i)\\bdiscount\\b\\s*(?:\\d+\\s*%)?\\s*[$€£]?\\s*([0-9][0-9,\\.]+)");
    /** "Discount 20%" when amount is on the same line only as percent */
    private static final Pattern DISCOUNT_PERCENT_PATTERN = Pattern.compile("(?i)\\bdiscount\\s*(\\d{1,2})\\s*%");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("(?i)\\b(EUR|USD|BAM|GBP)\\b");
    private static final Pattern STRICT_ITEM_PATTERN = Pattern.compile(
            "^(.+?)\\s+[$€£]?(\\d+(?:\\.\\d+)?)\\s+(\\d+(?:\\.\\d+)?)\\s+[$€£]?(\\d+(?:\\.\\d+)?)$");
    private static final Pattern NUMERIC_TOKEN_PATTERN = Pattern.compile("[$€£]?\\d[\\d,]*(?:\\.\\d+)?");
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
  
    private static final Pattern OCR_INVOICE_NO_PATTERN = Pattern.compile(
            "(?i)invoice\\s*no\\.?\\s*[:\\-]?\\s*((?:INV\\s*#\\s*|INV\\s*-\\s*)?[A-Za-z0-9][A-Za-z0-9\\-_/#]{0,30})");

    private static final Pattern INVOICE_INLINE_NUMBER_PATTERN = Pattern.compile("(?i)\\binvoice\\s*(\\d+)\\b");

    private static final Pattern INV_REF_PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])INV(?:\\s*#\\s*|\\s*-\\s*|\\s+)([A-Z0-9]{3,})(?![A-Za-z0-9])");

   
    private static final Pattern INVOICE_TO_COMPANY_PATTERN = Pattern.compile(
            "(?i)invoice\\s*to\\s*[:\\-]?\\s*(.+?)(?=\\s+\\d+[\\s,]|\\s+phone|\\s+web|\\n|$)");

    private static final Pattern OCR_DUE_DATE_PATTERN = Pattern.compile(
            "(?i)due\\s*date\\s*[:\\-]?\\s*(\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4}|\\d{1,2}/\\d{1,2}/\\d{4}|\\d{4}-\\d{1,2}-\\d{1,2})");

    private static final Pattern GRAND_TOTAL_PATTERN = Pattern
            .compile("(?i)grand\\s*total\\s*[$€£]?\\s*([0-9][0-9,\\.]+)");

    private static final Pattern OCR_SUBTOTAL_PATTERN = Pattern
            .compile("(?i)sub\\s*total\\s*[$€£]?\\s*([0-9][0-9,\\.]+)");

    private static final Pattern OCR_TAX_AMOUNT_PATTERN = Pattern
            .compile("(?i)tax\\s*\\d+%\\s*[$€£]?\\s*([0-9][0-9,\\.]+)");

    
    private static final Pattern COMPANY_BLOCK_PATTERN = Pattern.compile(
            "(?i)([A-Za-z][A-Za-z\\s,&.-]{2,}(?:(?:pvt\\.?\\s*)?ltd\\.?|llc|inc\\.?|gmbh|d\\.o\\.o\\.?))");

    public static String normalizeTypographicDashes(String line) {
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

    private static ParsedDocumentData.ParsedDocumentDataBuilder parseHeaderAndTotals(String[] lines) {
        ParsedDocumentData.ParsedDocumentDataBuilder builder = ParsedDocumentData.builder();
        DocumentsType resolvedType = null;
        String supplierName = null;
        String documentNumber = null;
        LocalDate issueDate = null;
        LocalDate dueDate = null;
        String currencyCode = null;
        BigDecimal subtotalAmount = null;
        BigDecimal taxAmount = null;
        BigDecimal discountAmount = null;
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

        Matcher invoiceToForSupplier = INVOICE_TO_COMPANY_PATTERN.matcher(joinedText);
        if (invoiceToForSupplier.find()) {
            String rawBillTo = invoiceToForSupplier.group(1).trim();
            Matcher companyFromBillTo = COMPANY_BLOCK_PATTERN.matcher(rawBillTo);
            if (companyFromBillTo.find()) {
                supplierName = companyFromBillTo.group(1).trim();
            } else {
                String firstSegment = rawBillTo.split(",")[0].trim();
                if (firstSegment.length() >= 3 && firstSegment.length() <= 160) {
                    supplierName = firstSegment;
                }
            }
        }

        Matcher invRefJoined = INV_REF_PATTERN.matcher(joinedText);
        if (invRefJoined.find()) {
            documentNumber = "INV-" + invRefJoined.group(1).toUpperCase(Locale.ROOT);
        }
        if (documentNumber == null) {
            Matcher invNumFallbackJoined = INV_NUMBER_FALLBACK_PATTERN.matcher(joinedText);
            if (invNumFallbackJoined.find()) {
                documentNumber = "INV" + invNumFallbackJoined.group(1);
            }
        }
        Matcher invoiceNoMatcher = OCR_INVOICE_NO_PATTERN.matcher(joinedText);
        if (documentNumber == null && invoiceNoMatcher.find()) {
            documentNumber = invoiceNoMatcher.group(1).trim().replaceAll("\\s+", " ").trim();
        }
        if (documentNumber == null) {
            Matcher inlineInvoiceNo = INVOICE_INLINE_NUMBER_PATTERN.matcher(joinedText);
            if (inlineInvoiceNo.find()) {
                documentNumber = inlineInvoiceNo.group(1).trim();
            }
        }

        Matcher dueDateMatcher = OCR_DUE_DATE_PATTERN.matcher(joinedText);
        if (dueDateMatcher.find()) {
            try {
                dueDate = parseDate(dueDateMatcher.group(1).trim());
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (supplierName == null) {
            Matcher companyMatcher = COMPANY_BLOCK_PATTERN.matcher(joinedText);
            while (companyMatcher.find()) {
                String companyCandidate = companyMatcher.group(1).trim();
                if (companyCandidate.matches("(?i).*\\bbank\\b.*")) {
                    continue;
                }
                supplierName = companyCandidate;
                break;
            }
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

        Matcher discountAmtJoin = DISCOUNT_AMOUNT_PATTERN.matcher(joinedText);
        if (discountAmtJoin.find()) {
            try {
                discountAmount = parseNumber(discountAmtJoin.group(1));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (discountAmount == null) {
            Matcher discountPctJoin = DISCOUNT_PERCENT_PATTERN.matcher(joinedText);
            if (discountPctJoin.find()) {
                try {
                    discountAmount = new BigDecimal(discountPctJoin.group(1).trim());
                } catch (NumberFormatException ignored) {
                }
            }
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
                && !normalizedLine.contains("discount")
                && total.find()) {
                totalAmount = parseNumber(total.group(1));
            }
            if (discountAmount == null) {
                Matcher discLine = DISCOUNT_AMOUNT_PATTERN.matcher(line);
                if (discLine.find()) {
                    try {
                        discountAmount = parseNumber(discLine.group(1));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            if (discountAmount == null) {
                Matcher discPctLine = DISCOUNT_PERCENT_PATTERN.matcher(line);
                if (discPctLine.find()) {
                    try {
                        discountAmount = new BigDecimal(discPctLine.group(1).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
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
        if (documentNumber != null && looksLikeGluedInvoiceLabels(documentNumber)) {
            documentNumber = null;
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
        builder.discountAmount(discountAmount);
        builder.totalAmount(totalAmount);

        return builder;
    }

    private static String inferCurrencyFromLines(String[] lines) {
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


    private static boolean looksLikeInvoiceSummaryLine(String lower) {
        if (lower.matches("(?i)^\\s*sub\\s*total\\b.*")) {
            return true;
        }
        if (lower.matches("(?i)^\\s*grand\\s*total\\b.*")) {
            return true;
        }
        if (lower.matches("(?i)^\\s*discount\\b.*")) {
            return true;
        }
        return lower.contains("%")
                && (lower.matches("(?i).*\\btax\\b.*") || lower.matches("(?i).*\\bvat\\b.*"));
    }

  
    private static boolean looksLikeProductTableHeaderRow(String lower, String line) {
        if (lineLooksLikeLineItemAmounts(line)) {
            return false;
        }
        boolean hasQty = lower.contains("quantity") || lower.matches(".*\\bqty\\b.*");
        boolean hasPrice = lower.contains("price");
        boolean hasTotal = lower.contains("total");
        return hasQty && hasPrice && hasTotal;
    }

    private static List<LineItems> parseLineItems(String[] lines) {
        List<LineItems> items = new ArrayList<>();

        int lineNo = 1;
        String pendingDescription = null;
        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.contains("terms") && lower.contains("condition")) {
                pendingDescription = null;
            }
            if (looksLikeInvoiceSummaryLine(lower)) {
                pendingDescription = null;
                continue;
            }
            if (looksLikeBoilerplateOrTerms(lower)) {
                if (lineLooksLikeLineItemAmounts(line)) {
                    
                } else {
                    if (!line.matches(".*\\d.*") && lineHasLetters(line) && !looksLikeHeaderNoise(lower)) {
                        pendingDescription = mergePendingDescription(pendingDescription, line.trim());
                    } else {
                        pendingDescription = null;
                    }
                    continue;
                }
            }
            if (lower.contains("category") && lower.contains("rate")
                    && (lower.contains("qty") || lower.contains("quantity") || lower.contains("price"))) {
                continue;
            }
            if (looksLikeProductTableHeaderRow(lower, line)) {
                continue;
            }
            if (lower.startsWith("description")
                    || lower.startsWith("subtotal")
                    || lower.startsWith("tax")
                    || lower.startsWith("total")
                    || lower.contains("unit price")) {
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
            List<Integer> tokenStarts = new ArrayList<>();
            while (tokenMatcher.find()) {
                tokens.add(tokenMatcher.group());
                tokenStarts.add(tokenMatcher.start());
            }
            int nTok = tokens.size();
            if (nTok < 3) {
                if (lineHasLetters(line) && !looksLikeHeaderNoise(lower)) {
                    pendingDescription = mergePendingDescription(pendingDescription, line.trim());
                }
                continue;
            }

            Optional<ParsedFourTokens> fourTokens = Optional.empty();
            Optional<ResolvedLineAmounts> threeTokens = Optional.empty();
            int amountRegionStart;
            if (nTok >= 4) {
                fourTokens = parseFourTokensToLineAmounts(
                        parseNumber(tokens.get(nTok - 4)),
                        parseNumber(tokens.get(nTok - 3)),
                        parseNumber(tokens.get(nTok - 2)),
                        parseNumber(tokens.get(nTok - 1)));
            }
            if (fourTokens.isPresent()) {
                amountRegionStart = tokenStarts.get(nTok - 4);
            } else {
                threeTokens = resolveQtyUnitLineTotal(
                        parseNumber(tokens.get(nTok - 3)),
                        parseNumber(tokens.get(nTok - 2)),
                        parseNumber(tokens.get(nTok - 1)));
                if (threeTokens.isEmpty()) {
                    continue;
                }
                amountRegionStart = tokenStarts.get(nTok - 3);
            }

            String description;
            if (amountRegionStart > 0) {
                description = mergePendingDescription(pendingDescription, line.substring(0, amountRegionStart).trim());
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
            if (fourTokens.isPresent()) {
                ParsedFourTokens p = fourTokens.get();
                quantity = p.quantity();
                unitPrice = p.unitPrice();
                lineTax = p.lineTax();
                lineTotal = p.lineTotal();
            } else {
                ResolvedLineAmounts r = threeTokens.get();
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

    private record ParsedFourTokens(BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTax, BigDecimal lineTotal) {
    }


    private static Optional<ParsedFourTokens> parseFourTokensToLineAmounts(BigDecimal a, BigDecimal b, BigDecimal c,
            BigDecimal d) {
        if (amountsClose(a.multiply(b).add(c), d)) {
            return Optional.of(new ParsedFourTokens(a, b, c, d));
        }
        if (amountsClose(a.multiply(b), d)) {
            return Optional.of(new ParsedFourTokens(a, b, BigDecimal.ZERO, d));
        }
        Optional<ResolvedLineAmounts> skipMiddle = resolveQtyUnitLineTotal(a, b, d);
        if (skipMiddle.isPresent()) {
            ResolvedLineAmounts r = skipMiddle.get();
            return Optional.of(new ParsedFourTokens(r.quantity(), r.unitPrice(), BigDecimal.ZERO, r.lineTotal()));
        }
        Optional<ResolvedLineAmounts> lastThree = resolveQtyUnitLineTotal(b, c, d);
        if (lastThree.isPresent()) {
            ResolvedLineAmounts r = lastThree.get();
            return Optional.of(new ParsedFourTokens(r.quantity(), r.unitPrice(), BigDecimal.ZERO, r.lineTotal()));
        }
        return Optional.empty();
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

    private static boolean looksLikeGluedInvoiceLabels(String documentNumber) {
        if (documentNumber == null || documentNumber.isBlank()) {
            return false;
        }
        String collapsed = documentNumber.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (collapsed.contains("INVOICE") && collapsed.contains("DATE") && collapsed.contains("ACCOUNT")) {
            return true;
        }
        return collapsed.length() > 18 && !documentNumber.matches(".*\\d.*");
    }

    private static boolean looksLikeHeaderNoise(String lower) {
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

        private static boolean lineLooksLikeLineItemAmounts(String line) {
        Matcher m = NUMERIC_TOKEN_PATTERN.matcher(line);
        int n = 0;
        while (m.find()) {
            n++;
        }
        if (n >= 3) {
            return true;
        }
        return line.contains("$") && n >= 2;
    }

    private static LocalDate tryExtractStandaloneDate(String line) {
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

    public static LocalDate parseDate(String raw) {
        String normalized = raw.trim().replaceAll("\\s+", " ");
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(normalized, formatter);
            } catch (DateTimeException ignored) {
            }
        }
        throw new IllegalArgumentException("Unsupported date format: " + raw);
    }

    public static BigDecimal parseNumber(String raw) {
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
}

package com.masteryapi.masteryapi.service;

import com.masteryapi.masteryapi.dto.DocumentsDto;
import com.masteryapi.masteryapi.dto.LineItemRequestDto;
import com.masteryapi.masteryapi.dto.UpdateDocumentRequestDto;
import com.masteryapi.masteryapi.entity.Documents;
import com.masteryapi.masteryapi.entity.DocumentsRevision;
import com.masteryapi.masteryapi.entity.LineItems;
import com.masteryapi.masteryapi.entity.ParsedDocumentData;
import com.masteryapi.masteryapi.entity.ValidationIssues;
import com.masteryapi.masteryapi.mapper.DocumentsMapper;
import com.masteryapi.masteryapi.types.DocumentsStatus;
import com.masteryapi.masteryapi.types.FileTypes;
import com.masteryapi.masteryapi.types.IssueTypes;
import com.masteryapi.masteryapi.types.IssuesSeverity;
import com.masteryapi.masteryapi.repository.DocumentsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DocumentsService {

    private final DocumentsRepository documentRepository;
    private final FileParserService fileParserService;

    public List<DocumentsDto> fetchAllDocuments() {
        return documentRepository.findAll()
                .stream()
                .map(DocumentsMapper::mapToDocumentsDto)
                .toList();
    }

    public DocumentsDto fetchDocumentById(Long id) {
        Documents document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with id: " + id));
    
        return DocumentsMapper.mapToDocumentsDto(document);
    }

    @Transactional
    public void deleteDocumentById(Long id) {
        if (!documentRepository.existsById(id)) {
            throw new IllegalArgumentException("Document not found with id: " + id);
        }
        documentRepository.deleteById(id);
    }

    @Transactional
    public List<DocumentsDto> upload(List<MultipartFile> files) {
        List<DocumentsDto> uploaded = files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .map(this::saveDocumentMetadata)
                .toList();

        if (uploaded.isEmpty()) {
            throw new IllegalArgumentException("No valid files uploaded");
        }

        return uploaded;
    }

    @Transactional
    public DocumentsDto updateDocument(Long id, UpdateDocumentRequestDto request) {
        Documents document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with id: " + id));

        String beforeData = toJsonSnapshot(document);

        document.setSupplierName(request.getSupplierName());
        document.setDocumentNumber(request.getDocumentNumber());
        document.setDocumentType(request.getDocumentType());
        document.setIssueDate(request.getIssueDate());
        document.setDueDate(request.getDueDate());
        document.setCurrencyCode(request.getCurrencyCode());
        document.setSubtotal(request.getSubtotal());
        document.setTaxAmount(request.getTaxAmount());
        document.setDiscountAmount(request.getDiscountAmount());
        document.setTotalAmount(request.getTotalAmount());

        if (request.getLineItems() != null) {
            replaceLineItemsFromRequest(document, request.getLineItems());
        }

        document.getValidationIssues().clear();
        List<ValidationIssues> issues = buildValidationIssues(document);
        for (ValidationIssues issue : issues) {
            issue.setDocument(document);
            document.getValidationIssues().add(issue);
        }

        boolean hasErrorIssues = issues.stream()
                .anyMatch(issue -> issue.getSeverity() == IssuesSeverity.ERROR);

        if (request.getStatus() == DocumentsStatus.REJECTED) {
            document.setStatus(DocumentsStatus.REJECTED);
        } else if (Boolean.TRUE.equals(request.getConfirmFinalize())) {
            if (hasErrorIssues) {
                throw new IllegalArgumentException(
                        "Cannot confirm final version: resolve all error-level validation issues first.");
            }
            document.setStatus(DocumentsStatus.VALIDATED);
        } else if (!hasErrorIssues) {
            document.setStatus(DocumentsStatus.VALIDATED);
        } else {
            document.setStatus(DocumentsStatus.NEEDS_REVIEW);
        }

        String afterData = toJsonSnapshot(document);
        DocumentsRevision revision = DocumentsRevision.builder()
                .document(document)
                .changedBy(request.getChangedBy() == null || request.getChangedBy().isBlank() ? "reviewer" : request.getChangedBy())
                .changeReason(request.getChangeReason() == null || request.getChangeReason().isBlank() ? "Manual correction" : request.getChangeReason())
                .beforeData(beforeData)
                .afterData(afterData)
                .build();
        document.getDocumentRevisions().add(revision);

        Documents saved = documentRepository.save(document);
        return DocumentsMapper.mapToDocumentsDto(saved);
    }

    private DocumentsDto saveDocumentMetadata(MultipartFile file) {
        FileTypes fileType = resolveFileType(file.getOriginalFilename());
        ParsedDocumentData parsed = enrichParsedTotalsFromLineItems(fileParserService.parse(file, fileType));

        LocalDate issueDate = parsed.issueDate();
        if (issueDate == null && parsed.dueDate() != null) {
            issueDate = parsed.dueDate();
        }

        Documents document = Documents.builder()
                .originalFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown")
                .fileType(fileType)
                .documentType(parsed.documentType())
                .supplierName(parsed.supplierName())
                .documentNumber(parsed.documentNumber())
                .issueDate(issueDate)
                .dueDate(parsed.dueDate())
                .currencyCode(parsed.currencyCode())
                .subtotal(parsed.subtotal())
                .taxAmount(parsed.taxAmount())
                .discountAmount(parsed.discountAmount())
                .totalAmount(parsed.totalAmount())
                .status(DocumentsStatus.UPLOADED)
                .lineItems(new ArrayList<>())
                .validationIssues(new ArrayList<>())
                .build();

        if (parsed.lineItems() != null) {
            for (LineItems item : parsed.lineItems()) {
                item.setDocument(document);
                document.getLineItems().add(item);
            }
        }

        List<ValidationIssues> issues = buildValidationIssues(document);
        for (ValidationIssues issue : issues) {
            issue.setDocument(document);
            document.getValidationIssues().add(issue);
        }

        if (!issues.isEmpty()) {
            document.setStatus(DocumentsStatus.NEEDS_REVIEW);
        }

        Documents saved = documentRepository.save(document);

        return DocumentsMapper.mapToDocumentsDto(saved);
    }

    /**
     * Fills header money fields using line sums, OCR header, and — when possible — reconciles with printed
     * grand total so VAT/discount percentages apply to the invoice subtotal (e.g. 5400), not only ∑ line items.
     */
    private static ParsedDocumentData enrichParsedTotalsFromLineItems(ParsedDocumentData p) {
        if (p.lineItems() == null || p.lineItems().isEmpty()) {
            return p;
        }
        BigDecimal lineSum = BigDecimal.ZERO;
        for (LineItems li : p.lineItems()) {
            if (li.getLineTotal() != null) {
                lineSum = lineSum.add(li.getLineTotal());
            }
        }
        if (lineSum.compareTo(BigDecimal.ZERO) <= 0) {
            return p;
        }

        BigDecimal taxRaw = p.taxAmount();
        BigDecimal discRaw = p.discountAmount();
        BigDecimal grand = p.totalAmount();
        BigDecimal headerSub = p.subtotal();

        BigDecimal basisSub = resolveBasisSubtotalForPercentRates(headerSub, grand, taxRaw, discRaw, lineSum);

        BigDecimal normTax = normalizeTaxAmount(taxRaw, basisSub);
        BigDecimal normDisc = normalizeDiscountAmount(discRaw, basisSub);

        BigDecimal finalTotal;
        if (grand != null && grand.compareTo(BigDecimal.ZERO) > 0) {
            finalTotal = grand.setScale(2, RoundingMode.HALF_UP);
            BigDecimal impliedDisc = basisSub.add(normTax).subtract(finalTotal);
            if (impliedDisc.compareTo(BigDecimal.ZERO) >= 0) {
                normDisc = impliedDisc.setScale(2, RoundingMode.HALF_UP);
            }
        } else {
            finalTotal = basisSub.add(normTax).subtract(normDisc).setScale(2, RoundingMode.HALF_UP);
        }

        return ParsedDocumentData.builder()
                .documentType(p.documentType())
                .supplierName(p.supplierName())
                .documentNumber(p.documentNumber())
                .issueDate(p.issueDate())
                .dueDate(p.dueDate())
                .currencyCode(p.currencyCode())
                .subtotal(basisSub.setScale(2, RoundingMode.HALF_UP))
                .taxAmount(normTax)
                .discountAmount(normDisc)
                .totalAmount(finalTotal)
                .lineItems(p.lineItems())
                .build();
    }

    /**
     * Prefer OCR subtotal; else back-solve from grand total when tax/discount are rate-like (e.g. 10 and 20),
     * so 4860 / (1 + 0.10 − 0.20) = 5400 instead of using ∑ lines (2700) for 20% discount.
     */
    private static BigDecimal resolveBasisSubtotalForPercentRates(
            BigDecimal headerSub,
            BigDecimal grand,
            BigDecimal taxRaw,
            BigDecimal discRaw,
            BigDecimal lineSum) {
        if (headerSub != null && headerSub.compareTo(BigDecimal.ZERO) > 0) {
            return headerSub;
        }
        if (grand != null
                && grand.compareTo(BigDecimal.ZERO) > 0
                && looksLikeRatePercent(taxRaw)
                && (discRaw == null
                        || discRaw.compareTo(BigDecimal.ZERO) == 0
                        || looksLikeRatePercent(discRaw))) {
            BigDecimal discRate =
                    discRaw == null || discRaw.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : discRaw;
            BigDecimal factor = BigDecimal.ONE
                    .add(taxRaw.movePointLeft(2))
                    .subtract(discRate.movePointLeft(2));
            if (factor.compareTo(new BigDecimal("0.01")) > 0) {
                return grand.divide(factor, 4, RoundingMode.HALF_UP);
            }
        }
        return lineSum;
    }

    private static boolean looksLikeRatePercent(BigDecimal raw) {
        return raw != null
                && raw.compareTo(BigDecimal.ZERO) > 0
                && raw.compareTo(BigDecimal.valueOf(99)) <= 0;
    }

    /**
     * If tax is a small integer compared to subtotal, treat it as a percentage (10 → 10% of subtotal).
     */
    private static BigDecimal normalizeTaxAmount(BigDecimal tax, BigDecimal subtotal) {
        if (tax == null || tax.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return tax;
        }
        if (subtotal.compareTo(tax.multiply(BigDecimal.valueOf(50))) > 0 && tax.compareTo(BigDecimal.valueOf(99)) <= 0) {
            return subtotal.multiply(tax).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        }
        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    /** Same idea as tax: "20" next to Discount often means 20% of subtotal. */
    private static BigDecimal normalizeDiscountAmount(BigDecimal discount, BigDecimal subtotal) {
        if (discount == null || discount.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (subtotal == null || subtotal.compareTo(BigDecimal.ZERO) <= 0) {
            return discount.setScale(2, RoundingMode.HALF_UP);
        }
        if (subtotal.compareTo(discount.multiply(BigDecimal.valueOf(50))) > 0
                && discount.compareTo(BigDecimal.valueOf(99)) <= 0) {
            return subtotal.multiply(discount).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
        }
        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    private void replaceLineItemsFromRequest(Documents document, List<LineItemRequestDto> dtos) {
        document.getLineItems().clear();
        int seq = 1;
        for (LineItemRequestDto dto : dtos) {
            if (dto.getDescription() == null || dto.getDescription().isBlank()) {
                continue;
            }
            LineItems item = LineItems.builder()
                    .lineNo(dto.getLineNo() != null ? dto.getLineNo() : seq)
                    .description(dto.getDescription().trim())
                    .quantity(dto.getQuantity() != null ? dto.getQuantity() : BigDecimal.ZERO)
                    .unitPrice(dto.getUnitPrice() != null ? dto.getUnitPrice() : BigDecimal.ZERO)
                    .lineTaxAmount(dto.getLineTaxAmount() != null ? dto.getLineTaxAmount() : BigDecimal.ZERO)
                    .lineTotal(dto.getLineTotal() != null ? dto.getLineTotal() : BigDecimal.ZERO)
                    .build();
            item.setDocument(document);
            document.getLineItems().add(item);
            seq++;
        }
    }

    private List<ValidationIssues> buildValidationIssues(Documents document) {
        List<ValidationIssues> issues = new ArrayList<>();

        if (document.getDocumentType() == null) {
            issues.add(missingFieldIssue("documentType", "Document type is missing."));
        }
        if (document.getSupplierName() == null || document.getSupplierName().isBlank()) {
            issues.add(missingFieldIssue("supplierName", "Supplier/company name is missing."));
        }
        if (document.getDocumentNumber() == null || document.getDocumentNumber().isBlank()) {
            issues.add(missingFieldIssue("documentNumber", "Document number is missing."));
        }
        if (document.getIssueDate() == null) {
            issues.add(missingFieldIssue("issueDate", "Issue date is missing."));
        }
        if (document.getCurrencyCode() == null || document.getCurrencyCode().isBlank()) {
            issues.add(missingFieldIssue("currencyCode", "Currency is missing."));
        }
        if (document.getDueDate() == null) {
            issues.add(warningFieldIssue("dueDate", "Due date is missing."));
        }

        if (isSuspiciousIssueDate(document.getIssueDate())) {
            issues.add(ValidationIssues.builder()
                    .issueType(IssueTypes.INVALID_DATE)
                    .fieldName("issueDate")
                    .message("Issue date looks invalid or implausible.")
                    .severity(IssuesSeverity.WARNING)
                    .isResolved(Boolean.FALSE)
                    .build());
        }

        if (hasTotalMismatch(
                document.getSubtotal(),
                document.getTaxAmount(),
                document.getDiscountAmount(),
                document.getTotalAmount())) {
            issues.add(ValidationIssues.builder()
                    .issueType(IssueTypes.TOTAL_MISMATCH)
                    .fieldName("totalAmount")
                    .message("Total does not match subtotal + tax.")
                    .severity(IssuesSeverity.ERROR)
                    .isResolved(Boolean.FALSE)
                    .build());
        }

        if (isInvalidDateRange(document.getIssueDate(), document.getDueDate())) {
            issues.add(ValidationIssues.builder()
                    .issueType(IssueTypes.INVALID_DATE)
                    .fieldName("dueDate")
                    .message("Due date cannot be before issue date.")
                    .severity(IssuesSeverity.ERROR)
                    .isResolved(Boolean.FALSE)
                    .build());
        }

        if (isDuplicateDocumentNumber(document.getDocumentNumber(), document.getId())) {
            issues.add(ValidationIssues.builder()
                    .issueType(IssueTypes.DUPLICATE_DOC_NUMBER)
                    .fieldName("documentNumber")
                    .message("Document number already exists.")
                    .severity(IssuesSeverity.ERROR)
                    .isResolved(Boolean.FALSE)
                    .build());
        }

        if (document.getLineItems() != null) {
            for (LineItems item : document.getLineItems()) {
                if (item.getQuantity() == null || item.getUnitPrice() == null || item.getLineTotal() == null) {
                    continue;
                }
                if (item.getQuantity().multiply(item.getUnitPrice()).compareTo(item.getLineTotal()) != 0) {
                    issues.add(ValidationIssues.builder()
                            .issueType(IssueTypes.LINE_CALC_ERROR)
                            .fieldName("lineItems")
                            .message("Line item total does not match quantity * unit price.")
                            .severity(IssuesSeverity.ERROR)
                            .isResolved(Boolean.FALSE)
                            .build());
                }
            }
        }

        return issues;
    }

    private boolean hasTotalMismatch(
            BigDecimal subtotal, BigDecimal taxAmount, BigDecimal discountAmount, BigDecimal totalAmount) {
        if (subtotal == null || taxAmount == null || totalAmount == null) {
            return false;
        }
        BigDecimal disc = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        return subtotal.add(taxAmount).subtract(disc).compareTo(totalAmount) != 0;
    }

    private boolean isInvalidDateRange(LocalDate issueDate, LocalDate dueDate) {
        return issueDate != null && dueDate != null && dueDate.isBefore(issueDate);
    }

    private boolean isDuplicateDocumentNumber(String documentNumber, Long currentDocumentId) {
        if (documentNumber == null || documentNumber.isBlank()) {
            return false;
        }
        if (currentDocumentId == null) {
            return documentRepository.existsByDocumentNumber(documentNumber);
        }
        return documentRepository.existsByDocumentNumberAndIdNot(documentNumber, currentDocumentId);
    }

    private ValidationIssues missingFieldIssue(String fieldName, String message) {
        return ValidationIssues.builder()
                .issueType(IssueTypes.MISSING_FIELD)
                .fieldName(fieldName)
                .message(message)
                .severity(IssuesSeverity.ERROR)
                .isResolved(Boolean.FALSE)
                .build();
    }

    private ValidationIssues warningFieldIssue(String fieldName, String message) {
        return ValidationIssues.builder()
                .issueType(IssueTypes.MISSING_FIELD)
                .fieldName(fieldName)
                .message(message)
                .severity(IssuesSeverity.WARNING)
                .isResolved(Boolean.FALSE)
                .build();
    }

    private boolean isSuspiciousIssueDate(LocalDate issueDate) {
        if (issueDate == null) {
            return false;
        }
        LocalDate now = LocalDate.now();
        return issueDate.isBefore(LocalDate.of(1990, 1, 1)) || issueDate.isAfter(now.plusYears(5));
    }

    private String toJsonSnapshot(Documents document) {
        return String.format(
                "{\"supplierName\":\"%s\",\"documentNumber\":\"%s\",\"documentType\":\"%s\",\"issueDate\":\"%s\",\"dueDate\":\"%s\",\"currencyCode\":\"%s\",\"subtotal\":\"%s\",\"taxAmount\":\"%s\",\"discountAmount\":\"%s\",\"totalAmount\":\"%s\",\"status\":\"%s\"}",
                safe(document.getSupplierName()),
                safe(document.getDocumentNumber()),
                document.getDocumentType() == null ? "" : document.getDocumentType().name(),
                document.getIssueDate() == null ? "" : document.getIssueDate(),
                document.getDueDate() == null ? "" : document.getDueDate(),
                safe(document.getCurrencyCode()),
                document.getSubtotal() == null ? "" : document.getSubtotal(),
                document.getTaxAmount() == null ? "" : document.getTaxAmount(),
                document.getDiscountAmount() == null ? "" : document.getDiscountAmount(),
                document.getTotalAmount() == null ? "" : document.getTotalAmount(),
                document.getStatus() == null ? "" : document.getStatus().name()
        );
    }

    private String safe(String value) {
        return value == null ? "" : value.replace("\"", "\\\"");
    }

    private FileTypes resolveFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            throw new IllegalArgumentException("Unsupported file type");
        }

        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

        return switch (extension) {
            case "pdf" -> FileTypes.PDF;
            case "png", "jpg", "jpeg", "webp" -> FileTypes.IMAGE;
            case "csv" -> FileTypes.CSV;
            case "txt" -> FileTypes.TXT;
            default -> throw new IllegalArgumentException("Unsupported file type: " + extension);
        };
    }
}
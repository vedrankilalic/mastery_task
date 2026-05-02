package com.masteryapi.masteryapi.mapper;

import com.masteryapi.masteryapi.dto.DocumentsDto;
import com.masteryapi.masteryapi.dto.LineItemsDto;
import com.masteryapi.masteryapi.dto.ValidationIssuesDto;
import com.masteryapi.masteryapi.entity.Documents;
import com.masteryapi.masteryapi.entity.LineItems;
import com.masteryapi.masteryapi.entity.ValidationIssues;
import java.util.List;

public class DocumentsMapper {

    public static DocumentsDto mapToDocumentsDto(Documents doc) {
        return DocumentsDto.builder()
                .id(doc.getId())
                .originalFileName(doc.getOriginalFileName())
                .fileType(doc.getFileType())
                .documentType(doc.getDocumentType())
                .supplierName(doc.getSupplierName())
                .documentNumber(doc.getDocumentNumber())
                .issueDate(doc.getIssueDate())
                .dueDate(doc.getDueDate())
                .currencyCode(doc.getCurrencyCode())
                .subtotal(doc.getSubtotal())
                .taxAmount(doc.getTaxAmount())
                .totalAmount(doc.getTotalAmount())
                .lineItems(mapLineItems(doc.getLineItems()))
                .validationIssues(mapValidationIssues(doc.getValidationIssues()))
                .status(doc.getStatus())
                .build();
    }

    private static List<LineItemsDto> mapLineItems(List<LineItems> lineItems) {
        if (lineItems == null || lineItems.isEmpty()) {
            return List.of();
        }

        return lineItems.stream()
                .map(item -> LineItemsDto.builder()
                        .id(item.getId())
                        .lineNo(item.getLineNo())
                        .description(item.getDescription())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .lineTaxAmount(item.getLineTaxAmount())
                        .lineTotal(item.getLineTotal())
                        .build())
                .toList();
    }

    private static List<ValidationIssuesDto> mapValidationIssues(List<ValidationIssues> issues) {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }

        return issues.stream()
                .map(issue -> ValidationIssuesDto.builder()
                        .id(issue.getId())
                        .issueType(issue.getIssueType())
                        .fieldName(issue.getFieldName())
                        .message(issue.getMessage())
                        .severity(issue.getSeverity())
                        .isResolved(issue.getIsResolved())
                        .build())
                .toList();
    }
}

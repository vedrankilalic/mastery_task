package com.masteryapi.masteryapi.entity;

import com.masteryapi.masteryapi.types.DocumentsType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;

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
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        List<LineItems> lineItems) {
}

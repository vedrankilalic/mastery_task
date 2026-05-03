package com.masteryapi.masteryapi.dto;

import com.masteryapi.masteryapi.types.DocumentsStatus;
import com.masteryapi.masteryapi.types.DocumentsType;
import com.masteryapi.masteryapi.types.FileTypes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class DocumentsDto {

    private Long id;
    private String originalFileName;
    private FileTypes fileType;
    private DocumentsType documentType;
    private String supplierName;
    private String documentNumber;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private String currencyCode;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private List<LineItemsDto> lineItems;
    private List<ValidationIssuesDto> validationIssues;
    private DocumentsStatus status;
}
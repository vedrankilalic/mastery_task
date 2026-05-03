package com.masteryapi.masteryapi.dto;

import com.masteryapi.masteryapi.types.DocumentsStatus;
import com.masteryapi.masteryapi.types.DocumentsType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDocumentRequestDto {

    private String supplierName;
    private String documentNumber;
    private DocumentsType documentType;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private String currencyCode;
    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private List<LineItemRequestDto> lineItems;
    private Boolean confirmFinalize;
    private String changeReason;
    private String changedBy;
    private DocumentsStatus status;
}

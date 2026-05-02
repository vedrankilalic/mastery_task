package com.masteryapi.masteryapi.dto;

import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LineItemRequestDto {

    private Integer lineNo;
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTaxAmount;
    private BigDecimal lineTotal;
}

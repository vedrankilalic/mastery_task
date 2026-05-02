package com.masteryapi.masteryapi.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class LineItemsDto {

    private Long id;
    private Integer lineNo;
    private String description;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTaxAmount;
    private BigDecimal lineTotal;
}

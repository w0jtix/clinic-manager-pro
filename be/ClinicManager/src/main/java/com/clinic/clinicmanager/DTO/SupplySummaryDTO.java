package com.clinic.clinicmanager.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SupplySummaryDTO {

    private Integer currentSupply;
    private Integer totalIn;
    private Integer totalSold;
    private Integer totalUsed;
    private Integer totalAdjustments;
    private BigDecimal avgNetPurchasePrice;
    private boolean fallbackPriceUsed;
    private BigDecimal stockValue;
}

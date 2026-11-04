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
public class KsefSpendChartPointDTO {

    private Integer month;
    private BigDecimal gross;
    private Long invoiceCount;
    private Long manualExpenseCount;
}

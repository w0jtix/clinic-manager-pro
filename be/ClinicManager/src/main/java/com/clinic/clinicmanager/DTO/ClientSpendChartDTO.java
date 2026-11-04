package com.clinic.clinicmanager.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClientSpendChartDTO {

    private int year;
    private int firstYear;
    private int lastYear;
    private List<BigDecimal> months;
    private List<Integer> visitCounts;
}

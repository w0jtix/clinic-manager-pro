package com.clinic.clinicmanager.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class KsefSpendChartDTO {

    private Integer firstYear;
    private Integer lastYear;
    private List<KsefSpendChartPointDTO> points;
}

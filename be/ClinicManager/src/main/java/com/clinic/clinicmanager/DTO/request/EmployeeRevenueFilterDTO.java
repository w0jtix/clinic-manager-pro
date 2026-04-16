package com.clinic.clinicmanager.DTO.request;

import com.clinic.clinicmanager.model.constants.ChartMode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeRevenueFilterDTO {

    private ChartMode mode;
    private Integer year;
    private Integer month; //only for ChartMode.DAILY
}

package com.clinic.clinicmanager.DTO.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CompanyExpenseFilterDTO {
    private List<String> categories;
    private Integer month;
    private Integer year;
}

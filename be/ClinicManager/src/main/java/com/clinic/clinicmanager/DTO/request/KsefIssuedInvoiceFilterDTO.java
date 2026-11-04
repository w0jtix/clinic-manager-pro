package com.clinic.clinicmanager.DTO.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KsefIssuedInvoiceFilterDTO {

    private String keyword;
    private Integer year;
    private Integer month;
}

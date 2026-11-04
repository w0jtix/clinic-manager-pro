package com.clinic.clinicmanager.DTO.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class KsefInvoiceFilterDTO {

    private String keyword;
    private Integer year;
    private Integer month;
    private Boolean isPaid;
    private Long sellerId;
    private List<String> categories;
    private Boolean uncategorized;
    private Boolean awaitingVerification;
}

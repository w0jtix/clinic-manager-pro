package com.clinic.clinicmanager.DTO.request;

import com.clinic.clinicmanager.DTO.NamedInvoiceItemDTO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.clinic.clinicmanager.model.constants.PaymentMethod;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class NamedInvoiceCreateDTO {

    private Long clientId;
    private Long sourceVisitId;
    private String invoiceNumber;
    private Integer sequenceInMonth;
    private Integer year;
    private Integer month;
    private LocalDate issueDate;
    private LocalDate saleDate;
    private Set<PaymentMethod> paymentMethods;
    private String notes;
    private String buyerName;
    private String buyerAddress;
    private String buyerPostalCode;
    private String buyerCity;
    private List<NamedInvoiceItemDTO> items;
}

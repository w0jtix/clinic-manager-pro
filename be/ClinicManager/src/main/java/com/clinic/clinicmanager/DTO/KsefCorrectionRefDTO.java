package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.KsefInvoice;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KsefCorrectionRefDTO {

    private Long id;
    private String ksefNumber;
    private String invoiceType;

    public KsefCorrectionRefDTO(KsefInvoice correction) {
        this.id = correction.getId();
        this.ksefNumber = correction.getKsefReferenceNumber();
        this.invoiceType = correction.getInvoiceType();
    }
}

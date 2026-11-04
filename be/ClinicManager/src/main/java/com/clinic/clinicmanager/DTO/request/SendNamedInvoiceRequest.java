package com.clinic.clinicmanager.DTO.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SendNamedInvoiceRequest {

    private String email;
    private boolean updateClientEmail;
}

package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.NamedInvoiceDTO;
import com.clinic.clinicmanager.DTO.request.SendNamedInvoiceRequest;

public interface NamedInvoiceEmailService {

    NamedInvoiceDTO send(Long invoiceId, SendNamedInvoiceRequest request);
}

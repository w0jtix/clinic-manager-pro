package com.clinic.clinicmanager.service;

public interface NamedInvoicePdfService {

    byte[] generateInvoicePdf(Long namedInvoiceId);
}

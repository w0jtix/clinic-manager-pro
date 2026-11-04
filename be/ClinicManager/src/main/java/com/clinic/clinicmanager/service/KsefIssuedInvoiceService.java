package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.KsefIssuedInvoiceCountDTO;
import com.clinic.clinicmanager.DTO.KsefIssuedInvoiceDTO;
import com.clinic.clinicmanager.DTO.request.KsefIssuedInvoiceFilterDTO;
import org.springframework.data.domain.Page;

public interface KsefIssuedInvoiceService {

    Page<KsefIssuedInvoiceDTO> getInvoices(KsefIssuedInvoiceFilterDTO filter, int page, int size);

    KsefIssuedInvoiceCountDTO getInvoiceCount(KsefIssuedInvoiceFilterDTO filter);

    KsefIssuedInvoiceDTO getInvoiceById(Long id);
}

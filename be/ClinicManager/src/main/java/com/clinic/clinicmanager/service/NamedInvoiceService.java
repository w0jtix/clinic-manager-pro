package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.NamedInvoiceDTO;
import com.clinic.clinicmanager.DTO.NamedInvoiceDraftDTO;
import com.clinic.clinicmanager.DTO.request.NamedInvoiceCreateDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface NamedInvoiceService {

    NamedInvoiceDraftDTO buildDraft(Long visitId);

    NamedInvoiceDTO create(NamedInvoiceCreateDTO request);

    NamedInvoiceDTO update(Long id, NamedInvoiceCreateDTO request);

    void delete(Long id);

    NamedInvoiceDTO getById(Long id);

    Page<NamedInvoiceDTO> getByClient(Long clientId, int page, int size);

    List<NamedInvoiceDTO> getByPeriod(int year, int month);
}

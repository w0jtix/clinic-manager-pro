package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.KsefIssuedInvoiceCountDTO;
import com.clinic.clinicmanager.DTO.KsefIssuedInvoiceDTO;
import com.clinic.clinicmanager.DTO.request.KsefIssuedInvoiceFilterDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.KsefIssuedInvoice;
import com.clinic.clinicmanager.repo.KsefIssuedInvoiceRepo;
import com.clinic.clinicmanager.service.KsefIssuedInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KsefIssuedInvoiceServiceImpl implements KsefIssuedInvoiceService {

    private final KsefIssuedInvoiceRepo issuedInvoiceRepo;

    @Override
    public Page<KsefIssuedInvoiceDTO> getInvoices(KsefIssuedInvoiceFilterDTO filter, int page, int size) {
        KsefIssuedInvoiceFilterDTO effectiveFilter = isNull(filter) ? new KsefIssuedInvoiceFilterDTO() : filter;
        DateRange range = resolveDateRange(effectiveFilter);
        Pageable pageable = PageRequest.of(page, size);

        return issuedInvoiceRepo.findAllWithFilters(
                        effectiveFilter.getKeyword(),
                        range.from(),
                        range.to(),
                        pageable)
                .map(KsefIssuedInvoiceDTO::new);
    }

    @Override
    public KsefIssuedInvoiceCountDTO getInvoiceCount(KsefIssuedInvoiceFilterDTO filter) {
        KsefIssuedInvoiceFilterDTO effectiveFilter = isNull(filter) ? new KsefIssuedInvoiceFilterDTO() : filter;
        DateRange range = resolveDateRange(effectiveFilter);

        long count = issuedInvoiceRepo.countWithFilters(
                effectiveFilter.getKeyword(),
                range.from(),
                range.to());

        return new KsefIssuedInvoiceCountDTO(count);
    }

    @Override
    public KsefIssuedInvoiceDTO getInvoiceById(Long id) {
        KsefIssuedInvoice invoice = issuedInvoiceRepo.findByIdWithItems(id)
                .orElseThrow(() -> new ResourceNotFoundException("KSeF issued invoice not found: " + id));
        return new KsefIssuedInvoiceDTO(invoice, true);
    }

    private DateRange resolveDateRange(KsefIssuedInvoiceFilterDTO filter) {
        Integer year = filter.getYear();
        Integer month = filter.getMonth();
        if (isNull(year)) {
            return new DateRange(null, null);
        }
        if (isNull(month)) {
            return new DateRange(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
        }
        LocalDate from = LocalDate.of(year, month, 1);
        return new DateRange(from, from.withDayOfMonth(from.lengthOfMonth()));
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}

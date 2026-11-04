package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.KsefIssuedInvoiceCountDTO;
import com.clinic.clinicmanager.DTO.KsefIssuedInvoiceDTO;
import com.clinic.clinicmanager.DTO.request.KsefIssuedInvoiceFilterDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.KsefIssuedInvoice;
import com.clinic.clinicmanager.model.KsefIssuedInvoiceItem;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.KsefIssuedInvoiceRepo;
import com.clinic.clinicmanager.service.impl.KsefIssuedInvoiceServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KsefIssuedInvoiceServiceImplTest {

    @Mock
    private KsefIssuedInvoiceRepo issuedInvoiceRepo;

    @InjectMocks
    private KsefIssuedInvoiceServiceImpl service;

    private KsefIssuedInvoice sampleInvoice(Long id) {
        KsefIssuedInvoice invoice = KsefIssuedInvoice.builder()
                .id(id)
                .ksefReferenceNumber("20260615-FS-00" + id)
                .invoiceNumber("FS/2026/06/00" + id)
                .invoiceType("VAT")
                .issueDate(LocalDate.of(2026, 6, 15))
                .sellerName("ClinicManager Sp. z o.o.")
                .sellerNip("1234567890")
                .sellerAddress("ul. Testowa 1")
                .buyerName("Acme S.A.")
                .buyerNip("0987654321")
                .buyerAddress("ul. Nabywcza 5")
                .netAmount(new BigDecimal("100.00"))
                .vatAmount(new BigDecimal("23.00"))
                .grossAmount(new BigDecimal("123.00"))
                .currency("PLN")
                .build();
        invoice.addItem(KsefIssuedInvoiceItem.builder()
                .ksefItemName("Item")
                .unit("szt.")
                .quantity(new BigDecimal("1"))
                .netValue(new BigDecimal("100.00"))
                .grossValue(new BigDecimal("123.00"))
                .vatRate(VatRate.VAT_23)
                .build());
        return invoice;
    }

    @Test
    void getInvoices_nullFilter_queriesWithoutDateBounds() {
        when(issuedInvoiceRepo.findAllWithFilters(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(Page.empty());

        Page<KsefIssuedInvoiceDTO> result = service.getInvoices(null, 0, 20);

        assertTrue(result.isEmpty());
        verify(issuedInvoiceRepo).findAllWithFilters(isNull(), isNull(), isNull(), eq(PageRequest.of(0, 20)));
    }

    @Test
    void getInvoices_yearOnly_resolvesToFullCalendarYear() {
        when(issuedInvoiceRepo.findAllWithFilters(any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        KsefIssuedInvoiceFilterDTO filter = new KsefIssuedInvoiceFilterDTO();
        filter.setYear(2026);

        service.getInvoices(filter, 0, 20);

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(issuedInvoiceRepo).findAllWithFilters(any(), from.capture(), to.capture(), any(Pageable.class));
        assertEquals(LocalDate.of(2026, 1, 1), from.getValue());
        assertEquals(LocalDate.of(2026, 12, 31), to.getValue());
    }

    @Test
    void getInvoices_yearAndMonth_resolvesToMonthBoundaries() {
        when(issuedInvoiceRepo.findAllWithFilters(any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        KsefIssuedInvoiceFilterDTO filter = new KsefIssuedInvoiceFilterDTO();
        filter.setYear(2026);
        filter.setMonth(2);

        service.getInvoices(filter, 0, 20);

        ArgumentCaptor<LocalDate> from = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> to = ArgumentCaptor.forClass(LocalDate.class);
        verify(issuedInvoiceRepo).findAllWithFilters(any(), from.capture(), to.capture(), any(Pageable.class));
        assertEquals(LocalDate.of(2026, 2, 1), from.getValue());
        assertEquals(LocalDate.of(2026, 2, 28), to.getValue());
    }

    @Test
    void getInvoices_forwardsKeyword_andMapsEntitiesToDtoWithoutItems() {
        KsefIssuedInvoiceFilterDTO filter = new KsefIssuedInvoiceFilterDTO();
        filter.setKeyword("acme");
        when(issuedInvoiceRepo.findAllWithFilters(eq("acme"), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleInvoice(1L))));

        Page<KsefIssuedInvoiceDTO> result = service.getInvoices(filter, 0, 20);

        assertEquals(1, result.getTotalElements());
        KsefIssuedInvoiceDTO dto = result.getContent().getFirst();
        assertEquals(1L, dto.getId());
        assertEquals("FS/2026/06/001", dto.getInvoiceNumber());
        assertEquals("ClinicManager Sp. z o.o.", dto.getSellerName());
        assertEquals("1234567890", dto.getSellerNip());
        assertEquals("ul. Testowa 1", dto.getSellerAddress());
        assertEquals("Acme S.A.", dto.getBuyerName());
        assertEquals(new BigDecimal("123.00"), dto.getGrossAmount());
        assertNull(dto.getItems());
    }

    @Test
    void getInvoices_forwardsRequestedPageAndSize() {
        when(issuedInvoiceRepo.findAllWithFilters(any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getInvoices(new KsefIssuedInvoiceFilterDTO(), 3, 15);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(issuedInvoiceRepo).findAllWithFilters(any(), any(), any(), pageable.capture());
        assertEquals(3, pageable.getValue().getPageNumber());
        assertEquals(15, pageable.getValue().getPageSize());
    }

    @Test
    void getInvoiceCount_returnsRepositoryCount_forResolvedMonthRange() {
        KsefIssuedInvoiceFilterDTO filter = new KsefIssuedInvoiceFilterDTO();
        filter.setYear(2026);
        filter.setMonth(2);
        filter.setKeyword("acme");
        when(issuedInvoiceRepo.countWithFilters("acme", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .thenReturn(7L);

        KsefIssuedInvoiceCountDTO result = service.getInvoiceCount(filter);

        assertEquals(7L, result.getCount());
    }

    @Test
    void getInvoiceCount_nullFilter_usesNoDateBounds() {
        when(issuedInvoiceRepo.countWithFilters(isNull(), isNull(), isNull())).thenReturn(0L);

        KsefIssuedInvoiceCountDTO result = service.getInvoiceCount(null);

        assertEquals(0L, result.getCount());
        verify(issuedInvoiceRepo).countWithFilters(isNull(), isNull(), isNull());
    }

    @Test
    void getInvoiceById_returnsDtoWithItems_whenFound() {
        when(issuedInvoiceRepo.findByIdWithItems(1L)).thenReturn(Optional.of(sampleInvoice(1L)));

        KsefIssuedInvoiceDTO result = service.getInvoiceById(1L);

        assertEquals(1L, result.getId());
        assertEquals(1, result.getItems().size());
        assertEquals("Item", result.getItems().getFirst().getKsefItemName());
        assertEquals(VatRate.VAT_23, result.getItems().getFirst().getVatRate());
    }

    @Test
    void getInvoiceById_throwsResourceNotFound_whenMissing() {
        when(issuedInvoiceRepo.findByIdWithItems(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getInvoiceById(99L));
    }
}

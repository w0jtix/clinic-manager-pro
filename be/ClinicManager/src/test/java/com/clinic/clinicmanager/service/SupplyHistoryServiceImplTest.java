package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.SupplyChartDTO;
import com.clinic.clinicmanager.DTO.SupplyHistoryDTO;
import com.clinic.clinicmanager.DTO.SupplySummaryDTO;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.model.ProductSupplyHistory;
import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import com.clinic.clinicmanager.repo.CompanyExpenseRepo;
import com.clinic.clinicmanager.repo.InventoryReportRepo;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.repo.ProductSupplyHistoryRepo;
import com.clinic.clinicmanager.repo.UsageRecordRepo;
import com.clinic.clinicmanager.repo.VisitRepo;
import com.clinic.clinicmanager.repo.projection.ProductPurchaseProjection;
import com.clinic.clinicmanager.repo.projection.SupplySourceTotalProjection;
import com.clinic.clinicmanager.service.impl.SupplyHistoryServiceImpl;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplyHistoryServiceImplTest {

    private static void assertMoney(double expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    @Mock ProductSupplyHistoryRepo supplyHistoryRepo;
    @Mock ProductRepo productRepo;
    @Mock VisitRepo visitRepo;
    @Mock UsageRecordRepo usageRecordRepo;
    @Mock InventoryReportRepo inventoryReportRepo;
    @Mock CompanyExpenseRepo companyExpenseRepo;

    @InjectMocks
    SupplyHistoryServiceImpl supplyHistoryService;

    private Product product(Long id, Integer supply) {
        return Product.builder()
                .id(id).name("Krem").supply(supply)
                .category(ProductCategory.builder().id(1L).name("Kremy").color("#000000").build())
                .brand(Brand.builder().id(1L).name("Brand").build())
                .build();
    }

    private ProductSupplyHistory historyEntry(Long id, Product product, int delta, int supplyAfter,
                                               SupplyChangeSource source, Long sourceId) {
        return ProductSupplyHistory.builder()
                .id(id).product(product).delta(delta).supplyAfter(supplyAfter)
                .source(source).sourceId(sourceId).sourceLabel("label")
                .occurredAt(LocalDateTime.now()).performedBy("SYSTEM")
                .build();
    }

    private SupplySourceTotalProjection projection(SupplyChangeSource source, long total) {
        SupplySourceTotalProjection projection = mock(SupplySourceTotalProjection.class);
        when(projection.getSource()).thenReturn(source);
        when(projection.getTotal()).thenReturn(total);
        return projection;
    }

    @Test
    void record_doesNotSave_whenDeltaIsZero() {
        Product product = product(1L, 10);

        supplyHistoryService.record(product, 0, SupplyChangeSource.PRODUCT_FORM, 1L, "Edycja produktu");

        verifyNoInteractions(supplyHistoryRepo);
    }

    @Test
    void record_savesEntry_withSupplyAfterFromProductAndUsernameFromSession() {
        Product product = product(1L, 12);
        UserDetailsImpl userDetails = mock(UserDetailsImpl.class);
        when(userDetails.getUsername()).thenReturn("wojtek");

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserDetailsFromSession).thenReturn(userDetails);

            supplyHistoryService.record(product, 2, SupplyChangeSource.KSEF_INVOICE, 5L, "FV/1");
        }

        ArgumentCaptor<ProductSupplyHistory> captor = ArgumentCaptor.forClass(ProductSupplyHistory.class);
        verify(supplyHistoryRepo).save(captor.capture());
        ProductSupplyHistory saved = captor.getValue();
        assertEquals(product, saved.getProduct());
        assertEquals(2, saved.getDelta());
        assertEquals(12, saved.getSupplyAfter());
        assertEquals(SupplyChangeSource.KSEF_INVOICE, saved.getSource());
        assertEquals(5L, saved.getSourceId());
        assertEquals("FV/1", saved.getSourceLabel());
        assertEquals("wojtek", saved.getPerformedBy());
        assertNotNull(saved.getOccurredAt());
    }

    @Test
    void record_fallsBackToSystemUsername_whenNoUserInSession() {
        Product product = product(1L, 5);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);

            supplyHistoryService.record(product, 1, SupplyChangeSource.PRODUCT_CREATED, 1L, "Nowy produkt");
        }

        ArgumentCaptor<ProductSupplyHistory> captor = ArgumentCaptor.forClass(ProductSupplyHistory.class);
        verify(supplyHistoryRepo).save(captor.capture());
        assertEquals("SYSTEM", captor.getValue().getPerformedBy());
    }

    @Test
    void getHistory_throwsResourceNotFoundException_whenProductMissing() {
        when(productRepo.findByIdIncludingDeleted(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> supplyHistoryService.getHistory(99L, null, PageRequest.of(0, 30)));
    }

    @Test
    void getHistory_passesSourceFilterToRepo_whenSourcesProvided() {
        Product p = product(1L, 5);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));

        List<SupplyChangeSource> sources = List.of(
                SupplyChangeSource.KSEF_INVOICE, SupplyChangeSource.KSEF_CORRECTION);
        when(supplyHistoryRepo.findAllByProductId(eq(1L), eq(sources), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        supplyHistoryService.getHistory(1L, sources, PageRequest.of(0, 30));

        verify(supplyHistoryRepo).findAllByProductId(eq(1L), eq(sources), any(Pageable.class));
    }

    @Test
    void getHistory_passesNullFilterToRepo_whenSourcesIsEmpty() {
        Product p = product(1L, 5);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));
        when(supplyHistoryRepo.findAllByProductId(eq(1L), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        supplyHistoryService.getHistory(1L, List.of(), PageRequest.of(0, 30));

        verify(supplyHistoryRepo).findAllByProductId(eq(1L), isNull(), any(Pageable.class));
    }

    @Test
    void getHistory_forwardsPageableUnchanged() {
        Product p = product(1L, 5);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));

        Pageable pageable = PageRequest.of(2, 15);
        when(supplyHistoryRepo.findAllByProductId(eq(1L), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        supplyHistoryService.getHistory(1L, null, pageable);

        verify(supplyHistoryRepo).findAllByProductId(eq(1L), isNull(), eq(pageable));
    }

    @Test
    void getHistory_stillResolvesSourceExistence_whenSourceFilterApplied() {
        Product p = product(1L, 5);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));

        ProductSupplyHistory saleEntry = historyEntry(10L, p, -1, 4, SupplyChangeSource.VISIT_SALE, 77L);
        List<SupplyChangeSource> sources = List.of(SupplyChangeSource.VISIT_SALE);
        when(supplyHistoryRepo.findAllByProductId(eq(1L), eq(sources), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(saleEntry)));
        when(visitRepo.findExistingIds(Set.of(77L))).thenReturn(Set.of());

        Page<SupplyHistoryDTO> result = supplyHistoryService.getHistory(1L, sources, PageRequest.of(0, 30));

        assertFalse(result.getContent().get(0).isSourceExists());
    }

    @Test
    void getHistory_setsSourceExistsFalse_whenLinkedVisitWasDeleted() {
        Product p = product(1L, 5);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));

        ProductSupplyHistory saleEntry = historyEntry(10L, p, -1, 4, SupplyChangeSource.VISIT_SALE, 77L);
        when(supplyHistoryRepo.findAllByProductId(eq(1L), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(saleEntry)));
        when(visitRepo.findExistingIds(Set.of(77L))).thenReturn(Set.of());

        Page<SupplyHistoryDTO> result = supplyHistoryService.getHistory(1L, null, PageRequest.of(0, 30));

        assertFalse(result.getContent().get(0).isSourceExists());
    }

    @Test
    void getHistory_setsSourceExistsTrue_whenLinkedVisitStillExists() {
        Product p = product(1L, 5);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));

        ProductSupplyHistory saleEntry = historyEntry(10L, p, -1, 4, SupplyChangeSource.VISIT_SALE, 77L);
        when(supplyHistoryRepo.findAllByProductId(eq(1L), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(saleEntry)));
        when(visitRepo.findExistingIds(Set.of(77L))).thenReturn(Set.of(77L));

        Page<SupplyHistoryDTO> result = supplyHistoryService.getHistory(1L, null, PageRequest.of(0, 30));

        assertTrue(result.getContent().get(0).isSourceExists());
    }

    @Test
    void getHistory_setsSourceExistsFalse_whenLinkedUsageRecordWasDeleted() {
        Product p = product(1L, 5);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));

        ProductSupplyHistory usageEntry = historyEntry(11L, p, -2, 3, SupplyChangeSource.USAGE_RECORD, 5L);
        when(supplyHistoryRepo.findAllByProductId(eq(1L), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(usageEntry)));
        when(usageRecordRepo.findExistingIds(Set.of(5L))).thenReturn(Set.of());

        Page<SupplyHistoryDTO> result = supplyHistoryService.getHistory(1L, null, PageRequest.of(0, 30));

        assertFalse(result.getContent().get(0).isSourceExists());
    }

    @Test
    void getHistory_setsSourceExistsFalse_whenLinkedInventoryReportWasDeleted() {
        Product p = product(1L, 5);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));

        ProductSupplyHistory reportEntry = historyEntry(12L, p, -2, 3, SupplyChangeSource.INVENTORY_REPORT, 9L);
        when(supplyHistoryRepo.findAllByProductId(eq(1L), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reportEntry)));
        when(inventoryReportRepo.findExistingIds(Set.of(9L))).thenReturn(Set.of());

        Page<SupplyHistoryDTO> result = supplyHistoryService.getHistory(1L, null, PageRequest.of(0, 30));

        assertFalse(result.getContent().get(0).isSourceExists());
    }

    @Test
    void getHistory_setsSourceExistsTrue_forSourcesThatAreNeverDeleted() {
        Product p = product(1L, 5);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));

        ProductSupplyHistory invoiceEntry = historyEntry(13L, p, 5, 10, SupplyChangeSource.KSEF_INVOICE, 4L);
        when(supplyHistoryRepo.findAllByProductId(eq(1L), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(invoiceEntry)));

        Page<SupplyHistoryDTO> result = supplyHistoryService.getHistory(1L, null, PageRequest.of(0, 30));

        assertTrue(result.getContent().get(0).isSourceExists());
        verifyNoInteractions(visitRepo, usageRecordRepo, inventoryReportRepo, companyExpenseRepo);
    }

    @Test
    void getHistory_resolvesExpenseExistence_withOneQueryForPurchaseAndDeletionEntries() {
        Product p = product(1L, 5);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));

        ProductSupplyHistory deletedExpenseRevert = historyEntry(16L, p, -3, 5, SupplyChangeSource.COMPANY_EXPENSE_DELETED, 4L);
        ProductSupplyHistory deletedExpensePurchase = historyEntry(15L, p, 3, 8, SupplyChangeSource.COMPANY_EXPENSE, 4L);
        ProductSupplyHistory existingExpensePurchase = historyEntry(14L, p, 5, 5, SupplyChangeSource.COMPANY_EXPENSE, 6L);
        when(supplyHistoryRepo.findAllByProductId(eq(1L), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(deletedExpenseRevert, deletedExpensePurchase, existingExpensePurchase)));
        when(companyExpenseRepo.findExistingIds(Set.of(4L, 6L))).thenReturn(Set.of(6L));

        Page<SupplyHistoryDTO> result = supplyHistoryService.getHistory(1L, null, PageRequest.of(0, 30));

        assertFalse(result.getContent().get(0).isSourceExists());
        assertFalse(result.getContent().get(1).isSourceExists());
        assertTrue(result.getContent().get(2).isSourceExists());
        verify(companyExpenseRepo, times(1)).findExistingIds(any());
        verifyNoInteractions(visitRepo, usageRecordRepo, inventoryReportRepo);
    }

    @Test
    void getChart_throwsResourceNotFoundException_whenProductMissing() {
        when(productRepo.findByIdIncludingDeleted(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> supplyHistoryService.getChart(99L, 2026));
    }

    @Test
    void getChart_returnsOpeningSupplyAndEmptyPoints_whenNoMovementInGivenYear() {
        Product p = product(1L, 20);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));
        when(supplyHistoryRepo.findSupplyBefore(eq(1L), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(20));
        when(supplyHistoryRepo.findAllByProductIdInRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        SupplyChartDTO result = supplyHistoryService.getChart(1L, 2026);

        assertEquals(20, result.getOpeningSupply());
        assertTrue(result.getPoints().isEmpty());
        assertEquals(2026, result.getFirstYear());
        assertEquals(2026, result.getLastYear());
    }

    @Test
    void getChart_usesCurrentYear_whenYearArgumentIsNull() {
        Product p = product(1L, 0);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));
        when(supplyHistoryRepo.findAllByProductIdInRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        supplyHistoryService.getChart(1L, null);

        verify(supplyHistoryRepo).findAllByProductIdInRange(eq(1L), fromCaptor.capture(), any(LocalDateTime.class));
        assertEquals(LocalDate.now().getYear(), fromCaptor.getValue().getYear());
    }

    @Test
    void getChart_buildsOnePointPerDay_usingLastEntryOfThatDay() {
        Product p = product(1L, 5);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));

        LocalDate day = LocalDate.of(2026, 3, 10);
        ProductSupplyHistory first = ProductSupplyHistory.builder()
                .id(1L).product(p).delta(5).supplyAfter(5)
                .source(SupplyChangeSource.PRODUCT_CREATED)
                .occurredAt(day.atTime(9, 0)).performedBy("SYSTEM").build();
        ProductSupplyHistory second = ProductSupplyHistory.builder()
                .id(2L).product(p).delta(-2).supplyAfter(3)
                .source(SupplyChangeSource.USAGE_RECORD)
                .occurredAt(day.atTime(15, 0)).performedBy("SYSTEM").build();

        when(supplyHistoryRepo.findAllByProductIdInRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(first, second));

        SupplyChartDTO result = supplyHistoryService.getChart(1L, 2026);

        assertEquals(1, result.getPoints().size());
        assertEquals(day, result.getPoints().get(0).getDate());
        assertEquals(3, result.getPoints().get(0).getSupply());
    }

    @Test
    void getSummary_throwsResourceNotFoundException_whenProductMissing() {
        when(productRepo.findByIdIncludingDeleted(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> supplyHistoryService.getSummary(99L));
    }

    @Test
    void getSummary_tileTotals_sumUpToCurrentSupply() {
        Product p = product(1L, 7);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));
        List<SupplySourceTotalProjection> projections = List.of(
                projection(SupplyChangeSource.KSEF_INVOICE, 20),
                projection(SupplyChangeSource.COMPANY_EXPENSE, 5),
                projection(SupplyChangeSource.VISIT_SALE, -10),
                projection(SupplyChangeSource.USAGE_RECORD, -6),
                projection(SupplyChangeSource.INVENTORY_REPORT, -2)
        );
        when(supplyHistoryRepo.sumDeltasBySource(1L)).thenReturn(projections);
        when(productRepo.findAllPurchases(1L)).thenReturn(List.of());

        SupplySummaryDTO result = supplyHistoryService.getSummary(1L);

        assertEquals(25, result.getTotalIn());
        assertEquals(-10, result.getTotalSold());
        assertEquals(-6, result.getTotalUsed());
        assertEquals(-2, result.getTotalAdjustments());
        assertEquals(7, result.getCurrentSupply());
        assertEquals(result.getCurrentSupply().intValue(),
                result.getTotalIn() + result.getTotalSold() + result.getTotalUsed() + result.getTotalAdjustments());
    }

    @Test
    void getSummary_countsCompanyExpenseDeletedIntoTotalIn_soDeletedPurchaseCancelsOut() {
        Product p = product(1L, 4);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));
        List<SupplySourceTotalProjection> projections = List.of(
                projection(SupplyChangeSource.INITIAL_BALANCE, 5),
                projection(SupplyChangeSource.COMPANY_EXPENSE, 3),
                projection(SupplyChangeSource.COMPANY_EXPENSE_DELETED, -3),
                projection(SupplyChangeSource.USAGE_RECORD, -1));
        when(supplyHistoryRepo.sumDeltasBySource(1L)).thenReturn(projections);
        when(productRepo.findAllPurchases(1L)).thenReturn(List.of());

        SupplySummaryDTO result = supplyHistoryService.getSummary(1L);

        assertEquals(5, result.getTotalIn());
        assertEquals(-1, result.getTotalUsed());
        assertEquals(0, result.getTotalAdjustments());
        assertEquals(result.getCurrentSupply().intValue(),
                result.getTotalIn() + result.getTotalSold() + result.getTotalUsed() + result.getTotalAdjustments());
    }

    @Test
    void getSummary_computesWeightedAvgNetPurchasePrice_fromPurchaseHistory() {
        Product p = product(1L, 10);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));
        when(supplyHistoryRepo.sumDeltasBySource(1L)).thenReturn(List.of());

        ProductPurchaseProjection purchase1 = mock(ProductPurchaseProjection.class);
        when(purchase1.getQuantity()).thenReturn(4);
        when(purchase1.getUnitNetPrice()).thenReturn(new BigDecimal("10.00"));

        ProductPurchaseProjection purchase2 = mock(ProductPurchaseProjection.class);
        when(purchase2.getQuantity()).thenReturn(6);
        when(purchase2.getUnitNetPrice()).thenReturn(new BigDecimal("20.00"));

        when(productRepo.findAllPurchases(1L)).thenReturn(List.of(purchase1, purchase2));

        SupplySummaryDTO result = supplyHistoryService.getSummary(1L);

        assertMoney(16.0, result.getAvgNetPurchasePrice());
        assertFalse(result.isFallbackPriceUsed());
        assertMoney(160.0, result.getStockValue());
    }

    @Test
    void getSummary_derivesNetPriceFromGrossAndVat_whenUnitNetPriceMissing() {
        Product p = product(1L, 1);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));
        when(supplyHistoryRepo.sumDeltasBySource(1L)).thenReturn(List.of());

        ProductPurchaseProjection purchase = mock(ProductPurchaseProjection.class);
        when(purchase.getQuantity()).thenReturn(1);
        when(purchase.getUnitNetPrice()).thenReturn(null);
        when(purchase.getUnitGrossPrice()).thenReturn(new BigDecimal("123.00"));
        when(purchase.getVatRate()).thenReturn("VAT_23");

        when(productRepo.findAllPurchases(1L)).thenReturn(List.of(purchase));

        SupplySummaryDTO result = supplyHistoryService.getSummary(1L);

        assertMoney(100.0, result.getAvgNetPurchasePrice());
    }

    @Test
    void getSummary_usesProductFallbackPrice_whenNoPurchaseHistory() {
        Product p = product(1L, 3);
        p.setFallbackNetPurchasePrice(new BigDecimal("12.50"));
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));
        when(supplyHistoryRepo.sumDeltasBySource(1L)).thenReturn(List.of());
        when(productRepo.findAllPurchases(1L)).thenReturn(List.of());

        SupplySummaryDTO result = supplyHistoryService.getSummary(1L);

        assertMoney(12.5, result.getAvgNetPurchasePrice());
        assertTrue(result.isFallbackPriceUsed());
    }

    @Test
    void getSummary_returnsZeroPrice_whenNoPurchaseHistoryAndNoFallback() {
        Product p = product(1L, 0);
        when(productRepo.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(p));
        when(supplyHistoryRepo.sumDeltasBySource(1L)).thenReturn(List.of());
        when(productRepo.findAllPurchases(1L)).thenReturn(List.of());

        SupplySummaryDTO result = supplyHistoryService.getSummary(1L);

        assertMoney(0.0, result.getAvgNetPurchasePrice());
        assertFalse(result.isFallbackPriceUsed());
    }
}

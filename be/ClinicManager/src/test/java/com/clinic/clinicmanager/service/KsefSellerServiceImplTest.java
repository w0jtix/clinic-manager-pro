package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.CompanyExpenseDTO;
import com.clinic.clinicmanager.DTO.KsefSellerDTO;
import com.clinic.clinicmanager.DTO.KsefSellerItemMappingDTO;
import com.clinic.clinicmanager.DTO.KsefSellerSummaryDTO;
import com.clinic.clinicmanager.DTO.KsefSpendChartDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.model.CompanyExpense;
import com.clinic.clinicmanager.model.KsefSeller;
import com.clinic.clinicmanager.model.KsefSellerItemMapping;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;
import com.clinic.clinicmanager.repo.CompanyExpenseRepo;
import com.clinic.clinicmanager.repo.KsefInvoiceRepo;
import com.clinic.clinicmanager.repo.KsefSellerItemMappingRepo;
import com.clinic.clinicmanager.repo.KsefSellerRepo;
import com.clinic.clinicmanager.repo.projection.KsefCategoryFacetProjection;
import com.clinic.clinicmanager.repo.projection.KsefMonthlySpendProjection;
import com.clinic.clinicmanager.repo.projection.KsefSellerSummaryProjection;
import com.clinic.clinicmanager.repo.projection.SellerManualExpenseSummaryProjection;
import com.clinic.clinicmanager.service.impl.KsefSellerServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KsefSellerServiceImplTest {

    @Mock
    private KsefSellerRepo sellerRepo;

    @Mock
    private KsefInvoiceRepo invoiceRepo;

    @Mock
    private KsefSellerItemMappingRepo mappingRepo;

    @Mock
    private CompanyExpenseRepo companyExpenseRepo;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ExpenseCategoryService expenseCategoryService;

    @InjectMocks
    private KsefSellerServiceImpl sellerService;

    private KsefSeller sampleSeller(boolean autoMark) {
        return KsefSeller.builder()
                .id(1L)
                .nip("1234567890")
                .name("Seller Sp. z o.o.")
                .autoMarkProducts(autoMark)
                .build();
    }

    private Product sampleProduct(Long id) {
        return Product.builder()
                .id(id)
                .name("Product " + id)
                .supply(5)
                .category(ProductCategory.builder().id(1L).name("Cat").color("ff0000").build())
                .brand(Brand.builder().id(1L).name("Brand").build())
                .build();
    }

    private KsefCategoryFacetProjection facet(String category, long count) {
        KsefCategoryFacetProjection projection = mock(KsefCategoryFacetProjection.class);
        lenient().when(projection.getCategory()).thenReturn(category);
        lenient().when(projection.getCnt()).thenReturn(count);
        return projection;
    }

    private KsefSellerSummaryProjection summaryProjection(Long invoiceCount, BigDecimal totalGross,
                                                            Long unpaidCount, Long awaitingCount,
                                                            Long uncategorizedCount) {
        KsefSellerSummaryProjection projection = mock(KsefSellerSummaryProjection.class);
        lenient().when(projection.getInvoiceCount()).thenReturn(invoiceCount);
        lenient().when(projection.getTotalGross()).thenReturn(totalGross);
        lenient().when(projection.getUnpaidCount()).thenReturn(unpaidCount);
        lenient().when(projection.getAwaitingVerificationCount()).thenReturn(awaitingCount);
        lenient().when(projection.getUncategorizedCount()).thenReturn(uncategorizedCount);
        return projection;
    }

    private KsefMonthlySpendProjection monthlySpend(int month, BigDecimal gross, long cnt) {
        KsefMonthlySpendProjection projection = mock(KsefMonthlySpendProjection.class);
        lenient().when(projection.getMonth()).thenReturn(month);
        lenient().when(projection.getGross()).thenReturn(gross);
        lenient().when(projection.getCnt()).thenReturn(cnt);
        return projection;
    }

    @Test
    void getSellerById_returnsMappedDTO_whenFound() {
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));

        KsefSellerDTO result = sellerService.getSellerById(1L);

        assertEquals(1L, result.getId());
        assertEquals("Seller Sp. z o.o.", result.getName());
    }

    @Test
    void getSellerById_throwsResourceNotFoundException_whenMissing() {
        when(sellerRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sellerService.getSellerById(99L));
    }

    @Test
    void getSummary_mapsTotalsAndFacets_whenSellerHasInvoices() {
        KsefSellerSummaryProjection summary = summaryProjection(10L, new BigDecimal("500.00"), 3L, 1L, 2L);
        List<KsefCategoryFacetProjection> facets = List.of(
                facet("EQUIPMENT", 4L),
                facet("SERVICES", 3L));
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));
        when(invoiceRepo.summarizeBySellerId(1L)).thenReturn(summary);
        when(invoiceRepo.countByCategoryForSeller(1L)).thenReturn(facets);

        KsefSellerSummaryDTO result = sellerService.getSummary(1L);

        assertEquals(10L, result.getInvoiceCount());
        assertEquals(0, new BigDecimal("500.00").compareTo(result.getTotalGross()));
        assertEquals(3L, result.getUnpaidCount());
        assertEquals(1L, result.getAwaitingVerificationCount());
        assertEquals(2L, result.getUncategorizedCount());
        assertEquals(2, result.getCategoryFacets().size());
    }

    @Test
    void getSummary_preservesNegativeSignOfTotalGross_forSellersWithCorrections() {
        KsefSellerSummaryProjection summary = summaryProjection(2L, new BigDecimal("-23.00"), 0L, 0L, 0L);
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));
        when(invoiceRepo.summarizeBySellerId(1L)).thenReturn(summary);
        when(invoiceRepo.countByCategoryForSeller(1L)).thenReturn(List.of());

        KsefSellerSummaryDTO result = sellerService.getSummary(1L);

        assertEquals(0, new BigDecimal("-23.00").compareTo(result.getTotalGross()));
    }

    @Test
    void getSummary_satisfiesInvariant_facetsPlusUncategorizedPlusAwaitingEqualsInvoiceCount() {
        KsefSellerSummaryProjection summary = summaryProjection(9L, new BigDecimal("100.00"), 1L, 2L, 3L);
        List<KsefCategoryFacetProjection> facets = List.of(facet("EQUIPMENT", 4L));
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));
        when(invoiceRepo.summarizeBySellerId(1L)).thenReturn(summary);
        when(invoiceRepo.countByCategoryForSeller(1L)).thenReturn(facets);

        KsefSellerSummaryDTO result = sellerService.getSummary(1L);

        long facetSum = result.getCategoryFacets().stream().mapToLong(f -> f.getCount()).sum();
        assertEquals(result.getInvoiceCount(),
                facetSum + result.getUncategorizedCount() + result.getAwaitingVerificationCount());
    }

    @Test
    void getSummary_returnsZerosAndEmptyFacetList_notNull_whenSellerHasNoInvoices() {
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));
        when(invoiceRepo.summarizeBySellerId(1L)).thenReturn(null);
        when(invoiceRepo.countByCategoryForSeller(1L)).thenReturn(List.of());

        KsefSellerSummaryDTO result = sellerService.getSummary(1L);

        assertEquals(0L, result.getInvoiceCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalGross()));
        assertEquals(0L, result.getUnpaidCount());
        assertEquals(0L, result.getAwaitingVerificationCount());
        assertEquals(0L, result.getUncategorizedCount());
        assertNotNull(result.getCategoryFacets());
        assertTrue(result.getCategoryFacets().isEmpty());
    }

    @Test
    void getSummary_throwsResourceNotFoundException_whenSellerMissing() {
        when(sellerRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sellerService.getSummary(99L));
        verifyNoInteractions(invoiceRepo);
    }

    @Test
    void getSpendChart_returnsTwelveZeroPoints_whenNoInvoicesInYear() {
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));
        when(invoiceRepo.sumMonthlySpendBySellerId(eq(1L), any(), any())).thenReturn(List.of());
        when(invoiceRepo.findFirstIssueDateBySellerId(1L)).thenReturn(null);
        when(invoiceRepo.findLastIssueDateBySellerId(1L)).thenReturn(null);

        KsefSpendChartDTO result = sellerService.getSpendChart(1L, 2025);

        assertEquals(12, result.getPoints().size());
        result.getPoints().forEach(point -> {
            assertEquals(0, BigDecimal.ZERO.compareTo(point.getGross()));
            assertEquals(0L, point.getInvoiceCount());
        });
    }

    @Test
    void getSpendChart_fillsOnlyMonthsWithData_leavingRestAtZero() {
        List<KsefMonthlySpendProjection> spend = List.of(monthlySpend(3, new BigDecimal("150.00"), 2L));
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));
        when(invoiceRepo.sumMonthlySpendBySellerId(eq(1L), any(), any())).thenReturn(spend);
        when(invoiceRepo.findFirstIssueDateBySellerId(1L)).thenReturn(LocalDate.of(2025, 3, 1));
        when(invoiceRepo.findLastIssueDateBySellerId(1L)).thenReturn(LocalDate.of(2025, 3, 1));

        KsefSpendChartDTO result = sellerService.getSpendChart(1L, 2025);

        assertEquals(0, new BigDecimal("150.00").compareTo(result.getPoints().get(2).getGross()));
        assertEquals(2L, result.getPoints().get(2).getInvoiceCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getPoints().get(0).getGross()));
    }

    @Test
    void getSpendChart_defaultsFirstAndLastYearToCurrentYear_whenSellerHasNoInvoices() {
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));
        when(invoiceRepo.sumMonthlySpendBySellerId(eq(1L), any(), any())).thenReturn(List.of());
        when(invoiceRepo.findFirstIssueDateBySellerId(1L)).thenReturn(null);
        when(invoiceRepo.findLastIssueDateBySellerId(1L)).thenReturn(null);

        KsefSpendChartDTO result = sellerService.getSpendChart(1L, null);

        int currentYear = LocalDate.now().getYear();
        assertEquals(currentYear, result.getFirstYear());
        assertEquals(currentYear, result.getLastYear());
    }

    @Test
    void getSpendChart_usesMinAndMaxIssueDateYears_whenPresent() {
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));
        when(invoiceRepo.sumMonthlySpendBySellerId(eq(1L), any(), any())).thenReturn(List.of());
        when(invoiceRepo.findFirstIssueDateBySellerId(1L)).thenReturn(LocalDate.of(2022, 5, 1));
        when(invoiceRepo.findLastIssueDateBySellerId(1L)).thenReturn(LocalDate.of(2025, 1, 1));

        KsefSpendChartDTO result = sellerService.getSpendChart(1L, 2023);

        assertEquals(2022, result.getFirstYear());
        assertEquals(2025, result.getLastYear());
    }

    @Test
    void getSpendChart_defaultsYearWindowToCurrentYear_whenYearParamAbsent() {
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));
        when(invoiceRepo.sumMonthlySpendBySellerId(eq(1L), any(), any())).thenReturn(List.of());
        when(invoiceRepo.findFirstIssueDateBySellerId(1L)).thenReturn(null);
        when(invoiceRepo.findLastIssueDateBySellerId(1L)).thenReturn(null);

        sellerService.getSpendChart(1L, null);

        int currentYear = LocalDate.now().getYear();
        verify(invoiceRepo).sumMonthlySpendBySellerId(1L,
                LocalDate.of(currentYear, 1, 1), LocalDate.of(currentYear, 12, 31));
    }

    @Test
    void getSpendChart_throwsResourceNotFoundException_whenSellerMissing() {
        when(sellerRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sellerService.getSpendChart(99L, null));
        verifyNoInteractions(invoiceRepo);
    }

    @Test
    void getItemMappings_returnsMappedList_whenSellerHasMappings() {
        KsefSellerItemMapping mapping = KsefSellerItemMapping.builder()
                .id(1L).seller(sampleSeller(false)).ksefItemName("Krem").product(sampleProduct(50L)).build();
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));
        when(mappingRepo.findBySellerIdWithProduct(1L)).thenReturn(List.of(mapping));

        List<KsefSellerItemMappingDTO> result = sellerService.getItemMappings(1L);

        assertEquals(1, result.size());
        assertEquals("Krem", result.getFirst().getKsefItemName());
        assertEquals(50L, result.getFirst().getProduct().getId());
    }

    @Test
    void getItemMappings_throwsResourceNotFoundException_whenSellerMissing() {
        when(sellerRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sellerService.getItemMappings(99L));
        verifyNoInteractions(mappingRepo);
    }

    @Test
    void deleteItemMapping_deletesMapping_andLogsAudit_whenBelongsToSeller() {
        KsefSeller seller = sampleSeller(false);
        KsefSellerItemMapping mapping = KsefSellerItemMapping.builder()
                .id(5L).seller(seller).ksefItemName("Krem").product(sampleProduct(50L)).build();
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(seller));
        when(mappingRepo.findById(5L)).thenReturn(Optional.of(mapping));

        sellerService.deleteItemMapping(1L, 5L);

        verify(mappingRepo).delete(mapping);
        verify(auditLogService).logDelete(eq("KsefSellerItemMapping"), eq(5L), eq("Krem"), any());
    }

    @Test
    void deleteItemMapping_doesNotTouchInvoicesOrProductAssignments() {
        KsefSeller seller = sampleSeller(false);
        KsefSellerItemMapping mapping = KsefSellerItemMapping.builder()
                .id(5L).seller(seller).ksefItemName("Krem").product(sampleProduct(50L)).build();
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(seller));
        when(mappingRepo.findById(5L)).thenReturn(Optional.of(mapping));

        sellerService.deleteItemMapping(1L, 5L);

        verifyNoInteractions(invoiceRepo);
    }

    @Test
    void deleteItemMapping_throwsResourceNotFoundException_whenMappingBelongsToDifferentSeller() {
        KsefSeller otherSeller = KsefSeller.builder().id(2L).nip("999").build();
        KsefSellerItemMapping mapping = KsefSellerItemMapping.builder()
                .id(5L).seller(otherSeller).ksefItemName("Krem").product(sampleProduct(50L)).build();
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));
        when(mappingRepo.findById(5L)).thenReturn(Optional.of(mapping));

        assertThrows(ResourceNotFoundException.class, () -> sellerService.deleteItemMapping(1L, 5L));
        verify(mappingRepo, never()).delete(any());
    }

    @Test
    void deleteItemMapping_throwsResourceNotFoundException_whenMappingMissing() {
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));
        when(mappingRepo.findById(5L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sellerService.deleteItemMapping(1L, 5L));
    }

    @Test
    void deleteItemMapping_throwsResourceNotFoundException_whenSellerMissing() {
        when(sellerRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sellerService.deleteItemMapping(99L, 5L));
        verifyNoInteractions(mappingRepo);
    }

    @Test
    void setDefaultCategory_byId_setsProductsPair_withAutoMarkTrue() {
        KsefSeller seller = sampleSeller(false);
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(seller));
        when(sellerRepo.save(any(KsefSeller.class))).thenAnswer(inv -> inv.getArgument(0));

        sellerService.setDefaultCategory(1L, ExpenseCategoryCode.PRODUCTS);

        assertEquals(ExpenseCategoryCode.PRODUCTS, seller.getDefaultExpenseCategory());
        assertTrue(seller.getAutoMarkProducts());
        verify(sellerRepo).save(seller);
        verify(auditLogService).logUpdate(eq("KsefSeller"), eq(1L), any(), any(), any());
    }

    @Test
    void setDefaultCategory_setsOtherCategoryPair_withAutoMarkFalse() {
        KsefSeller seller = sampleSeller(true);
        seller.setDefaultExpenseCategory(ExpenseCategoryCode.PRODUCTS);
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(seller));
        when(sellerRepo.save(any(KsefSeller.class))).thenAnswer(inv -> inv.getArgument(0));

        sellerService.setDefaultCategory(1L, "EQUIPMENT");

        assertEquals("EQUIPMENT", seller.getDefaultExpenseCategory());
        assertFalse(seller.getAutoMarkProducts());
    }

    @Test
    void setDefaultCategory_setsNullPair_whenCategoryNull() {
        KsefSeller seller = sampleSeller(true);
        seller.setDefaultExpenseCategory(ExpenseCategoryCode.PRODUCTS);
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(seller));
        when(sellerRepo.save(any(KsefSeller.class))).thenAnswer(inv -> inv.getArgument(0));

        sellerService.setDefaultCategory(1L, null);

        assertNull(seller.getDefaultExpenseCategory());
        assertFalse(seller.getAutoMarkProducts());
    }

    @ParameterizedTest
    @ValueSource(strings = {"RENT", "TAXES", "ZUS", "SALARY", "MIXED"})
    void setDefaultCategory_throwsUpdateException_forForbiddenCategories(String forbidden) {
        KsefSeller seller = sampleSeller(false);
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(seller));

        assertThrows(UpdateException.class, () -> sellerService.setDefaultCategory(1L, forbidden));
        verify(sellerRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void setDefaultCategory_isIdempotent_whenPairAlreadyMatchesNonProductsCategory() {
        KsefSeller seller = sampleSeller(false);
        seller.setDefaultExpenseCategory("EQUIPMENT");
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(seller));

        sellerService.setDefaultCategory(1L, "EQUIPMENT");

        verify(sellerRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void setDefaultCategory_isIdempotent_whenPairAlreadyMatchesProducts() {
        KsefSeller seller = sampleSeller(true);
        seller.setDefaultExpenseCategory(ExpenseCategoryCode.PRODUCTS);
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(seller));

        sellerService.setDefaultCategory(1L, ExpenseCategoryCode.PRODUCTS);

        verify(sellerRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void setDefaultCategory_byEntity_isNoOp_whenSellerNull() {
        sellerService.setDefaultCategory((KsefSeller) null, "EQUIPMENT");

        verifyNoInteractions(sellerRepo, auditLogService);
    }

    @Test
    void setDefaultCategory_byId_throwsResourceNotFoundException_whenSellerMissing() {
        when(sellerRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> sellerService.setDefaultCategory(99L, "EQUIPMENT"));
    }

    @Test
    void removeAutoMark_setsAutoMarkProductsToFalse_andPersists() {
        KsefSeller seller = sampleSeller(true);
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(seller));
        when(sellerRepo.save(any(KsefSeller.class))).thenAnswer(inv -> inv.getArgument(0));

        sellerService.removeAutoMark(1L);

        assertFalse(seller.getAutoMarkProducts());
        verify(sellerRepo).save(seller);
        verify(auditLogService).logUpdate(eq("KsefSeller"), eq(1L), eq("Seller Sp. z o.o."), any(), any());
    }

    @Test
    void removeAutoMark_clearsBothAutoMarkAndDefaultCategory() {
        KsefSeller seller = sampleSeller(true);
        seller.setDefaultExpenseCategory(ExpenseCategoryCode.PRODUCTS);
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(seller));
        when(sellerRepo.save(any(KsefSeller.class))).thenAnswer(inv -> inv.getArgument(0));

        sellerService.removeAutoMark(1L);

        assertFalse(seller.getAutoMarkProducts());
        assertNull(seller.getDefaultExpenseCategory());
    }

    @Test
    void removeAutoMark_isNoOp_whenAlreadyFalse() {
        KsefSeller seller = sampleSeller(false);
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(seller));

        sellerService.removeAutoMark(1L);

        verify(sellerRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void removeAutoMark_throwsResourceNotFoundException_whenIdInvalid() {
        when(sellerRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sellerService.removeAutoMark(99L));
    }

    private SellerManualExpenseSummaryProjection manualSummary(Long count, BigDecimal total) {
        SellerManualExpenseSummaryProjection projection = mock(SellerManualExpenseSummaryProjection.class);
        lenient().when(projection.getExpenseCount()).thenReturn(count);
        lenient().when(projection.getTotalValue()).thenReturn(total);
        return projection;
    }

    @Test
    void getSellerByNip_returnsMappedDTO_andNormalizesNip() {
        when(sellerRepo.findByNip("5261040828")).thenReturn(Optional.of(sampleSeller(false)));

        KsefSellerDTO result = sellerService.getSellerByNip(" 526-104-08 28 ");

        assertEquals(1L, result.getId());
        assertEquals("Seller Sp. z o.o.", result.getName());
    }

    @Test
    void getSellerByNip_throwsResourceNotFoundException_whenMissing() {
        when(sellerRepo.findByNip("5261040828")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sellerService.getSellerByNip("5261040828"));
    }

    @Test
    void getSellerByNip_normalizesForeignVatId_beforeLookup() {
        KsefSeller foreign = KsefSeller.builder().id(5L).nip("DE123456789").name("GmbH").build();
        when(sellerRepo.findByNip("DE123456789")).thenReturn(Optional.of(foreign));

        KsefSellerDTO result = sellerService.getSellerByNip("de 123 456 789");

        assertEquals(5L, result.getId());
        assertEquals("DE123456789", result.getNip());
        verify(sellerRepo).findByNip("DE123456789");
    }

    @Test
    void getSellerByNip_stripsPolishPrefix_beforeLookup() {
        when(sellerRepo.findByNip("1234567890")).thenReturn(Optional.of(sampleSeller(false)));

        KsefSellerDTO result = sellerService.getSellerByNip("PL1234567890");

        assertEquals(1L, result.getId());
        verify(sellerRepo).findByNip("1234567890");
    }

    @Test
    void getSellerByNip_throwsResourceNotFoundException_whenForeignVatIdMissing() {
        when(sellerRepo.findByNip("DE123456789")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sellerService.getSellerByNip("de-123456789"));
    }

    @Test
    void getSummary_includesManualExpenseCountAndTotal_withoutChangingInvoiceFields() {
        KsefSellerSummaryProjection summary = summaryProjection(2L, new BigDecimal("200.00"), 0L, 0L, 0L);
        SellerManualExpenseSummaryProjection manual = manualSummary(3L, new BigDecimal("450.50"));
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));
        when(invoiceRepo.summarizeBySellerId(1L)).thenReturn(summary);
        when(invoiceRepo.countByCategoryForSeller(1L)).thenReturn(List.of());
        when(companyExpenseRepo.summarizeBySellerId(1L)).thenReturn(manual);

        KsefSellerSummaryDTO result = sellerService.getSummary(1L);

        assertEquals(2L, result.getInvoiceCount());
        assertEquals(0, new BigDecimal("200.00").compareTo(result.getTotalGross()));
        assertEquals(3L, result.getManualExpenseCount());
        assertEquals(0, new BigDecimal("450.50").compareTo(result.getManualExpenseTotal()));
    }

    @Test
    void getSummary_returnsZeroManualFields_whenSellerHasNoManualExpenses() {
        SellerManualExpenseSummaryProjection manual = manualSummary(0L, null);
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));
        when(invoiceRepo.summarizeBySellerId(1L)).thenReturn(null);
        when(invoiceRepo.countByCategoryForSeller(1L)).thenReturn(List.of());
        when(companyExpenseRepo.summarizeBySellerId(1L)).thenReturn(manual);

        KsefSellerSummaryDTO result = sellerService.getSummary(1L);

        assertEquals(0L, result.getManualExpenseCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getManualExpenseTotal()));
    }

    @Test
    void getSpendChart_sumsInvoicesAndManualExpensesPerMonth() {
        List<KsefMonthlySpendProjection> invoiceSpend = List.of(monthlySpend(3, new BigDecimal("150.00"), 2L));
        List<KsefMonthlySpendProjection> manualSpend = List.of(
                monthlySpend(3, new BigDecimal("50.00"), 1L),
                monthlySpend(5, new BigDecimal("80.00"), 2L));
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));
        when(invoiceRepo.sumMonthlySpendBySellerId(eq(1L), any(), any())).thenReturn(invoiceSpend);
        when(companyExpenseRepo.sumMonthlySpendBySellerId(eq(1L), any(), any())).thenReturn(manualSpend);

        KsefSpendChartDTO result = sellerService.getSpendChart(1L, 2025);

        assertEquals(0, new BigDecimal("200.00").compareTo(result.getPoints().get(2).getGross()));
        assertEquals(2L, result.getPoints().get(2).getInvoiceCount());
        assertEquals(1L, result.getPoints().get(2).getManualExpenseCount());
        assertEquals(0, new BigDecimal("80.00").compareTo(result.getPoints().get(4).getGross()));
        assertEquals(0L, result.getPoints().get(4).getInvoiceCount());
        assertEquals(2L, result.getPoints().get(4).getManualExpenseCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getPoints().get(0).getGross()));
    }

    @Test
    void getSpendChart_yearRangeSpansInvoicesAndManualExpenses() {
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));
        when(invoiceRepo.sumMonthlySpendBySellerId(eq(1L), any(), any())).thenReturn(List.of());
        when(companyExpenseRepo.sumMonthlySpendBySellerId(eq(1L), any(), any())).thenReturn(List.of());
        when(invoiceRepo.findFirstIssueDateBySellerId(1L)).thenReturn(LocalDate.of(2023, 5, 1));
        when(invoiceRepo.findLastIssueDateBySellerId(1L)).thenReturn(LocalDate.of(2024, 1, 1));
        when(companyExpenseRepo.findFirstExpenseDateBySellerId(1L)).thenReturn(LocalDate.of(2021, 2, 1));
        when(companyExpenseRepo.findLastExpenseDateBySellerId(1L)).thenReturn(LocalDate.of(2026, 3, 1));

        KsefSpendChartDTO result = sellerService.getSpendChart(1L, 2024);

        assertEquals(2021, result.getFirstYear());
        assertEquals(2026, result.getLastYear());
    }

    @Test
    void getSpendChart_usesManualExpenseYears_whenSellerHasNoInvoices() {
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(sampleSeller(false)));
        when(invoiceRepo.sumMonthlySpendBySellerId(eq(1L), any(), any())).thenReturn(List.of());
        when(companyExpenseRepo.sumMonthlySpendBySellerId(eq(1L), any(), any())).thenReturn(List.of());
        when(companyExpenseRepo.findFirstExpenseDateBySellerId(1L)).thenReturn(LocalDate.of(2022, 2, 1));
        when(companyExpenseRepo.findLastExpenseDateBySellerId(1L)).thenReturn(LocalDate.of(2023, 3, 1));

        KsefSpendChartDTO result = sellerService.getSpendChart(1L, 2023);

        assertEquals(2022, result.getFirstYear());
        assertEquals(2023, result.getLastYear());
    }

    @Test
    void getManualExpenses_returnsMappedPage_withRequestedPaging() {
        KsefSeller seller = sampleSeller(false);
        CompanyExpense expense = CompanyExpense.builder()
                .id(7L).source("Seller Sp. z o.o.").expenseDate(LocalDate.of(2026, 9, 1))
                .category("OTHER").seller(seller).build();
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(seller));
        when(companyExpenseRepo.findBySellerId(1L, PageRequest.of(2, 5)))
                .thenReturn(new PageImpl<>(List.of(expense), PageRequest.of(2, 5), 11));

        Page<CompanyExpenseDTO> result = sellerService.getManualExpenses(1L, 2, 5);

        assertEquals(1, result.getContent().size());
        assertEquals(7L, result.getContent().get(0).getId());
        assertEquals(1L, result.getContent().get(0).getSeller().getId());
        assertEquals("MANUAL", result.getContent().get(0).getSourceType());
        assertEquals(11, result.getTotalElements());
    }

    @Test
    void getManualExpenses_throwsResourceNotFoundException_whenSellerMissing() {
        when(sellerRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sellerService.getManualExpenses(99L, 0, 20));
        verifyNoInteractions(companyExpenseRepo);
    }
    @Test
    void setDefaultCategory_throwsValidation_whenCategoryUnknown() {
        KsefSeller seller = sampleSeller(false);
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(seller));
        doThrow(new ValidationException("Nieznana kategoria wydatku.")).when(expenseCategoryService).requireExistingCode("XYZ");

        ValidationException ex = assertThrows(ValidationException.class, () -> sellerService.setDefaultCategory(1L, "XYZ"));

        assertEquals("Nieznana kategoria wydatku.", ex.getMessage());
        assertNull(seller.getDefaultExpenseCategory());
        verify(sellerRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void setDefaultCategory_skipsCodeValidation_whenCategoryNull() {
        KsefSeller seller = sampleSeller(false);
        seller.setDefaultExpenseCategory("CAR");
        when(sellerRepo.findById(1L)).thenReturn(Optional.of(seller));
        when(sellerRepo.save(any(KsefSeller.class))).thenAnswer(inv -> inv.getArgument(0));

        sellerService.setDefaultCategory(1L, null);

        assertNull(seller.getDefaultExpenseCategory());
        verifyNoInteractions(expenseCategoryService);
    }
}

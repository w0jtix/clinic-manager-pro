package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.KsefCorrectionMatchDTO;
import com.clinic.clinicmanager.DTO.KsefInvoiceDTO;
import com.clinic.clinicmanager.DTO.StockChangePreviewDTO;
import com.clinic.clinicmanager.DTO.StockChangeProductDTO;
import com.clinic.clinicmanager.DTO.request.KsefBookCorrectionItemDTO;
import com.clinic.clinicmanager.DTO.request.KsefBookCorrectionRequestDTO;
import com.clinic.clinicmanager.DTO.request.KsefInvoiceFilterDTO;
import com.clinic.clinicmanager.DTO.request.KsefVerifyProductsItemDTO;
import com.clinic.clinicmanager.DTO.request.KsefVerifyProductsRequestDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.exceptions.StockChangeBlockedException;
import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.model.KsefInvoice;
import com.clinic.clinicmanager.model.KsefInvoiceItem;
import com.clinic.clinicmanager.model.KsefSeller;
import com.clinic.clinicmanager.model.KsefSellerItemMapping;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;
import com.clinic.clinicmanager.model.constants.KsefCorrectionMatchType;
import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.KsefInvoiceRepo;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.service.impl.KsefInvoiceServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KsefInvoiceServiceImplTest {

    private static void assertMoney(double expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    @Mock
    private KsefInvoiceRepo invoiceRepo;

    @Mock
    private ProductRepo productRepo;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private KsefSellerItemMappingService mappingService;

    @Mock
    private KsefSellerService sellerService;

    @Mock
    private SupplyHistoryService supplyHistoryService;

    @Mock
    private ExpenseCategoryService expenseCategoryService;

    @InjectMocks
    private KsefInvoiceServiceImpl invoiceService;

    private Product sampleProduct(Long id, Integer supply) {
        return Product.builder()
                .id(id)
                .name("Product " + id)
                .supply(supply)
                .category(ProductCategory.builder().id(1L).name("Cat").color("ff0000").build())
                .brand(Brand.builder().id(1L).name("Brand").build())
                .build();
    }

    private Product produktyProduct(Long id, Integer supply) {
        return Product.builder()
                .id(id)
                .name("Product " + id)
                .supply(supply)
                .category(ProductCategory.builder().id(2L).name("Produkty").color("00ff00").isSystem(true).build())
                .brand(Brand.builder().id(1L).name("Brand").build())
                .build();
    }

    private Product productInCategory(Long id, String categoryName, boolean system) {
        return Product.builder()
                .id(id)
                .name("Product " + id)
                .supply(0)
                .category(ProductCategory.builder().id(3L).name(categoryName).color("0000ff").isSystem(system).build())
                .brand(Brand.builder().id(1L).name("Brand").build())
                .build();
    }

    private KsefSeller sampleSeller() {
        return KsefSeller.builder()
                .id(10L)
                .nip("1234567890")
                .name("Seller Sp. z o.o.")
                .address("ul. Testowa 1")
                .autoMarkProducts(false)
                .build();
    }

    private KsefInvoice sampleInvoice() {
        return KsefInvoice.builder()
                .id(1L)
                .ksefReferenceNumber("20240115-FA-001")
                .invoiceNumber("FV/2024/01/001")
                .invoiceType("VAT")
                .seller(sampleSeller())
                .buyerNip("0987654321")
                .issueDate(LocalDate.of(2024, 1, 15))
                .grossAmount(new BigDecimal("123.00"))
                .netAmount(new BigDecimal("100.00"))
                .vatAmount(new BigDecimal("23.00"))
                .currency("PLN")
                .isPaid(true)
                .awaitingProductVerification(false)
                .rawXml("<Faktura/>")
                .build();
    }

    @Test
    void getInvoices_returnsMappedPage_withCorrectContent() {
        KsefInvoiceFilterDTO filter = new KsefInvoiceFilterDTO();
        Page<KsefInvoice> repoPage = new PageImpl<>(List.of(sampleInvoice()), PageRequest.of(0, 10), 1);
        when(invoiceRepo.findAllWithFilters(any(), any(), any(), any(), anyBoolean(), anyBoolean(),
                any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(repoPage);

        Page<KsefInvoiceDTO> result = invoiceService.getInvoices(filter, 0, 10);

        assertEquals(1, result.getTotalElements());
        KsefInvoiceDTO dto = result.getContent().getFirst();
        assertEquals("FV/2024/01/001", dto.getInvoiceNumber());
        assertNotNull(dto.getSeller());
        assertEquals("Seller Sp. z o.o.", dto.getSeller().getName());
        assertTrue(dto.getIsPaid());
    }

    @Test
    void getInvoices_passesComputedDatesToRepo() {
        KsefInvoiceFilterDTO filter = new KsefInvoiceFilterDTO();
        filter.setYear(2024);
        filter.setMonth(1);
        when(invoiceRepo.findAllWithFilters(any(), any(), any(), any(), anyBoolean(), anyBoolean(),
                any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(Page.empty());

        invoiceService.getInvoices(filter, 0, 10);

        verify(invoiceRepo).findAllWithFilters(
                eq(LocalDate.of(2024, 1, 1)), eq(LocalDate.of(2024, 1, 31)), isNull(), isNull(),
                eq(true), eq(false), eq(List.of("")), eq(false), eq(false), any());
    }

    @Test
    void getInvoices_passesNullDatesToRepo_whenYearAndMonthAbsent() {
        KsefInvoiceFilterDTO filter = new KsefInvoiceFilterDTO();
        when(invoiceRepo.findAllWithFilters(any(), any(), any(), any(), anyBoolean(), anyBoolean(),
                any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(Page.empty());

        invoiceService.getInvoices(filter, 0, 10);

        verify(invoiceRepo).findAllWithFilters(isNull(), isNull(), isNull(), isNull(),
                eq(true), eq(false), eq(List.of("")), eq(false), eq(false), any());
    }

    @Test
    void getInvoices_combinesSellerIdWithYearMonthAndIsPaid() {
        KsefInvoiceFilterDTO filter = new KsefInvoiceFilterDTO();
        filter.setSellerId(10L);
        filter.setYear(2024);
        filter.setMonth(3);
        filter.setIsPaid(false);
        when(invoiceRepo.findAllWithFilters(any(), any(), any(), any(), anyBoolean(), anyBoolean(),
                any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(Page.empty());

        invoiceService.getInvoices(filter, 0, 10);

        verify(invoiceRepo).findAllWithFilters(
                eq(LocalDate.of(2024, 3, 1)), eq(LocalDate.of(2024, 3, 31)), eq(10L), eq(false),
                eq(true), eq(false), eq(List.of("")), eq(false), eq(false), any());
    }

    @Test
    void getInvoices_combinesCategoriesAndUncategorized_asSeparateOrBuckets() {
        KsefInvoiceFilterDTO filter = new KsefInvoiceFilterDTO();
        filter.setCategories(List.of("EQUIPMENT"));
        filter.setUncategorized(true);
        when(invoiceRepo.findAllWithFilters(any(), any(), any(), any(), anyBoolean(), anyBoolean(),
                any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(Page.empty());

        invoiceService.getInvoices(filter, 0, 10);

        verify(invoiceRepo).findAllWithFilters(
                isNull(), isNull(), isNull(), isNull(),
                eq(false), eq(true), eq(List.of("EQUIPMENT")), eq(true), eq(false), any());
    }

    @Test
    void getInvoices_passesAwaitingVerificationFlag_withoutCategoryFilter() {
        KsefInvoiceFilterDTO filter = new KsefInvoiceFilterDTO();
        filter.setAwaitingVerification(true);
        when(invoiceRepo.findAllWithFilters(any(), any(), any(), any(), anyBoolean(), anyBoolean(),
                any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(Page.empty());

        invoiceService.getInvoices(filter, 0, 10);

        verify(invoiceRepo).findAllWithFilters(
                isNull(), isNull(), isNull(), isNull(),
                eq(false), eq(false), eq(List.of("")), eq(false), eq(true), any());
    }

    @Test
    void getInvoices_treatsEmptyCategoriesList_asNoCategoryFilter() {
        KsefInvoiceFilterDTO filter = new KsefInvoiceFilterDTO();
        filter.setCategories(List.of());
        when(invoiceRepo.findAllWithFilters(any(), any(), any(), any(), anyBoolean(), anyBoolean(),
                any(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(Page.empty());

        invoiceService.getInvoices(filter, 0, 10);

        verify(invoiceRepo).findAllWithFilters(
                isNull(), isNull(), isNull(), isNull(),
                eq(true), eq(false), eq(List.of("")), eq(false), eq(false), any());
    }

    @Test
    void getInvoiceById_returnsDetailDTO_whenFound() {
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(sampleInvoice()));

        KsefInvoiceDTO result = invoiceService.getInvoiceById(1L);

        assertEquals(1L, result.getId());
        assertEquals("FV/2024/01/001", result.getInvoiceNumber());
    }

    @Test
    void getInvoiceById_throwsResourceNotFoundException_whenNotFound() {
        when(invoiceRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> invoiceService.getInvoiceById(99L));
    }

    @Test
    void togglePaidStatus_flipsTrueToFalse_andPersists() {
        KsefInvoice invoice = sampleInvoice();
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefInvoiceDTO result = invoiceService.togglePaidStatus(1L);

        assertFalse(result.getIsPaid());
        assertFalse(invoice.getIsPaid());
        verify(invoiceRepo).save(invoice);
    }

    @Test
    void togglePaidStatus_flipsFalseToTrue() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setIsPaid(false);
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefInvoiceDTO result = invoiceService.togglePaidStatus(1L);

        assertTrue(result.getIsPaid());
    }

    @Test
    void togglePaidStatus_flipsNullToTrue() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setIsPaid(null);
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefInvoiceDTO result = invoiceService.togglePaidStatus(1L);

        assertTrue(result.getIsPaid());
    }

    @Test
    void togglePaidStatus_throwsResourceNotFoundException_whenIdInvalid() {
        when(invoiceRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> invoiceService.togglePaidStatus(99L));
    }

    @Test
    void toggleProductInvoice_movesRegularToPending() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(false);
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefInvoiceDTO result = invoiceService.toggleProductInvoice(1L);

        assertTrue(result.isAwaitingProductVerification());
        assertTrue(invoice.isAwaitingProductVerification());
        verify(invoiceRepo).save(invoice);
    }

    @Test
    void toggleProductInvoice_clearsCategory_whenMovingToPending() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(false);
        invoice.setCategory("SERVICES");
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefInvoiceDTO result = invoiceService.toggleProductInvoice(1L);

        assertTrue(result.isAwaitingProductVerification());
        assertNull(result.getCategory());
        assertNull(invoice.getCategory());
    }

    @Test
    void toggleProductInvoice_movesPendingBackToRegular() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefInvoiceDTO result = invoiceService.toggleProductInvoice(1L);

        assertFalse(result.isAwaitingProductVerification());
    }

    @Test
    void toggleProductInvoice_throwsConflict_whenAlreadyVerifiedProducts() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setCategory(ExpenseCategoryCode.PRODUCTS);
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));

        assertThrows(ConflictException.class, () -> invoiceService.toggleProductInvoice(1L));
        verify(invoiceRepo, never()).save(any(KsefInvoice.class));
    }

    @Test
    void toggleProductInvoice_throwsResourceNotFoundException_whenIdInvalid() {
        when(invoiceRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> invoiceService.toggleProductInvoice(99L));
    }

    @Test
    void verifyProducts_assignsProducts_addsToStock_andSetsStatusToProducts() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);

        KsefInvoiceItem item = KsefInvoiceItem.builder()
                .id(100L)
                .ksefItemName("Krem nawilżający 50ml")
                .ksefGtin("5901234567890")
                .quantity(new BigDecimal("1.5"))
                .build();
        invoice.addItem(item);

        Product product = sampleProduct(50L, 10);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(productRepo.findOneById(50L)).thenReturn(Optional.of(product));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(100L, 50L, 2, true)
        ), false);

        KsefInvoiceDTO result = invoiceService.verifyProducts(1L, request);

        assertEquals(ExpenseCategoryCode.PRODUCTS, result.getCategory());
        assertFalse(result.isAwaitingProductVerification());
        assertSame(product, item.getProduct());
        assertEquals(new BigDecimal("1.5"), item.getQuantity());
        assertEquals(Integer.valueOf(2), item.getDeclaredQuantity());
        assertEquals(12, product.getSupply());
        verify(mappingService).saveOrUpdate(invoice.getSeller(),
                "Krem nawilżający 50ml", "5901234567890", product);
    }

    @Test
    void verifyProducts_shouldRecordSupplyHistory_withKsefInvoiceSource() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);

        KsefInvoiceItem item = KsefInvoiceItem.builder()
                .id(100L)
                .ksefItemName("Krem nawilżający 50ml")
                .quantity(new BigDecimal("1.5"))
                .build();
        invoice.addItem(item);

        Product product = sampleProduct(50L, 10);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(productRepo.findOneById(50L)).thenReturn(Optional.of(product));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(100L, 50L, 2, false)
        ), false);

        invoiceService.verifyProducts(1L, request);

        verify(supplyHistoryService).record(product, 2, SupplyChangeSource.KSEF_INVOICE,
                invoice.getId(), invoice.getInvoiceNumber());
    }

    @Test
    void verifyProducts_skipsItemsWithoutProductAssignment() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);

        KsefInvoiceItem item = KsefInvoiceItem.builder()
                .id(100L)
                .ksefItemName("Opłata transportowa")
                .quantity(new BigDecimal("1"))
                .build();
        invoice.addItem(item);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(100L, null, null, false)
        ), false);

        KsefInvoiceDTO result = invoiceService.verifyProducts(1L, request);

        assertEquals(ExpenseCategoryCode.PRODUCTS, result.getCategory());
        assertFalse(result.isAwaitingProductVerification());
        assertNull(item.getProduct());
        verify(productRepo, never()).findOneById(any());
        verify(mappingService, never()).saveOrUpdate(any(), any(), any(), any());
    }

    @Test
    void verifyProducts_doesNotSaveMapping_whenRememberFalse() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);
        KsefInvoiceItem item = KsefInvoiceItem.builder().id(100L).ksefItemName("X").build();
        invoice.addItem(item);

        Product product = sampleProduct(50L, 0);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(productRepo.findOneById(50L)).thenReturn(Optional.of(product));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(100L, 50L, 3, false)
        ), false);

        invoiceService.verifyProducts(1L, request);

        verify(mappingService, never()).saveOrUpdate(any(), any(), any(), any());
    }

    @Test
    void verifyProducts_throwsConflict_whenStatusNotPending() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(false);
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(), false);

        assertThrows(ConflictException.class, () -> invoiceService.verifyProducts(1L, request));
    }

    @Test
    void verifyProducts_throwsConflict_whenItemDoesNotBelongToInvoice() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(999L, 50L, 1, false)
        ), false);

        assertThrows(ConflictException.class, () -> invoiceService.verifyProducts(1L, request));
    }

    @Test
    void verifyProducts_throwsConflict_whenQuantityNullForAssignedItem() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);
        KsefInvoiceItem item = KsefInvoiceItem.builder().id(100L).ksefItemName("X").build();
        invoice.addItem(item);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(100L, 50L, null, false)
        ), false);

        assertThrows(ConflictException.class, () -> invoiceService.verifyProducts(1L, request));
    }

    @Test
    void verifyProducts_throwsResourceNotFound_whenProductMissing() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);
        KsefInvoiceItem item = KsefInvoiceItem.builder().id(100L).ksefItemName("X").build();
        invoice.addItem(item);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(productRepo.findOneById(50L)).thenReturn(Optional.empty());

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(100L, 50L, 1, false)
        ), false);

        assertThrows(ResourceNotFoundException.class, () -> invoiceService.verifyProducts(1L, request));
    }

    @Test
    void verifyProducts_throwsResourceNotFoundException_whenInvoiceMissing() {
        when(invoiceRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> invoiceService.verifyProducts(99L, new KsefVerifyProductsRequestDTO()));
    }

    @Test
    void verifyProducts_throwsConflict_whenAssigningSoftDeletedProduct() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);
        KsefInvoiceItem item = KsefInvoiceItem.builder().id(100L).ksefItemName("X").build();
        invoice.addItem(item);

        Product deleted = sampleProduct(50L, 0);
        deleted.setIsDeleted(true);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(productRepo.findOneById(50L)).thenReturn(Optional.of(deleted));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(100L, 50L, 1, false)
        ), false);

        assertThrows(ConflictException.class, () -> invoiceService.verifyProducts(1L, request));
    }

    @Test
    void getInvoiceById_setsSuggestedProductId_forUnassignedItems() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);
        KsefInvoiceItem item = KsefInvoiceItem.builder()
                .id(100L)
                .ksefItemName("Krem nawilżający 50ml")
                .ksefGtin("5901234567890")
                .build();
        invoice.addItem(item);

        Product suggested = sampleProduct(77L, 5);
        KsefSellerItemMapping mapping = KsefSellerItemMapping.builder()
                .id(1L).product(suggested).build();

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(mappingService.findFor(10L, "Krem nawilżający 50ml", "5901234567890"))
                .thenReturn(Optional.of(mapping));

        KsefInvoiceDTO result = invoiceService.getInvoiceById(1L);

        assertEquals(1, result.getItems().size());
        assertEquals(77L, result.getItems().getFirst().getSuggestedProductId());
        assertNull(result.getItems().getFirst().getProduct());
    }

    @Test
    void getInvoiceById_doesNotSetSuggestion_whenItemAlreadyHasProduct() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setCategory(ExpenseCategoryCode.PRODUCTS);
        Product assigned = sampleProduct(50L, 10);
        KsefInvoiceItem item = KsefInvoiceItem.builder()
                .id(100L).ksefItemName("X").product(assigned).build();
        invoice.addItem(item);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));

        KsefInvoiceDTO result = invoiceService.getInvoiceById(1L);

        assertEquals(1, result.getItems().size());
        assertNull(result.getItems().getFirst().getSuggestedProductId());
        assertNotNull(result.getItems().getFirst().getProduct());
        verify(mappingService, never()).findFor(any(), any(), any());
    }

    @Test
    void getInvoiceById_setsNullSuggestion_whenMappingMissing() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);
        KsefInvoiceItem item = KsefInvoiceItem.builder()
                .id(100L).ksefItemName("New item").build();
        invoice.addItem(item);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(mappingService.findFor(any(), any(), any())).thenReturn(Optional.empty());

        KsefInvoiceDTO result = invoiceService.getInvoiceById(1L);

        assertEquals(1, result.getItems().size());
        assertNull(result.getItems().getFirst().getSuggestedProductId());
    }

    @Test
    void getInvoiceById_doesNotSetSuggestion_whenSellerNull() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setSeller(null);
        invoice.setAwaitingProductVerification(true);
        KsefInvoiceItem item = KsefInvoiceItem.builder()
                .id(100L).ksefItemName("New item").build();
        invoice.addItem(item);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));

        KsefInvoiceDTO result = invoiceService.getInvoiceById(1L);

        assertEquals(1, result.getItems().size());
        assertNull(result.getItems().getFirst().getSuggestedProductId());
        verify(mappingService, never()).findFor(any(), any(), any());
    }

    @Test
    void verifyProducts_setsPurchasePrice_fromNetValueDividedByQuantity_forProduktyCategory() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);

        KsefInvoiceItem item = KsefInvoiceItem.builder()
                .id(100L)
                .ksefItemName("Krem")
                .netValue(new BigDecimal("30.00"))
                .vatRate(VatRate.VAT_23)
                .build();
        invoice.addItem(item);

        Product product = produktyProduct(50L, 0);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(productRepo.findOneById(50L)).thenReturn(Optional.of(product));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(100L, 50L, 3, false)
        ), false);

        invoiceService.verifyProducts(1L, request);

        assertMoney(10.0, product.getFallbackNetPurchasePrice());
        assertEquals(VatRate.VAT_23, product.getFallbackVatRate());
    }

    @Test
    void verifyProducts_doesNotSetPurchasePrice_forNonProduktyCategory() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);

        KsefInvoiceItem item = KsefInvoiceItem.builder()
                .id(100L)
                .ksefItemName("Narzędzie")
                .netValue(new BigDecimal("30.00"))
                .vatRate(VatRate.VAT_23)
                .build();
        invoice.addItem(item);

        Product product = sampleProduct(50L, 0);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(productRepo.findOneById(50L)).thenReturn(Optional.of(product));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(100L, 50L, 3, false)
        ), false);

        invoiceService.verifyProducts(1L, request);

        assertNull(product.getFallbackNetPurchasePrice());
        assertNull(product.getFallbackVatRate());
    }

    @Test
    void verifyProducts_doesNotSetPurchasePrice_whenCategoryNamedProduktyButNotSystem() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);

        KsefInvoiceItem item = KsefInvoiceItem.builder()
                .id(100L)
                .ksefItemName("Krem")
                .netValue(new BigDecimal("30.00"))
                .vatRate(VatRate.VAT_23)
                .build();
        invoice.addItem(item);

        Product product = productInCategory(50L, "Produkty", false);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(productRepo.findOneById(50L)).thenReturn(Optional.of(product));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(100L, 50L, 3, false)
        ), false);

        invoiceService.verifyProducts(1L, request);

        assertNull(product.getFallbackNetPurchasePrice());
        assertNull(product.getFallbackVatRate());
        assertEquals(3, product.getSupply());
    }

    @Test
    void verifyProducts_setsPurchasePrice_whenSystemCategoryRenamed() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);

        KsefInvoiceItem item = KsefInvoiceItem.builder()
                .id(100L)
                .ksefItemName("Krem")
                .netValue(new BigDecimal("30.00"))
                .vatRate(VatRate.VAT_23)
                .build();
        invoice.addItem(item);

        Product product = productInCategory(50L, "Towary", true);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(productRepo.findOneById(50L)).thenReturn(Optional.of(product));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(100L, 50L, 3, false)
        ), false);

        invoiceService.verifyProducts(1L, request);

        assertMoney(10.0, product.getFallbackNetPurchasePrice());
        assertEquals(VatRate.VAT_23, product.getFallbackVatRate());
    }

    @Test
    void verifyProducts_fallsBackToUnitNetPrice_whenNetValueNull() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);

        KsefInvoiceItem item = KsefInvoiceItem.builder()
                .id(100L)
                .ksefItemName("Krem")
                .netValue(null)
                .unitNetPrice(new BigDecimal("12.50"))
                .vatRate(VatRate.VAT_8)
                .build();
        invoice.addItem(item);

        Product product = produktyProduct(50L, 0);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(productRepo.findOneById(50L)).thenReturn(Optional.of(product));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(100L, 50L, 4, false)
        ), false);

        invoiceService.verifyProducts(1L, request);

        assertMoney(12.5, product.getFallbackNetPurchasePrice());
        assertEquals(VatRate.VAT_8, product.getFallbackVatRate());
    }

    @Test
    void verifyProducts_doesNotSetPurchasePrice_whenNetValueAndUnitNetPriceNull() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);

        KsefInvoiceItem item = KsefInvoiceItem.builder()
                .id(100L)
                .ksefItemName("Krem")
                .netValue(null)
                .unitNetPrice(null)
                .vatRate(VatRate.VAT_23)
                .build();
        invoice.addItem(item);

        Product product = produktyProduct(50L, 0);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(productRepo.findOneById(50L)).thenReturn(Optional.of(product));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(100L, 50L, 3, false)
        ), false);

        invoiceService.verifyProducts(1L, request);

        assertNull(product.getFallbackNetPurchasePrice());
        assertNull(product.getFallbackVatRate());
    }

    @Test
    void verifyProducts_setsInvoiceCategoryToProducts() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);
        KsefInvoiceItem item = KsefInvoiceItem.builder().id(100L).ksefItemName("X").build();
        invoice.addItem(item);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(100L, null, null, false)
        ), false);

        invoiceService.verifyProducts(1L, request);

        assertEquals(ExpenseCategoryCode.PRODUCTS, invoice.getCategory());
    }

    @Test
    void verifyProducts_setsAutoMarkOnSeller_whenRememberSeller() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);
        invoice.getSeller().setAutoMarkProducts(false);
        KsefInvoiceItem item = KsefInvoiceItem.builder().id(100L).ksefItemName("X").build();
        invoice.addItem(item);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(100L, null, null, false)
        ), true);

        invoiceService.verifyProducts(1L, request);

        verify(sellerService).setDefaultCategory(invoice.getSeller(), ExpenseCategoryCode.PRODUCTS);
    }

    @Test
    void verifyProducts_doesNotSetAutoMarkOnSeller_whenNotRememberSeller() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);
        invoice.getSeller().setAutoMarkProducts(false);
        KsefInvoiceItem item = KsefInvoiceItem.builder().id(100L).ksefItemName("X").build();
        invoice.addItem(item);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(100L, null, null, false)
        ), false);

        invoiceService.verifyProducts(1L, request);

        verify(sellerService, never()).setDefaultCategory(any(KsefSeller.class), any());
    }

    @Test
    void setCategory_setsCategory_andPersists() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(false);
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefInvoiceDTO result = invoiceService.setCategory(1L, "EQUIPMENT", false);

        assertEquals("EQUIPMENT", result.getCategory());
        assertEquals("EQUIPMENT", invoice.getCategory());
        verify(invoiceRepo).save(invoice);
    }

    @Test
    void setCategory_clearsAwaitingProductVerification() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(true);
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefInvoiceDTO result = invoiceService.setCategory(1L, "SERVICES", false);

        assertEquals("SERVICES", result.getCategory());
        assertFalse(result.isAwaitingProductVerification());
        assertFalse(invoice.isAwaitingProductVerification());
    }

    @Test
    void setCategory_remembersDefaultOnSeller_whenRequested() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(false);
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        invoiceService.setCategory(1L, "EQUIPMENT", true);

        verify(sellerService).setDefaultCategory(invoice.getSeller(), "EQUIPMENT");
    }

    @Test
    void setCategory_doesNotRememberDefaultOnSeller_whenNotRequested() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setAwaitingProductVerification(false);
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        invoiceService.setCategory(1L, "EQUIPMENT", false);

        verify(sellerService, never()).setDefaultCategory(any(KsefSeller.class), any());
    }

    @Test
    void setCategory_throwsConflict_whenStatusProducts() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setCategory(ExpenseCategoryCode.PRODUCTS);
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));

        assertThrows(ConflictException.class,
                () -> invoiceService.setCategory(1L, "EQUIPMENT", false));
        verify(invoiceRepo, never()).save(any(KsefInvoice.class));
    }

    @Test
    void setCategory_throwsConflict_whenCategoryIsForbiddenForKsef() {
        assertThrows(ConflictException.class,
                () -> invoiceService.setCategory(1L, ExpenseCategoryCode.RENT, false));
        assertThrows(ConflictException.class,
                () -> invoiceService.setCategory(1L, ExpenseCategoryCode.TAXES, false));
        assertThrows(ConflictException.class,
                () -> invoiceService.setCategory(1L, ExpenseCategoryCode.ZUS, false));
        assertThrows(ConflictException.class,
                () -> invoiceService.setCategory(1L, ExpenseCategoryCode.SALARY, false));
        assertThrows(ConflictException.class,
                () -> invoiceService.setCategory(1L, ExpenseCategoryCode.MIXED, false));
        assertThrows(ConflictException.class,
                () -> invoiceService.setCategory(1L, ExpenseCategoryCode.MIXED, true));
        verify(invoiceRepo, never()).findById(any());
        verify(invoiceRepo, never()).save(any(KsefInvoice.class));
        verify(sellerService, never()).setDefaultCategory(any(KsefSeller.class), any());
    }

    @Test
    void setCategory_throwsConflict_whenCategoryIsProducts_regardlessOfStatus() {
        assertThrows(ConflictException.class,
                () -> invoiceService.setCategory(1L, ExpenseCategoryCode.PRODUCTS, false));
        verify(invoiceRepo, never()).findById(any());
        verify(invoiceRepo, never()).save(any(KsefInvoice.class));
    }

    @Test
    void setCategory_throwsResourceNotFoundException_whenInvoiceMissing() {
        when(invoiceRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> invoiceService.setCategory(99L, "EQUIPMENT", false));
    }

    private KsefInvoice correctionInvoice() {
        return KsefInvoice.builder()
                .id(2L)
                .ksefReferenceNumber("20240116-KOR-001")
                .invoiceNumber("KOR/2024/01/001")
                .invoiceType("KOR")
                .seller(sampleSeller())
                .correctedInvoiceId(1L)
                .correctedKsefNumber("20240115-FA-001")
                .awaitingProductVerification(true)
                .build();
    }

    private KsefInvoiceItem correctionItem(Long id, String name, String quantity) {
        return KsefInvoiceItem.builder()
                .id(id)
                .ksefItemName(name)
                .quantity(quantity != null ? new BigDecimal(quantity) : null)
                .build();
    }

    private KsefBookCorrectionRequestDTO bookRequest(KsefBookCorrectionItemDTO... items) {
        return new KsefBookCorrectionRequestDTO(List.of(items));
    }

    @Test
    void bookCorrection_reducesStock_whenDeltaNegative_assignsProduct_andLocksAsProducts() {
        KsefInvoice correction = correctionInvoice();
        KsefInvoiceItem item = correctionItem(100L, "Krem", "-3");
        correction.addItem(item);
        Product product = sampleProduct(50L, 10);

        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));
        when(productRepo.findAllByIdInForUpdate(any())).thenReturn(List.of(product));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefInvoiceDTO result = invoiceService.bookCorrection(2L,
                bookRequest(new KsefBookCorrectionItemDTO(100L, 50L, 3)));

        assertEquals(ExpenseCategoryCode.PRODUCTS, result.getCategory());
        assertFalse(result.isAwaitingProductVerification());
        assertSame(product, item.getProduct());
        assertEquals(7, product.getSupply());
        verify(invoiceRepo).save(correction);
        verify(productRepo, never()).findOneById(any());
        verify(auditLogService).logUpdate(eq("KsefInvoice (CE)"), eq(2L), eq("KOR/2024/01/001"), any(), any());
    }

    @Test
    void bookCorrection_addsStock_whenDeltaPositive() {
        KsefInvoice correction = correctionInvoice();
        KsefInvoiceItem item = correctionItem(100L, "Krem", "2");
        correction.addItem(item);
        Product product = sampleProduct(50L, 10);

        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));
        when(productRepo.findAllByIdInForUpdate(any())).thenReturn(List.of(product));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        invoiceService.bookCorrection(2L, bookRequest(new KsefBookCorrectionItemDTO(100L, 50L, 4)));

        assertEquals(14, product.getSupply());
        assertSame(product, item.getProduct());
        verify(supplyHistoryService).record(product, 4, SupplyChangeSource.KSEF_CORRECTION,
                correction.getId(), correction.getInvoiceNumber());
    }

    @Test
    void bookCorrection_shouldRecordSupplyHistory_withSignedDelta_whenDeltaNegative() {
        KsefInvoice correction = correctionInvoice();
        correction.addItem(correctionItem(100L, "Krem", "-1"));
        Product product = sampleProduct(50L, 10);

        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));
        when(productRepo.findAllByIdInForUpdate(any())).thenReturn(List.of(product));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        invoiceService.bookCorrection(2L, bookRequest(new KsefBookCorrectionItemDTO(100L, 50L, 3)));

        verify(supplyHistoryService).record(product, -3, SupplyChangeSource.KSEF_CORRECTION,
                correction.getId(), correction.getInvoiceNumber());
    }

    @Test
    void bookCorrection_assignsProduct_withoutStockChange_whenDeltaZeroAndQuantityZero() {
        KsefInvoice correction = correctionInvoice();
        KsefInvoiceItem item = correctionItem(100L, "Krem", "0");
        correction.addItem(item);
        Product product = sampleProduct(50L, 10);

        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));
        when(productRepo.findAllByIdInForUpdate(any())).thenReturn(List.of(product));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        invoiceService.bookCorrection(2L, bookRequest(new KsefBookCorrectionItemDTO(100L, 50L, 0)));

        assertSame(product, item.getProduct());
        assertEquals(10, product.getSupply());
        verifyNoInteractions(supplyHistoryService);
    }

    @Test
    void bookCorrection_assignsProduct_withoutStockChange_whenDeltaNonZeroButQuantityZero() {
        KsefInvoice correction = correctionInvoice();
        KsefInvoiceItem item = correctionItem(100L, "Krem", "-5");
        correction.addItem(item);
        Product product = sampleProduct(50L, 10);

        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));
        when(productRepo.findAllByIdInForUpdate(any())).thenReturn(List.of(product));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        invoiceService.bookCorrection(2L, bookRequest(new KsefBookCorrectionItemDTO(100L, 50L, 0)));

        assertSame(product, item.getProduct());
        assertEquals(10, product.getSupply());
        verifyNoInteractions(supplyHistoryService);
    }

    @Test
    void bookCorrection_throwsConflict_whenDeltaZeroButQuantityNonZero() {
        KsefInvoice correction = correctionInvoice();
        correction.addItem(correctionItem(100L, "Krem", "0.000"));
        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));

        assertThrows(ConflictException.class, () -> invoiceService.bookCorrection(2L,
                bookRequest(new KsefBookCorrectionItemDTO(100L, 50L, 2))));
        verify(productRepo, never()).findAllByIdInForUpdate(any());
        verify(invoiceRepo, never()).save(any(KsefInvoice.class));
    }

    @Test
    void bookCorrection_throwsConflict_whenItemQuantityUnknownAndQuantityNonZero() {
        KsefInvoice correction = correctionInvoice();
        correction.addItem(correctionItem(100L, "Krem", null));
        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));

        assertThrows(ConflictException.class, () -> invoiceService.bookCorrection(2L,
                bookRequest(new KsefBookCorrectionItemDTO(100L, 50L, 1))));
    }

    @Test
    void bookCorrection_appliesNetDeltaOncePerProduct_whenSeveralItemsShareProduct() {
        KsefInvoice correction = correctionInvoice();
        KsefInvoiceItem minus = correctionItem(100L, "Krem", "-5");
        KsefInvoiceItem plus = correctionItem(101L, "Krem gratis", "2");
        correction.addItem(minus);
        correction.addItem(plus);
        Product product = sampleProduct(50L, 4);

        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));
        when(productRepo.findAllByIdInForUpdate(any())).thenReturn(List.of(product));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        invoiceService.bookCorrection(2L, bookRequest(
                new KsefBookCorrectionItemDTO(100L, 50L, 5),
                new KsefBookCorrectionItemDTO(101L, 50L, 2)));

        assertEquals(1, product.getSupply());
        assertSame(product, minus.getProduct());
        assertSame(product, plus.getProduct());
        verify(supplyHistoryService).record(product, -3, SupplyChangeSource.KSEF_CORRECTION,
                correction.getId(), correction.getInvoiceNumber());
        verifyNoMoreInteractions(supplyHistoryService);
    }

    @Test
    void bookCorrection_throwsBlockedWithPreview_andChangesNothing_whenSupplyInsufficient() {
        KsefInvoice correction = correctionInvoice();
        KsefInvoiceItem shortItem = correctionItem(100L, "Krem", "-10");
        KsefInvoiceItem okItem = correctionItem(101L, "Balsam", "-1");
        correction.addItem(shortItem);
        correction.addItem(okItem);
        Product shortProduct = sampleProduct(50L, 4);
        shortProduct.setName("krem A");
        Product okProduct = sampleProduct(51L, 5);
        okProduct.setName("Balsam B");

        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));
        when(productRepo.findAllByIdInForUpdate(any())).thenReturn(List.of(shortProduct, okProduct));

        StockChangeBlockedException ex = assertThrows(StockChangeBlockedException.class,
                () -> invoiceService.bookCorrection(2L, bookRequest(
                        new KsefBookCorrectionItemDTO(100L, 50L, 10),
                        new KsefBookCorrectionItemDTO(101L, 51L, 1))));

        StockChangePreviewDTO preview = ex.getPreview();
        assertFalse(preview.isCanApply());
        assertEquals(List.of("Balsam B", "krem A"),
                preview.getProducts().stream().map(StockChangeProductDTO::getProductName).toList());
        StockChangeProductDTO blocked = preview.getProducts().get(1);
        assertEquals(50L, blocked.getProductId());
        assertEquals(-10, blocked.getQuantity());
        assertEquals(4, blocked.getCurrentSupply());
        assertEquals(-6, blocked.getSupplyAfter());
        assertFalse(blocked.isSufficient());
        assertTrue(preview.getProducts().get(0).isSufficient());

        assertEquals(4, shortProduct.getSupply());
        assertEquals(5, okProduct.getSupply());
        assertNull(shortItem.getProduct());
        assertNull(okItem.getProduct());
        assertTrue(correction.isAwaitingProductVerification());
        assertNull(correction.getCategory());
        verify(invoiceRepo, never()).save(any(KsefInvoice.class));
        verifyNoInteractions(supplyHistoryService, auditLogService);
    }

    @Test
    void bookCorrection_skipsItemsWithoutProductAssignment() {
        KsefInvoice correction = correctionInvoice();
        KsefInvoiceItem item = correctionItem(100L, "Opłata", "-1");
        correction.addItem(item);

        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefInvoiceDTO result = invoiceService.bookCorrection(2L,
                bookRequest(new KsefBookCorrectionItemDTO(100L, null, null)));

        assertEquals(ExpenseCategoryCode.PRODUCTS, result.getCategory());
        assertNull(item.getProduct());
        verify(productRepo, never()).findAllByIdInForUpdate(any());
    }

    @Test
    void bookCorrection_throwsConflict_whenInvoiceIsNotCorrection() {
        KsefInvoice regular = sampleInvoice();
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(regular));

        assertThrows(ConflictException.class,
                () -> invoiceService.bookCorrection(1L, new KsefBookCorrectionRequestDTO(List.of())));
    }

    @Test
    void bookCorrection_throwsConflict_whenCorrectionNotLinkedToOriginal() {
        KsefInvoice correction = correctionInvoice();
        correction.setCorrectedInvoiceId(null);
        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));

        assertThrows(ConflictException.class,
                () -> invoiceService.bookCorrection(2L, new KsefBookCorrectionRequestDTO(List.of())));
    }

    @Test
    void bookCorrection_throwsConflict_whenNotAwaitingBooking() {
        KsefInvoice correction = correctionInvoice();
        correction.setAwaitingProductVerification(false);
        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));

        assertThrows(ConflictException.class,
                () -> invoiceService.bookCorrection(2L, new KsefBookCorrectionRequestDTO(List.of())));
    }

    @Test
    void bookCorrection_throwsConflict_whenItemsEmpty() {
        KsefInvoice correction = correctionInvoice();
        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));

        assertThrows(ConflictException.class,
                () -> invoiceService.bookCorrection(2L, new KsefBookCorrectionRequestDTO(List.of())));
    }

    @Test
    void bookCorrection_throwsConflict_whenItemDoesNotBelongToInvoice() {
        KsefInvoice correction = correctionInvoice();
        correction.addItem(correctionItem(100L, "Krem", "-1"));
        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));

        assertThrows(ConflictException.class, () -> invoiceService.bookCorrection(2L,
                bookRequest(new KsefBookCorrectionItemDTO(999L, 50L, 1))));
    }

    @Test
    void bookCorrection_throwsConflict_whenItemRepeatedInRequest() {
        KsefInvoice correction = correctionInvoice();
        correction.addItem(correctionItem(100L, "Krem", "-1"));
        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));

        assertThrows(ConflictException.class, () -> invoiceService.bookCorrection(2L, bookRequest(
                new KsefBookCorrectionItemDTO(100L, 50L, 1),
                new KsefBookCorrectionItemDTO(100L, 50L, 1))));
    }

    @Test
    void bookCorrection_throwsConflict_whenQuantityNegative() {
        KsefInvoice correction = correctionInvoice();
        correction.addItem(correctionItem(100L, "Krem", "-1"));
        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));

        assertThrows(ConflictException.class, () -> invoiceService.bookCorrection(2L,
                bookRequest(new KsefBookCorrectionItemDTO(100L, 50L, -1))));
    }

    @Test
    void bookCorrection_throwsResourceNotFound_whenProductMissing() {
        KsefInvoice correction = correctionInvoice();
        correction.addItem(correctionItem(100L, "Krem", "-1"));
        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));
        when(productRepo.findAllByIdInForUpdate(any())).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> invoiceService.bookCorrection(2L,
                bookRequest(new KsefBookCorrectionItemDTO(100L, 50L, 1))));
    }

    @Test
    void bookCorrection_throwsConflict_whenAssigningDeletedProduct() {
        KsefInvoice correction = correctionInvoice();
        correction.addItem(correctionItem(100L, "Krem", "-1"));
        Product deleted = sampleProduct(50L, 5);
        deleted.setIsDeleted(true);
        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));
        when(productRepo.findAllByIdInForUpdate(any())).thenReturn(List.of(deleted));

        assertThrows(ConflictException.class, () -> invoiceService.bookCorrection(2L,
                bookRequest(new KsefBookCorrectionItemDTO(100L, 50L, 1))));
        assertEquals(5, deleted.getSupply());
    }

    @Test
    void bookCorrection_throwsResourceNotFound_whenInvoiceMissing() {
        when(invoiceRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> invoiceService.bookCorrection(99L, new KsefBookCorrectionRequestDTO(List.of())));
    }

    @Test
    void previewCorrectionBooking_returnsSignedAggregatedQuantities_sortedByName_withoutChangingAnything() {
        KsefInvoice correction = correctionInvoice();
        KsefInvoiceItem a1 = correctionItem(100L, "Krem", "-5");
        KsefInvoiceItem a2 = correctionItem(101L, "Krem", "-2");
        KsefInvoiceItem b = correctionItem(102L, "Balsam", "3");
        correction.addItem(a1);
        correction.addItem(a2);
        correction.addItem(b);
        Product krem = sampleProduct(50L, 4);
        krem.setName("Krem A");
        Product balsam = sampleProduct(51L, 0);
        balsam.setName("balsam");

        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));
        when(productRepo.findAllById(any())).thenReturn(List.of(krem, balsam));

        StockChangePreviewDTO preview = invoiceService.previewCorrectionBooking(2L, bookRequest(
                new KsefBookCorrectionItemDTO(100L, 50L, 5),
                new KsefBookCorrectionItemDTO(101L, 50L, 2),
                new KsefBookCorrectionItemDTO(102L, 51L, 3)));

        assertFalse(preview.isCanApply());
        assertEquals(2, preview.getProducts().size());
        StockChangeProductDTO first = preview.getProducts().get(0);
        assertEquals("balsam", first.getProductName());
        assertEquals(3, first.getQuantity());
        assertEquals(3, first.getSupplyAfter());
        assertTrue(first.isSufficient());
        StockChangeProductDTO second = preview.getProducts().get(1);
        assertEquals("Krem A", second.getProductName());
        assertEquals(-7, second.getQuantity());
        assertEquals(4, second.getCurrentSupply());
        assertEquals(-3, second.getSupplyAfter());
        assertFalse(second.isSufficient());
        assertFalse(second.isSkipped());

        assertEquals(4, krem.getSupply());
        assertNull(a1.getProduct());
        verify(productRepo, never()).findAllByIdInForUpdate(any());
        verify(invoiceRepo, never()).save(any(KsefInvoice.class));
        verifyNoInteractions(supplyHistoryService, auditLogService);
    }

    @Test
    void previewCorrectionBooking_canApply_whenAllSufficient() {
        KsefInvoice correction = correctionInvoice();
        correction.addItem(correctionItem(100L, "Krem", "-4"));
        Product krem = sampleProduct(50L, 4);

        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));
        when(productRepo.findAllById(any())).thenReturn(List.of(krem));

        StockChangePreviewDTO preview = invoiceService.previewCorrectionBooking(2L,
                bookRequest(new KsefBookCorrectionItemDTO(100L, 50L, 4)));

        assertTrue(preview.isCanApply());
        assertEquals(0, preview.getProducts().getFirst().getSupplyAfter());
    }

    @Test
    void previewCorrectionBooking_appliesSameGuards_asBooking() {
        KsefInvoice correction = correctionInvoice();
        correction.setAwaitingProductVerification(false);
        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));

        assertThrows(ConflictException.class, () -> invoiceService.previewCorrectionBooking(2L,
                bookRequest(new KsefBookCorrectionItemDTO(100L, 50L, 1))));
    }

    @Test
    void previewCorrectionBooking_throwsConflict_whenProductDeleted() {
        KsefInvoice correction = correctionInvoice();
        correction.addItem(correctionItem(100L, "Krem", "-1"));
        Product deleted = sampleProduct(50L, 5);
        deleted.setIsDeleted(true);
        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));
        when(productRepo.findAllById(any())).thenReturn(List.of(deleted));

        assertThrows(ConflictException.class, () -> invoiceService.previewCorrectionBooking(2L,
                bookRequest(new KsefBookCorrectionItemDTO(100L, 50L, 1))));
    }

    @Test
    void getInvoiceById_attachesCorrectionMatch_forLinkedCorrection() {
        Product product = sampleProduct(50L, 10);
        KsefInvoice parent = sampleInvoice();
        parent.addItem(KsefInvoiceItem.builder().id(10L).ksefItemName("Krem").lineNumber(1)
                .quantity(new BigDecimal("2")).declaredQuantity(20).product(product).build());
        parent.addItem(KsefInvoiceItem.builder().id(11L).ksefItemName("Dostawa").lineNumber(2)
                .quantity(BigDecimal.ONE).build());

        KsefInvoice correction = correctionInvoice();
        correction.setSeller(null);
        correction.addItem(KsefInvoiceItem.builder().id(100L).ksefItemName("Krem").lineNumber(1)
                .quantity(new BigDecimal("-1")).build());
        correction.addItem(KsefInvoiceItem.builder().id(101L).ksefItemName("Dostawa").lineNumber(2)
                .quantity(new BigDecimal("-1")).build());
        correction.addItem(KsefInvoiceItem.builder().id(102L).ksefItemName("Nowa pozycja")
                .quantity(BigDecimal.ONE).build());

        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(parent));

        KsefInvoiceDTO result = invoiceService.getInvoiceById(2L);

        KsefCorrectionMatchDTO krem = result.getItems().get(0).getCorrectionMatch();
        assertEquals(KsefCorrectionMatchType.LINE, krem.getMatchType());
        assertEquals(10L, krem.getOriginalItemId());
        assertEquals(50L, krem.getSuggestedProductId());
        assertEquals(-10, krem.getSuggestedQuantity());
        assertFalse(krem.isNotProduct());
        assertEquals(1, result.getItems().get(0).getLineNumber());

        KsefCorrectionMatchDTO dostawa = result.getItems().get(1).getCorrectionMatch();
        assertTrue(dostawa.isNotProduct());
        assertNull(dostawa.getSuggestedProductId());
        assertEquals(0, dostawa.getSuggestedQuantity());

        KsefCorrectionMatchDTO none = result.getItems().get(2).getCorrectionMatch();
        assertEquals(KsefCorrectionMatchType.NONE, none.getMatchType());
        assertNull(none.getOriginalItemId());
        assertNull(none.getSuggestedQuantity());
    }

    @Test
    void getInvoiceById_leavesCorrectionMatchNull_forRegularInvoice() {
        KsefInvoice invoice = sampleInvoice();
        invoice.setSeller(null);
        invoice.addItem(KsefInvoiceItem.builder().id(10L).ksefItemName("Krem").build());
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));

        KsefInvoiceDTO result = invoiceService.getInvoiceById(1L);

        assertNull(result.getItems().getFirst().getCorrectionMatch());
    }

    @Test
    void toggleProductInvoice_throwsConflict_whenInvoiceIsCorrection() {
        KsefInvoice correction = correctionInvoice();
        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));

        assertThrows(ConflictException.class, () -> invoiceService.toggleProductInvoice(2L));
        verify(invoiceRepo, never()).save(any(KsefInvoice.class));
    }

    @Test
    void verifyProducts_throwsConflict_whenInvoiceIsCorrection() {
        KsefInvoice correction = correctionInvoice();
        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));

        assertThrows(ConflictException.class,
                () -> invoiceService.verifyProducts(2L, new KsefVerifyProductsRequestDTO()));
    }

    @Test
    void setCategory_throwsConflict_whenInvoiceIsCorrection() {
        KsefInvoice correction = correctionInvoice();
        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));

        assertThrows(ConflictException.class,
                () -> invoiceService.setCategory(2L, "SERVICES", false));
        verify(invoiceRepo, never()).save(any(KsefInvoice.class));
    }

    @Test
    void setCategory_propagatesInheritedCategoryToLinkedCorrections() {
        KsefInvoice parent = sampleInvoice();
        parent.setAwaitingProductVerification(false);
        KsefInvoice correction = correctionInvoice();
        correction.setAwaitingProductVerification(true);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(parent));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepo.findByCorrectedInvoiceId(1L)).thenReturn(List.of(correction));

        invoiceService.setCategory(1L, "SERVICES", false);

        assertEquals("SERVICES", correction.getCategory());
        assertFalse(correction.isAwaitingProductVerification());
        verify(invoiceRepo).save(correction);
    }

    @Test
    void verifyProducts_propagatesProductsState_toLinkedCorrections() {
        KsefInvoice parent = sampleInvoice();
        parent.setAwaitingProductVerification(true);
        KsefInvoiceItem parentItem = KsefInvoiceItem.builder()
                .id(100L).ksefItemName("Krem").ksefGtin("5901234567890").build();
        parent.addItem(parentItem);
        Product product = sampleProduct(50L, 0);

        KsefInvoice correction = correctionInvoice();
        KsefInvoiceItem correctionItem = KsefInvoiceItem.builder()
                .id(200L).ksefItemName("Krem").ksefGtin("5901234567890").build();
        correction.addItem(correctionItem);

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(parent));
        when(productRepo.findOneById(50L)).thenReturn(Optional.of(product));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepo.findByCorrectedInvoiceId(1L)).thenReturn(List.of(correction));

        KsefVerifyProductsRequestDTO request = new KsefVerifyProductsRequestDTO(List.of(
                new KsefVerifyProductsItemDTO(100L, 50L, 1, false)
        ), false);

        invoiceService.verifyProducts(1L, request);

        assertTrue(correction.isAwaitingProductVerification());
        assertNull(correction.getCategory());
        assertSame(product, correctionItem.getProduct());
        verify(invoiceRepo).save(correction);
    }

    @Test
    void getInvoiceById_attachesCorrections_forOriginalVatInvoice() {
        KsefInvoice original = sampleInvoice();
        original.setSeller(null);
        KsefInvoice correction = correctionInvoice();

        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(original));
        when(invoiceRepo.findCorrectionsByInvoiceId(1L)).thenReturn(List.of(correction));

        KsefInvoiceDTO result = invoiceService.getInvoiceById(1L);

        assertEquals(1, result.getCorrections().size());
        assertEquals("20240116-KOR-001", result.getCorrections().getFirst().getKsefNumber());
    }

    @Test
    void getInvoiceById_doesNotQueryCorrections_forCorrectionInvoice() {
        KsefInvoice correction = correctionInvoice();
        correction.setSeller(null);
        when(invoiceRepo.findById(2L)).thenReturn(Optional.of(correction));

        KsefInvoiceDTO result = invoiceService.getInvoiceById(2L);

        assertTrue(result.getCorrections().isEmpty());
        verify(invoiceRepo, never()).findCorrectionsByInvoiceId(any());
    }
    @Test
    void setCategory_throwsValidation_whenCategoryUnknown() {
        doThrow(new ValidationException("Nieznana kategoria wydatku.")).when(expenseCategoryService).requireExistingCode("XYZ");

        ValidationException ex = assertThrows(ValidationException.class,
                () -> invoiceService.setCategory(1L, "XYZ", true));

        assertEquals("Nieznana kategoria wydatku.", ex.getMessage());
        verify(invoiceRepo, never()).findById(any());
        verify(invoiceRepo, never()).save(any(KsefInvoice.class));
        verify(sellerService, never()).setDefaultCategory(any(KsefSeller.class), any());
    }

    @Test
    void setCategory_acceptsCustomCategoryCode() {
        String customCode = "C0123456789abcdef0123456789abcdef";
        KsefInvoice invoice = sampleInvoice();
        when(invoiceRepo.findById(1L)).thenReturn(Optional.of(invoice));
        when(invoiceRepo.save(any(KsefInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

        KsefInvoiceDTO result = invoiceService.setCategory(1L, customCode, false);

        assertEquals(customCode, result.getCategory());
        assertEquals(customCode, invoice.getCategory());
        verify(expenseCategoryService).requireExistingCode(customCode);
    }
}

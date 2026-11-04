package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.SaleDTO;
import com.clinic.clinicmanager.DTO.SaleItemDTO;
import com.clinic.clinicmanager.DTO.VoucherDTO;
import com.clinic.clinicmanager.DTO.VisitDTO;
import com.clinic.clinicmanager.DTO.ProductDTO;
import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import com.clinic.clinicmanager.model.constants.VoucherStatus;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.repo.ProductCategoryRepo;
import com.clinic.clinicmanager.repo.SaleItemRepo;
import com.clinic.clinicmanager.repo.VisitRepo;
import com.clinic.clinicmanager.repo.VoucherRepo;
import com.clinic.clinicmanager.service.impl.ProductCategoryServiceImpl;
import com.clinic.clinicmanager.service.impl.VisitPricingCalculator;
import com.clinic.clinicmanager.service.impl.VisitSaleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VisitSaleServiceTest {

    private static void assertMoney(double expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    @Mock ProductRepo productRepo;
    @Mock VoucherRepo voucherRepo;
    @Mock SaleItemRepo saleItemRepo;
    @Mock VisitRepo visitRepo;
    @Mock VisitPricingCalculator pricingCalculator;
    @Mock SupplyHistoryService supplyHistoryService;
    @Mock AuditLogService auditLogService;
    @Mock ProductCategoryRepo productCategoryRepo;
    @Mock ProductCategoryService productCategoryService;
    @InjectMocks VisitSaleService visitSaleService;


    private Visit buildVisitWithSaleItems(List<SaleItem> items) {
        Sale sale = Sale.builder().items(new ArrayList<>(items)).build();
        return Visit.builder().sale(sale).build();
    }

    private SaleItem saleItemWithVoucher(Long voucherId, VoucherStatus status) {
        Voucher v = Voucher.builder().id(voucherId).status(status).build();
        return SaleItem.builder().voucher(v).build();
    }

    private SaleItem saleItemWithProduct(Long productId) {
        Product p = Product.builder().id(productId).build();
        return SaleItem.builder().product(p).build();
    }

    private VisitDTO visitDTOWithSale(List<SaleItemDTO> items) {
        SaleDTO sale = new SaleDTO();
        sale.setItems(new ArrayList<>(items));
        VisitDTO visit = new VisitDTO();
        visit.setSale(sale);
        visit.setDate(LocalDate.now());
        visit.setClient(new ClientDTO());
        return visit;
    }

    private SaleItemDTO saleItemDTOWithProduct(Long productId, Double price) {
        ProductDTO productDTO = new ProductDTO();
        productDTO.setId(productId);
        SaleItemDTO item = new SaleItemDTO();
        item.setProduct(productDTO);
        item.setPrice(price == null ? null : BigDecimal.valueOf(price));
        return item;
    }

    private SaleItemDTO saleItemDTOWithVoucher(Double price, String name) {
        VoucherDTO voucher = new VoucherDTO();
        SaleItemDTO item = new SaleItemDTO();
        item.setVoucher(voucher);
        item.setPrice(price == null ? null : BigDecimal.valueOf(price));
        item.setName(name);
        return item;
    }

    private AppSettings defaultSettings() {
        return AppSettings.builder().voucherExpiryTime(3).build();
    }

    // checkSaleItemVouchers
    @Test
    void checkSaleItemVouchers_emptyItems_doesNothing() {
        Visit visit = buildVisitWithSaleItems(List.of());

        visitSaleService.checkSaleItemVouchers(visit);

        verifyNoInteractions(visitRepo);
    }
    @Test
    void checkSaleItemVouchers_itemWithNoVoucher_doesNothing() {
        SaleItem item = SaleItem.builder().build();
        Visit visit = buildVisitWithSaleItems(List.of(item));

        visitSaleService.checkSaleItemVouchers(visit);

        verifyNoInteractions(visitRepo);
    }
    @Test
    void checkSaleItemVouchers_itemVoucherNotUsed_doesNothing() {
        Visit visit = buildVisitWithSaleItems(List.of(saleItemWithVoucher(1L, VoucherStatus.ACTIVE)));

        visitSaleService.checkSaleItemVouchers(visit);

        verifyNoInteractions(visitRepo);
    }
    @Test
    void checkSaleItemVouchers_usedVoucher_visitFound_throwsIllegalStateException() {
        Visit visit = buildVisitWithSaleItems(List.of(saleItemWithVoucher(1L, VoucherStatus.USED)));
        Visit payingVisit = Visit.builder().id(99L).build();
        when(visitRepo.findByVoucherId(1L)).thenReturn(Optional.of(payingVisit));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> visitSaleService.checkSaleItemVouchers(visit));
        assertTrue(ex.getMessage().contains("99"));
    }
    @Test
    void checkSaleItemVouchers_usedVoucher_visitNotFound_throwsResourceNotFoundException() {
        Visit visit = buildVisitWithSaleItems(List.of(saleItemWithVoucher(1L, VoucherStatus.USED)));
        when(visitRepo.findByVoucherId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> visitSaleService.checkSaleItemVouchers(visit));
    }
    // processSale
    @Test
    void processSale_nullSale_returnsEarly() {
        VisitDTO visit = new VisitDTO();
        visit.setSale(null);

        visitSaleService.processSale(visit, defaultSettings(), false);

        verifyNoInteractions(productRepo, voucherRepo, pricingCalculator);
    }
    @Test
    void processSale_emptySaleItems_callsCalculateSaleTotals() {
        VisitDTO visit = visitDTOWithSale(List.of());

        visitSaleService.processSale(visit, defaultSettings(), false);

        verify(pricingCalculator).calculateSaleTotals(visit.getSale());
        verify(pricingCalculator, never()).calculateSaleItemTotals(any());
    }
    @Test
    void processSale_preview_skipsProductProcessing_callsCalculator() {
        SaleItemDTO item = saleItemDTOWithProduct(1L, 50.0);
        VisitDTO visit = visitDTOWithSale(List.of(item));

        visitSaleService.processSale(visit, defaultSettings(), true);

        verifyNoInteractions(productRepo);
        verify(pricingCalculator).calculateSaleItemTotals(item);
        verify(pricingCalculator).calculateSaleTotals(visit.getSale());
    }
    @Test
    void processSale_notPreview_productNotFound_throwsResourceNotFoundException() {
        SaleItemDTO item = saleItemDTOWithProduct(1L, 50.0);
        VisitDTO visit = visitDTOWithSale(List.of(item));
        when(productRepo.findOneById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> visitSaleService.processSale(visit, defaultSettings(), false));
    }
    @Test
    void processSale_notPreview_itemPriceNull_productHasSellingPrice_setsItemPrice() {
        SaleItemDTO item = saleItemDTOWithProduct(1L, null);
        VisitDTO visit = visitDTOWithSale(List.of(item));
        Product product = Product.builder().id(1L).sellingPrice(BigDecimal.valueOf(100.0)).supply(5).name("X").build();
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));

        visitSaleService.processSale(visit, defaultSettings(), false);

        assertMoney(100.0, item.getPrice());
    }
    @Test
    void processSale_notPreview_itemPriceNull_productNoSellingPrice_throwsIllegalStateException() {
        SaleItemDTO item = saleItemDTOWithProduct(1L, null);
        VisitDTO visit = visitDTOWithSale(List.of(item));
        Product product = Product.builder().id(1L).sellingPrice(null).supply(5).name("X").build();
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));

        assertThrows(IllegalStateException.class,
                () -> visitSaleService.processSale(visit, defaultSettings(), false));
    }
    @Test
    void processSale_notPreview_productOutOfStock_throwsIllegalStateException() {
        SaleItemDTO item = saleItemDTOWithProduct(1L, 50.0);
        VisitDTO visit = visitDTOWithSale(List.of(item));
        Product product = Product.builder().id(1L).supply(0).name("X").build();
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));

        assertThrows(IllegalStateException.class,
                () -> visitSaleService.processSale(visit, defaultSettings(), false));
    }
    @Test
    void processSale_notPreview_productInStock_decrementsSupplyAndSaves() {
        SaleItemDTO item = saleItemDTOWithProduct(1L, 50.0);
        VisitDTO visit = visitDTOWithSale(List.of(item));
        Product product = Product.builder().id(1L).supply(3).name("X").build();
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));

        visitSaleService.processSale(visit, defaultSettings(), false);

        assertEquals(2, product.getSupply());
        verify(productRepo).save(product);
    }
    @Test
    void processSale_notPreview_productInStock_recordsSupplyHistory_withVisitSaleSource() {
        SaleItemDTO item = saleItemDTOWithProduct(1L, 50.0);
        VisitDTO visit = visitDTOWithSale(List.of(item));
        visit.setId(42L);
        Product product = Product.builder().id(1L).supply(3).name("X").build();
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));

        visitSaleService.processSale(visit, defaultSettings(), false);

        verify(supplyHistoryService).record(product, -1, SupplyChangeSource.VISIT_SALE, 42L, "Wizyta #42");
    }
    @Test
    void processSale_notPreview_voucherItem_savesVoucherAndSetsId() {
        SaleItemDTO item = saleItemDTOWithVoucher(80.0, "Voucher");
        VisitDTO visit = visitDTOWithSale(List.of(item));
        Voucher saved = Voucher.builder().id(99L).build();
        when(voucherRepo.save(any())).thenReturn(saved);

        visitSaleService.processSale(visit, defaultSettings(), false);

        verify(voucherRepo).save(any());
        assertEquals(99L, item.getVoucher().getId());
    }
    @Test
    void processSale_notPreview_voucherItem_nameNull_setsNameToVoucher() {
        SaleItemDTO item = saleItemDTOWithVoucher(80.0, null);
        VisitDTO visit = visitDTOWithSale(List.of(item));
        when(voucherRepo.save(any())).thenReturn(Voucher.builder().id(1L).build());

        visitSaleService.processSale(visit, defaultSettings(), false);

        assertEquals("Voucher", item.getName());
    }
    @Test
    void processSale_preview_skipsVoucherProcessing_callsCalculator() {
        SaleItemDTO item = saleItemDTOWithVoucher(80.0, "V");
        VisitDTO visit = visitDTOWithSale(List.of(item));

        visitSaleService.processSale(visit, defaultSettings(), true);

        verifyNoInteractions(voucherRepo);
        verify(pricingCalculator).calculateSaleItemTotals(item);
        verify(pricingCalculator).calculateSaleTotals(visit.getSale());
    }
    @Test
    void processSale_calculateSaleItemTotals_calledForEachItem() {
        SaleItemDTO item1 = saleItemDTOWithProduct(1L, 30.0);
        SaleItemDTO item2 = saleItemDTOWithProduct(2L, 50.0);
        VisitDTO visit = visitDTOWithSale(List.of(item1, item2));
        Product p1 = Product.builder().id(1L).supply(5).name("A").build();
        Product p2 = Product.builder().id(2L).supply(5).name("B").build();
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(p1));
        when(productRepo.findOneById(2L)).thenReturn(Optional.of(p2));

        visitSaleService.processSale(visit, defaultSettings(), false);

        verify(pricingCalculator).calculateSaleItemTotals(item1);
        verify(pricingCalculator).calculateSaleItemTotals(item2);
        verify(pricingCalculator).calculateSaleTotals(visit.getSale());
    }

    // undoSale
    @Test
    void undoSale_nullSale_returnsEarly() {
        Visit visit = Visit.builder().sale(null).build();

        visitSaleService.undoSale(visit);

        verifyNoInteractions(productRepo, voucherRepo, visitRepo);
    }
    @Test
    void undoSale_emptySaleItems_setsVisitSaleToNull() {
        Visit visit = buildVisitWithSaleItems(List.of());

        visitSaleService.undoSale(visit);

        assertNull(visit.getSale());
    }
    @Test
    void undoSale_itemWithVoucher_voucherNotFound_throwsResourceNotFoundException() {
        SaleItem item = saleItemWithVoucher(1L, VoucherStatus.ACTIVE);
        Visit visit = buildVisitWithSaleItems(new ArrayList<>(List.of(item)));
        when(voucherRepo.findOneById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> visitSaleService.undoSale(visit));
    }
    @Test
    void undoSale_itemWithUsedVoucher_checkThrows_propagatesIllegalStateException() {
        SaleItem item = saleItemWithVoucher(1L, VoucherStatus.USED);
        Visit visit = buildVisitWithSaleItems(new ArrayList<>(List.of(item)));
        Voucher voucher = Voucher.builder().id(1L).status(VoucherStatus.USED).build();
        when(voucherRepo.findOneById(1L)).thenReturn(Optional.of(voucher));
        Visit payingVisit = Visit.builder().id(77L).build();
        when(visitRepo.findByVoucherId(1L)).thenReturn(Optional.of(payingVisit));

        assertThrows(IllegalStateException.class,
                () -> visitSaleService.undoSale(visit));
    }
    @Test
    void undoSale_itemWithActiveVoucher_deletesVoucher() {
        SaleItem item = saleItemWithVoucher(1L, VoucherStatus.ACTIVE);
        Visit visit = buildVisitWithSaleItems(new ArrayList<>(List.of(item)));
        Voucher voucher = Voucher.builder().id(1L).status(VoucherStatus.ACTIVE).build();
        when(voucherRepo.findOneById(1L)).thenReturn(Optional.of(voucher));

        visitSaleService.undoSale(visit);

        verify(voucherRepo).delete(voucher);
    }
    @Test
    void undoSale_itemWithProduct_productNotFound_throwsResourceNotFoundException() {
        SaleItem item = saleItemWithProduct(1L);
        Visit visit = buildVisitWithSaleItems(new ArrayList<>(List.of(item)));
        when(productRepo.findOneById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> visitSaleService.undoSale(visit));
    }
    @Test
    void undoSale_itemWithProduct_productDeleted_restoresProduct() {
        Product product = Product.builder().id(1L).isDeleted(true).supply(0).build();
        SaleItem item = SaleItem.builder().product(product).build();
        Visit visit = buildVisitWithSaleItems(new ArrayList<>(List.of(item)));
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));

        visitSaleService.undoSale(visit);

        assertFalse(product.getIsDeleted());
        assertEquals(1, product.getSupply());
        verify(productRepo).save(product);
    }
    @Test
    void undoSale_itemWithProduct_productNotDeleted_incrementsSupplyAndSaves() {
        Product product = Product.builder().id(1L).isDeleted(false).supply(2).build();
        SaleItem item = SaleItem.builder().product(product).build();
        Visit visit = buildVisitWithSaleItems(new ArrayList<>(List.of(item)));
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));

        visitSaleService.undoSale(visit);

        assertEquals(3, product.getSupply());
        verify(productRepo).save(product);
    }
    @Test
    void undoSale_itemWithProduct_recordsSupplyHistory_withVisitDeletedSource() {
        Product product = Product.builder().id(1L).isDeleted(false).supply(2).build();
        SaleItem item = SaleItem.builder().product(product).build();
        Sale sale = Sale.builder().items(new ArrayList<>(List.of(item))).build();
        Visit visit = Visit.builder().id(55L).sale(sale).build();
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));

        visitSaleService.undoSale(visit);

        verify(supplyHistoryService).record(product, 1, SupplyChangeSource.VISIT_DELETED, 55L, "Wizyta #55");
    }
    @Test
    void undoSale_afterProcessing_setsVisitSaleToNull() {
        SaleItem item = saleItemWithProduct(1L);
        Product product = Product.builder().id(1L).isDeleted(false).supply(1).build();
        Visit visit = buildVisitWithSaleItems(new ArrayList<>(List.of(item)));
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));

        visitSaleService.undoSale(visit);

        assertNull(visit.getSale());
    }

    private VisitSaleService visitSaleServiceWithRealCategoryService() {
        ProductCategoryServiceImpl categoryService = new ProductCategoryServiceImpl(productCategoryRepo, productRepo, auditLogService);
        return new VisitSaleService(productRepo, voucherRepo, pricingCalculator, visitRepo, supplyHistoryService, categoryService);
    }

    private Product archivedProductIn(ProductCategory category) {
        return Product.builder().id(1L).name("Krem").isDeleted(true).supply(0).category(category).build();
    }

    @Test
    void undoSale_archivedProductInHiddenCategory_restoresProductAndCategoryWithAudit() {
        ProductCategory hidden = ProductCategory.builder().id(7L).name("Jednorazowe").color("10,20,30").isDeleted(true).build();
        Product product = archivedProductIn(hidden);
        Visit visit = buildVisitWithSaleItems(new ArrayList<>(List.of(SaleItem.builder().product(product).build())));
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));
        when(productCategoryRepo.existsByColorAndIsDeletedFalse("10,20,30")).thenReturn(false);
        when(productCategoryRepo.findByIdAndIsDeletedTrue(7L)).thenReturn(Optional.of(hidden));
        when(productCategoryRepo.saveAndFlush(hidden)).thenReturn(hidden);

        visitSaleServiceWithRealCategoryService().undoSale(visit);

        assertFalse(product.getIsDeleted());
        assertEquals(1, product.getSupply());
        assertFalse(hidden.getIsDeleted());
        assertEquals("10,20,30", hidden.getColor());
        verify(productCategoryRepo).acquireTransactionLock(anyLong());
        verify(productCategoryRepo).saveAndFlush(hidden);
        verify(auditLogService).logUpdate(eq("ProductCategory"), eq(7L), eq("Jednorazowe"), any(), any());
    }

    @Test
    void undoSale_archivedProductInHiddenCategoryWithTakenColor_restoresCategoryWithFreeColor() {
        ProductCategory hidden = ProductCategory.builder().id(7L).name("Jednorazowe").color("10,20,30").isDeleted(true).build();
        Product product = archivedProductIn(hidden);
        Visit visit = buildVisitWithSaleItems(new ArrayList<>(List.of(SaleItem.builder().product(product).build())));
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));
        when(productCategoryRepo.existsByColorAndIsDeletedFalse("10,20,30")).thenReturn(true);
        when(productCategoryRepo.findActiveColors()).thenReturn(List.of("10,20,30"));
        when(productCategoryRepo.findByIdAndIsDeletedTrue(7L)).thenReturn(Optional.of(hidden));
        when(productCategoryRepo.saveAndFlush(hidden)).thenReturn(hidden);

        assertDoesNotThrow(() -> visitSaleServiceWithRealCategoryService().undoSale(visit));

        assertFalse(product.getIsDeleted());
        assertFalse(hidden.getIsDeleted());
        assertEquals("239,68,68", hidden.getColor());
        verify(auditLogService).logUpdate(eq("ProductCategory"), eq(7L), eq("Jednorazowe"), any(), any());
    }

    @Test
    void undoSale_archivedProductInActiveCategory_leavesCategoryUnchanged() {
        ProductCategory active = ProductCategory.builder().id(8L).name("Kremy").color("1,1,1").build();
        Product product = archivedProductIn(active);
        Visit visit = buildVisitWithSaleItems(new ArrayList<>(List.of(SaleItem.builder().product(product).build())));
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));

        visitSaleServiceWithRealCategoryService().undoSale(visit);

        assertFalse(product.getIsDeleted());
        assertFalse(active.getIsDeleted());
        assertEquals("1,1,1", active.getColor());
        verifyNoInteractions(productCategoryRepo, auditLogService);
    }
}

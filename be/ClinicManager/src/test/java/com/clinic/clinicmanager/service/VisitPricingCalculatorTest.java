package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.*;
import com.clinic.clinicmanager.model.AppSettings;
import com.clinic.clinicmanager.model.constants.PaymentStatus;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.service.impl.VisitPricingCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VisitPricingCalculatorTest {

    private final VisitPricingCalculator calculator = new VisitPricingCalculator();

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    private static void assertMoney(double expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    @Test
    void roundPrice_halfUp() {
        assertMoney(1.01, calculator.roundPrice(bd(1.005)));
    }
    @Test
    void roundPrice_halfDown() {
        assertMoney(1.00, calculator.roundPrice(bd(1.004)));
    }
    @Test
    void roundPrice_exactValue() {
        assertMoney(2.56, calculator.roundPrice(bd(2.555)));
    }

    @Test
    void calculateSaleItemTotals_withProduct_vatFromProduct() {
        ProductDTO productDTO = new ProductDTO();
        productDTO.setVatRate(VatRate.VAT_23);

        SaleItemDTO item = new SaleItemDTO();
        item.setProduct(productDTO);
        item.setPrice(bd(123.0));

        calculator.calculateSaleItemTotals(item);

        assertMoney(100.0, item.getNetValue());
        assertMoney(23.0, item.getVatValue());
    }
    @Test
    void calculateSaleItemTotals_withVoucher_usesVat8() {
        SaleItemDTO item = new SaleItemDTO();
        item.setVoucher(new VoucherDTO());
        item.setPrice(bd(108.0));

        calculator.calculateSaleItemTotals(item);

        assertMoney(100.0, item.getNetValue());
        assertMoney(8.0, item.getVatValue());
    }
    @Test
    void calculateSaleItemTotals_withNeither_usesVat23() {
        SaleItemDTO item = new SaleItemDTO();
        item.setPrice(bd(123.0));

        calculator.calculateSaleItemTotals(item);

        assertMoney(100.0, item.getNetValue());
        assertMoney(23.0, item.getVatValue());
    }

    @Test
    void calculateSaleTotals_sumsItemTotals() {
        SaleItemDTO item1 = new SaleItemDTO();
        item1.setPrice(bd(100.0)); item1.setNetValue(bd(81.30)); item1.setVatValue(bd(18.70));

        SaleItemDTO item2 = new SaleItemDTO();
        item2.setPrice(bd(50.0)); item2.setNetValue(bd(40.65)); item2.setVatValue(bd(9.35));

        SaleDTO sale = new SaleDTO();
        sale.setItems(List.of(item1, item2));

        calculator.calculateSaleTotals(sale);

        assertMoney(150.0, sale.getTotalValue());
        assertMoney(121.95, sale.getTotalNet());
        assertMoney(28.05, sale.getTotalVat());
    }
    @Test
    void calculateSaleTotals_emptyItems_returnsZeros() {
        SaleDTO sale = new SaleDTO();
        sale.setItems(new ArrayList<>());

        calculator.calculateSaleTotals(sale);

        assertMoney(0.0, sale.getTotalValue());
        assertMoney(0.0, sale.getTotalNet());
        assertMoney(0.0, sale.getTotalVat());
    }

    @Test
    void calculateVisitItemsTotalValue_noFlags_sumsFinalPrices() {
        VisitItemDTO item1 = new VisitItemDTO(); item1.setFinalPrice(bd(100.0));
        VisitItemDTO item2 = new VisitItemDTO(); item2.setFinalPrice(bd(50.0));

        VisitDTO visit = new VisitDTO();
        visit.setIsVip(false); visit.setAbsence(false);
        visit.setItems(List.of(item1, item2));

        AppSettings settings = AppSettings.builder().build();

        BigDecimal result = calculator.calculateVisitItemsTotalValue(visit, settings, false);

        assertMoney(150.0, result);
    }
    @Test
    void calculateVisitItemsTotalValue_vip_appliesVipRate() {
        VisitItemDTO item = new VisitItemDTO(); item.setFinalPrice(bd(100.0));

        VisitDTO visit = new VisitDTO();
        visit.setIsVip(true); visit.setAbsence(false);
        visit.setItems(List.of(item));

        AppSettings settings = AppSettings.builder().visitVipRate(120).build();

        BigDecimal result = calculator.calculateVisitItemsTotalValue(visit, settings, false);

        assertMoney(120.0, result);
    }
    @Test
    void calculateVisitItemsTotalValue_previewAbsence_appliesAbsenceRate() {
        VisitItemDTO item = new VisitItemDTO(); item.setFinalPrice(bd(100.0));

        VisitDTO visit = new VisitDTO();
        visit.setIsVip(false); visit.setAbsence(true);
        visit.setItems(List.of(item));

        AppSettings settings = AppSettings.builder().visitAbsenceRate(80).build();

        BigDecimal result = calculator.calculateVisitItemsTotalValue(visit, settings, true);

        assertMoney(80.0, result);
    }
    @Test
    void calculateVisitItemsTotalValue_notPreviewAbsence_doesNotApplyAbsenceRate() {
        VisitItemDTO item = new VisitItemDTO(); item.setFinalPrice(bd(100.0));

        VisitDTO visit = new VisitDTO();
        visit.setIsVip(false); visit.setAbsence(true);
        visit.setItems(List.of(item));

        AppSettings settings = AppSettings.builder().visitAbsenceRate(80).build();

        BigDecimal result = calculator.calculateVisitItemsTotalValue(visit, settings, false);

        assertMoney(100.0, result);
    }

    @Test
    void calculateVisitTotals_itemsOnly_setsCorrectTotals() {
        VisitItemDTO item = new VisitItemDTO(); item.setFinalPrice(bd(108.0));

        VisitDTO visit = new VisitDTO();
        visit.setIsVip(false); visit.setAbsence(false);
        visit.setItems(List.of(item));
        visit.setDebtRedemptions(new ArrayList<>());
        visit.setSale(null);

        AppSettings settings = AppSettings.builder().build();

        calculator.calculateVisitTotals(visit, settings, false);

        assertMoney(108.0, visit.getTotalValue());
        assertMoney(100.0, visit.getTotalNet());
        assertMoney(8.0, visit.getTotalVat());
    }
    @Test
    void calculateVisitTotals_withSale_addsSaleTotals() {
        VisitItemDTO item = new VisitItemDTO(); item.setFinalPrice(bd(108.0));

        SaleDTO sale = new SaleDTO();
        sale.setTotalValue(bd(50.0)); sale.setTotalNet(bd(40.65)); sale.setTotalVat(bd(9.35));
        sale.setItems(new ArrayList<>());

        VisitDTO visit = new VisitDTO();
        visit.setIsVip(false); visit.setAbsence(false);
        visit.setItems(List.of(item));
        visit.setDebtRedemptions(new ArrayList<>());
        visit.setSale(sale);

        calculator.calculateVisitTotals(visit, AppSettings.builder().build(), false);

        assertMoney(158.0, visit.getTotalValue());
    }

    @Test
    void previewPaymentStatus_fullyPaid_setsPAID() {
        PaymentDTO p = new PaymentDTO(); p.setAmount(bd(100.0));
        VisitDTO visit = new VisitDTO();
        visit.setTotalValue(bd(100.0));
        visit.setPayments(List.of(p));

        calculator.previewPaymentStatus(visit);

        assertEquals(PaymentStatus.PAID, visit.getPaymentStatus());
    }
    @Test
    void previewPaymentStatus_partiallyPaid_setsPARTIAL() {
        PaymentDTO p = new PaymentDTO(); p.setAmount(bd(50.0));
        VisitDTO visit = new VisitDTO();
        visit.setTotalValue(bd(100.0));
        visit.setPayments(List.of(p));

        calculator.previewPaymentStatus(visit);

        assertEquals(PaymentStatus.PARTIAL, visit.getPaymentStatus());
    }
    @Test
    void previewPaymentStatus_notPaid_setsUNPAID() {
        VisitDTO visit = new VisitDTO();
        visit.setTotalValue(bd(100.0));
        visit.setPayments(new ArrayList<>());

        calculator.previewPaymentStatus(visit);

        assertEquals(PaymentStatus.UNPAID, visit.getPaymentStatus());
    }
}

package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.*;
import com.clinic.clinicmanager.model.AppSettings;
import com.clinic.clinicmanager.model.constants.PaymentStatus;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.utils.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

import static java.util.Objects.nonNull;

@Component
@RequiredArgsConstructor
public class VisitPricingCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public BigDecimal roundPrice(BigDecimal price) {
        return MoneyUtils.money(price);
    }

    public void calculateSaleItemTotals(SaleItemDTO saleItem) {
        BigDecimal itemTotalPrice = MoneyUtils.money(saleItem.getPrice());
        VatRate vatRate = saleItem.getProduct() != null ? saleItem.getProduct().getVatRate()
                : saleItem.getVoucher() != null ? VatRate.VAT_8
                : VatRate.VAT_23;

        BigDecimal vatVal = MoneyUtils.vatFromGross(itemTotalPrice, vatRate);
        BigDecimal netVal = itemTotalPrice.subtract(vatVal);

        saleItem.setNetValue(MoneyUtils.money(netVal));
        saleItem.setVatValue(MoneyUtils.money(vatVal));
    }

    public void calculateSaleTotals(SaleDTO sale) {
        BigDecimal netVal = BigDecimal.ZERO;
        BigDecimal vatVal = BigDecimal.ZERO;
        BigDecimal total = BigDecimal.ZERO;
        for (SaleItemDTO saleItem : sale.getItems()) {
            netVal = netVal.add(MoneyUtils.nz(saleItem.getNetValue()));
            vatVal = vatVal.add(MoneyUtils.nz(saleItem.getVatValue()));
            total = total.add(MoneyUtils.nz(saleItem.getPrice()));
        }
        sale.setTotalNet(MoneyUtils.money(netVal));
        sale.setTotalVat(MoneyUtils.money(vatVal));
        sale.setTotalValue(MoneyUtils.money(total));
    }

    public BigDecimal calculateVisitItemsTotalValue(VisitDTO visit, AppSettings settings, boolean preview) {
        BigDecimal totalValue = BigDecimal.ZERO;
        for (VisitItemDTO item : visit.getItems()) {
            if (visit.getIsVip()) {
                item.setFinalPrice(applyPercent(item.getFinalPrice(), settings.getVisitVipRate()));
            }
            if (preview && visit.getAbsence()) {
                item.setFinalPrice(applyPercent(item.getFinalPrice(), settings.getVisitAbsenceRate()));
            }
            totalValue = totalValue.add(MoneyUtils.nz(item.getFinalPrice()));
        }
        return MoneyUtils.money(totalValue);
    }

    public void calculateVisitTotals(VisitDTO visit, AppSettings settings, boolean preview) {
        BigDecimal visitItemsTotalValue = calculateVisitItemsTotalValue(visit, settings, preview);
        BigDecimal visitItemsTotalVat = MoneyUtils.vatFromGross(visitItemsTotalValue, VatRate.VAT_8);
        BigDecimal visitItemsTotalNet = visitItemsTotalValue.subtract(visitItemsTotalVat);

        BigDecimal debtRedemptionsTotalValue = BigDecimal.ZERO;
        BigDecimal debtRedemptionsTotalVat = BigDecimal.ZERO;
        BigDecimal debtRedemptionsTotalNet = BigDecimal.ZERO;
        if (!visit.getDebtRedemptions().isEmpty()) {
            for (DebtRedemptionDTO redemption : visit.getDebtRedemptions()) {
                debtRedemptionsTotalValue = debtRedemptionsTotalValue.add(
                        MoneyUtils.nz(redemption.getDebtSource().getValue()));
                BigDecimal vatPart = MoneyUtils.vatFromGross(debtRedemptionsTotalValue, VatRate.VAT_8);
                debtRedemptionsTotalNet = debtRedemptionsTotalNet.add(debtRedemptionsTotalValue.subtract(vatPart));
                debtRedemptionsTotalVat = debtRedemptionsTotalVat.add(vatPart);
            }
        }

        BigDecimal saleTotalValue = nonNull(visit.getSale()) ? MoneyUtils.nz(visit.getSale().getTotalValue()) : BigDecimal.ZERO;
        BigDecimal saleTotalVat = nonNull(visit.getSale()) ? MoneyUtils.nz(visit.getSale().getTotalVat()) : BigDecimal.ZERO;
        BigDecimal saleTotalNet = nonNull(visit.getSale()) ? MoneyUtils.nz(visit.getSale().getTotalNet()) : BigDecimal.ZERO;

        BigDecimal totalValue = visitItemsTotalValue.add(saleTotalValue).add(debtRedemptionsTotalValue);
        BigDecimal totalVat = visitItemsTotalVat.add(saleTotalVat).add(debtRedemptionsTotalVat);
        BigDecimal totalNet = visitItemsTotalNet.add(saleTotalNet).add(debtRedemptionsTotalNet);

        visit.setTotalValue(MoneyUtils.money(totalValue));
        visit.setTotalNet(MoneyUtils.money(totalNet));
        visit.setTotalVat(MoneyUtils.money(totalVat));
    }

    public void previewPaymentStatus(VisitDTO visit) {
        BigDecimal visitTotalValue = MoneyUtils.nz(visit.getTotalValue());
        BigDecimal clientPaid = visit.getPayments().stream()
                .map(PaymentDTO::getAmount)
                .map(MoneyUtils::nz)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (clientPaid.compareTo(visitTotalValue) >= 0) {
            visit.setPaymentStatus(PaymentStatus.PAID);
        } else if (clientPaid.signum() > 0 && clientPaid.compareTo(visitTotalValue) < 0) {
            visit.setPaymentStatus(PaymentStatus.PARTIAL);
        } else {
            visit.setPaymentStatus(PaymentStatus.UNPAID);
        }
    }

    private BigDecimal applyPercent(BigDecimal base, Number percent) {
        return MoneyUtils.money(
                MoneyUtils.nz(base)
                        .multiply(BigDecimal.valueOf(percent.doubleValue()))
                        .divide(HUNDRED, 6, MoneyUtils.ROUNDING));
    }
}

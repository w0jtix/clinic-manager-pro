package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.PaymentDTO;
import com.clinic.clinicmanager.DTO.VisitDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.DebtType;
import com.clinic.clinicmanager.model.constants.PaymentMethod;
import com.clinic.clinicmanager.model.constants.PaymentStatus;
import com.clinic.clinicmanager.model.constants.VoucherStatus;
import com.clinic.clinicmanager.repo.ClientDebtRepo;
import com.clinic.clinicmanager.repo.PaymentRepo;
import com.clinic.clinicmanager.repo.VisitRepo;
import com.clinic.clinicmanager.repo.VoucherRepo;
import com.clinic.clinicmanager.utils.MoneyUtils;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class VisitPaymentService {

    private final VoucherRepo voucherRepo;
    private final ClientDebtRepo clientDebtRepo;
    private final VisitRepo visitRepo;
    private final PaymentRepo paymentRepo;
    private final VisitPricingCalculator pricingCalculator;

    public void processPayments(VisitDTO visit) {
        if (visit.getPayments() == null || visit.getPayments().isEmpty()) {
            return;
        }

        Set<Long> usedVoucherIds = new HashSet<>();
        for (PaymentDTO payment : visit.getPayments()) {
            if (payment.getVoucher() != null) {
                if(usedVoucherIds.contains(payment.getVoucher().getId())) {
                    throw new IllegalStateException("Using same Voucher twice as Payment method is forbidden! VoucherId: " + payment.getVoucher().getId());
                }
                Voucher voucher = voucherRepo.findOneById(payment.getVoucher().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Voucher not found with id: " + payment.getVoucher().getId()));
                if (voucher.getStatus() != VoucherStatus.ACTIVE || !voucher.getExpiryDate().isAfter(LocalDate.now())) {
                    throw new IllegalStateException("Voucher is expired or already used. VoucherId: " +
                            voucher.getId());
                }
                voucher.setStatus(VoucherStatus.USED);
                voucherRepo.save(voucher);
                payment.setAmount(voucher.getValue());
                payment.setMethod(PaymentMethod.VOUCHER);
                usedVoucherIds.add(voucher.getId());
            }
        }
    }
    public void processPaymentStatus(Visit visit, AppSettings settings) {
        BigDecimal visitTotalValue = MoneyUtils.nz(visit.getTotalValue());
        BigDecimal clientPaid = visit.getPayments().stream()
                .map(Payment::getAmount)
                .map(MoneyUtils::nz)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (clientPaid.compareTo(visitTotalValue) >= 0) {
            visit.setPaymentStatus(PaymentStatus.PAID);
        } else if (clientPaid.signum() > 0 && clientPaid.compareTo(visitTotalValue) < 0) {
            visit.setPaymentStatus(PaymentStatus.PARTIAL);
            ClientDebt clientDebt = ClientDebt.builder()
                    .client(visit.getClient())
                    .sourceVisit(visit)
                    .type(DebtType.PARTIAL_PAYMENT)
                    .value(pricingCalculator.roundPrice(visitTotalValue.subtract(clientPaid)))
                    .createdAt(LocalDate.from(visit.getDate()))
                    .createdByUserId(SessionUtils.getUserIdFromSession())
                    .build();

            clientDebtRepo.save(clientDebt);
        } else {
            visit.setPaymentStatus(PaymentStatus.UNPAID);
            BigDecimal debtValue = visit.getAbsence()
                    ? MoneyUtils.money(
                            pricingCalculator.calculateVisitItemsTotalValue(new VisitDTO(visit), settings, false)
                                    .multiply(BigDecimal.valueOf(settings.getVisitAbsenceRate()))
                                    .divide(BigDecimal.valueOf(100), 6, MoneyUtils.ROUNDING))
                    : visitTotalValue;
            ClientDebt clientDebt = ClientDebt.builder()
                    .client(visit.getClient())
                    .sourceVisit(visit)
                    .type(visit.getAbsence() ? DebtType.ABSENCE_FEE : DebtType.UNPAID)
                    .value(debtValue)
                    .createdAt(LocalDate.from(visit.getDate()))
                    .createdByUserId(SessionUtils.getUserIdFromSession())
                    .build();

            clientDebtRepo.save(clientDebt);
        }
        visitRepo.save(visit);
    }

    public void undoVoucherPayment(Visit visit) {
        for(Payment payment : visit.getPayments()){
            if(payment.getVoucher() != null) {
                Voucher voucher = voucherRepo.findOneById(payment.getVoucher().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Voucher (Payment method) not found with id: " + payment.getVoucher().getId()));
                if(voucher.getExpiryDate().isAfter(LocalDate.now())){
                    voucher.setStatus(VoucherStatus.ACTIVE);
                } else {
                    voucher.setStatus(VoucherStatus.EXPIRED);
                }
                voucherRepo.save(voucher);
            }
        }
    }

    public void removePayments(Visit visit) {
        if(visit.getPayments().isEmpty()) return;
        for (Iterator<Payment> it = visit.getPayments().iterator(); it.hasNext();) {
            Payment payment = it.next();
            paymentRepo.delete(payment);
            it.remove();
        }
    }
    public boolean isFullyPaid(Visit visit) {
        BigDecimal totalPaid = visit.getPayments().stream()
                .map(Payment::getAmount)
                .map(MoneyUtils::nz)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalPaid.compareTo(MoneyUtils.nz(visit.getTotalValue())) >= 0;
    }
}

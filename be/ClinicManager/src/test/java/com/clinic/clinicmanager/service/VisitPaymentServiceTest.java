package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.PaymentDTO;
import com.clinic.clinicmanager.DTO.VisitDTO;
import com.clinic.clinicmanager.DTO.VoucherDTO;
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
import com.clinic.clinicmanager.service.impl.VisitPaymentService;
import com.clinic.clinicmanager.service.impl.VisitPricingCalculator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VisitPaymentServiceTest {

    @Mock VoucherRepo voucherRepo;
    @Mock ClientDebtRepo clientDebtRepo;
    @Mock VisitRepo visitRepo;
    @Mock PaymentRepo paymentRepo;
    @Mock VisitPricingCalculator pricingCalculator;
    @InjectMocks VisitPaymentService paymentService;

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }

    private static void assertMoney(double expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }


    private PaymentDTO voucherPaymentDTO(Long voucherId) {
        VoucherDTO v = new VoucherDTO();
        v.setId(voucherId);
        PaymentDTO p = new PaymentDTO();
        p.setVoucher(v);
        return p;
    }
    private Voucher activeVoucher(Long id, Double value, LocalDate expiryDate) {
        return Voucher.builder().id(id).status(VoucherStatus.ACTIVE).value(BigDecimal.valueOf(value)).expiryDate(expiryDate).build();
    }
    private Visit buildVisit(List<Payment> payments, Double totalValue, boolean absence) {
        return Visit.builder()
                .id(1L)
                .client(Client.builder().id(1L).build())
                .employee(Employee.builder().id(1L).build())
                .payments(payments)
                .totalValue(BigDecimal.valueOf(totalValue))
                .absence(absence)
                .date(LocalDate.now())
                .build();
    }
    private AppSettings defaultSettings() {
        return AppSettings.builder().visitAbsenceRate(80).build();
    }

    // processPayments
    @Test
    void processPayments_nullPayments_returnsEarly() {
        VisitDTO visit = new VisitDTO();
        visit.setPayments(null);

        paymentService.processPayments(visit);

        verifyNoInteractions(voucherRepo);
    }
    @Test
    void processPayments_emptyPayments_returnsEarly() {
        VisitDTO visit = new VisitDTO();
        visit.setPayments(new ArrayList<>());

        paymentService.processPayments(visit);

        verifyNoInteractions(voucherRepo);
    }
    @Test
    void processPayments_paymentWithNoVoucher_doesNothing() {
        PaymentDTO payment = new PaymentDTO();
        payment.setVoucher(null);
        VisitDTO visit = new VisitDTO();
        visit.setPayments(List.of(payment));

        paymentService.processPayments(visit);

        verifyNoInteractions(voucherRepo);
    }
    @Test
    void processPayments_sameVoucherUsedTwice_throwsIllegalStateException() {
        PaymentDTO p1 = voucherPaymentDTO(1L);
        PaymentDTO p2 = voucherPaymentDTO(1L);
        Voucher voucher = activeVoucher(1L, 50.0, LocalDate.now().plusMonths(1));
        when(voucherRepo.findOneById(1L)).thenReturn(Optional.of(voucher));

        VisitDTO visit = new VisitDTO();
        visit.setPayments(List.of(p1, p2));

        assertThrows(IllegalStateException.class,
                () -> paymentService.processPayments(visit));
    }
    @Test
    void processPayments_voucherNotFound_throwsResourceNotFoundException() {
        PaymentDTO payment = voucherPaymentDTO(1L);
        when(voucherRepo.findOneById(1L)).thenReturn(Optional.empty());

        VisitDTO visit = new VisitDTO();
        visit.setPayments(List.of(payment));

        assertThrows(ResourceNotFoundException.class,
                () -> paymentService.processPayments(visit));
    }
    @Test
    void processPayments_voucherExpired_throwsIllegalStateException() {
        PaymentDTO payment = voucherPaymentDTO(1L);
        Voucher voucher = activeVoucher(1L, 50.0, LocalDate.now().minusDays(1));
        when(voucherRepo.findOneById(1L)).thenReturn(Optional.of(voucher));

        VisitDTO visit = new VisitDTO();
        visit.setPayments(new ArrayList<>(List.of(payment)));

        assertThrows(IllegalStateException.class,
                () -> paymentService.processPayments(visit));
    }
    @Test
    void processPayments_voucherAlreadyUsed_throwsIllegalStateException() {
        PaymentDTO payment = voucherPaymentDTO(1L);
        Voucher voucher = Voucher.builder().id(1L).status(VoucherStatus.USED).expiryDate(LocalDate.now().plusMonths(1)).build();
        when(voucherRepo.findOneById(1L)).thenReturn(Optional.of(voucher));

        VisitDTO visit = new VisitDTO();
        visit.setPayments(new ArrayList<>(List.of(payment)));

        assertThrows(IllegalStateException.class,
                () -> paymentService.processPayments(visit));
    }
    @Test
    void processPayments_activeVoucher_marksUsedSetsAmountAndMethod() {
        PaymentDTO payment = voucherPaymentDTO(1L);
        Voucher voucher = activeVoucher(1L, 75.0, LocalDate.now().plusMonths(1));
        when(voucherRepo.findOneById(1L)).thenReturn(Optional.of(voucher));

        VisitDTO visit = new VisitDTO();
        visit.setPayments(new ArrayList<>(List.of(payment)));

        paymentService.processPayments(visit);

        assertEquals(VoucherStatus.USED, voucher.getStatus());
        verify(voucherRepo).save(voucher);
        assertMoney(75.0, payment.getAmount());
        assertEquals(PaymentMethod.VOUCHER, payment.getMethod());
    }
    // processPaymentStatus
    @Test
    void processPaymentStatus_fullyPaid_setsPaidStatus() {
        Payment payment = Payment.builder().amount(bd(100.0)).build();
        Visit visit = buildVisit(List.of(payment), 100.0, false);

        paymentService.processPaymentStatus(visit, defaultSettings());

        assertEquals(PaymentStatus.PAID, visit.getPaymentStatus());
        verify(clientDebtRepo, never()).save(any());
        verify(visitRepo).save(visit);
    }
    @Test
    void processPaymentStatus_partiallyPaid_setsPartialStatusAndCreatesDebt() {
        Payment payment = Payment.builder().amount(bd(40.0)).build();
        Visit visit = buildVisit(List.of(payment), 100.0, false);
        when(pricingCalculator.roundPrice(bd(60.0))).thenReturn(bd(60.0));

        paymentService.processPaymentStatus(visit, defaultSettings());

        assertEquals(PaymentStatus.PARTIAL, visit.getPaymentStatus());
        verify(clientDebtRepo).save(argThat(debt ->
                debt.getType() == DebtType.PARTIAL_PAYMENT && debt.getValue().compareTo(bd(60.0)) == 0
        ));
        verify(visitRepo).save(visit);
    }
    @Test
    void processPaymentStatus_unpaid_noAbsence_createsUnpaidDebt() {
        Visit visit = buildVisit(List.of(), 100.0, false);

        paymentService.processPaymentStatus(visit, defaultSettings());

        assertEquals(PaymentStatus.UNPAID, visit.getPaymentStatus());
        verify(clientDebtRepo).save(argThat(debt ->
                debt.getType() == DebtType.UNPAID && debt.getValue().compareTo(bd(100.0)) == 0
        ));
        verify(visitRepo).save(visit);
    }
    @Test
    void processPaymentStatus_unpaid_withAbsence_createsAbsenceFeeDebt() {
        Visit visit = buildVisit(List.of(), 100.0, true);
        when(pricingCalculator.calculateVisitItemsTotalValue(any(), any(), eq(false))).thenReturn(bd(200.0));

        paymentService.processPaymentStatus(visit, defaultSettings());

        assertEquals(PaymentStatus.UNPAID, visit.getPaymentStatus());
        verify(clientDebtRepo).save(argThat(debt ->
                debt.getType() == DebtType.ABSENCE_FEE && debt.getValue().compareTo(bd(160.0)) == 0
        ));
    }
    @Test

    void processPaymentStatus_alwaysSavesVisit() {
        Visit visit = buildVisit(List.of(), 100.0, false);

        paymentService.processPaymentStatus(visit, defaultSettings());

        verify(visitRepo).save(visit);
    }
    // undoVoucherPayment
    @Test
    void undoVoucherPayment_noPayments_doesNothing() {
        Visit visit = buildVisit(List.of(), 0.0, false);

        paymentService.undoVoucherPayment(visit);

        verifyNoInteractions(voucherRepo);
    }
    @Test
    void undoVoucherPayment_paymentWithNoVoucher_doesNothing() {
        Payment payment = Payment.builder().amount(bd(50.0)).build();
        Visit visit = buildVisit(List.of(payment), 50.0, false);

        paymentService.undoVoucherPayment(visit);

        verifyNoInteractions(voucherRepo);
    }
    @Test
    void undoVoucherPayment_voucherNotFound_throwsResourceNotFoundException() {
        Voucher v = Voucher.builder().id(1L).build();
        Payment payment = Payment.builder().voucher(v).build();
        Visit visit = buildVisit(List.of(payment), 50.0, false);
        when(voucherRepo.findOneById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> paymentService.undoVoucherPayment(visit));
    }
    @Test
    void undoVoucherPayment_voucherNotExpired_setsActive() {
        Voucher v = Voucher.builder().id(1L).status(VoucherStatus.USED).expiryDate(LocalDate.now().plusMonths(1)).build();
        Payment payment = Payment.builder().voucher(v).build();
        Visit visit = buildVisit(List.of(payment), 50.0, false);
        when(voucherRepo.findOneById(1L)).thenReturn(Optional.of(v));

        paymentService.undoVoucherPayment(visit);

        assertEquals(VoucherStatus.ACTIVE, v.getStatus());
        verify(voucherRepo).save(v);
    }
    @Test
    void undoVoucherPayment_voucherExpired_setsExpired() {
        Voucher v = Voucher.builder().id(1L).status(VoucherStatus.USED).expiryDate(LocalDate.now().minusDays(1)).build();
        Payment payment = Payment.builder().voucher(v).build();
        Visit visit = buildVisit(List.of(payment), 50.0, false);
        when(voucherRepo.findOneById(1L)).thenReturn(Optional.of(v));

        paymentService.undoVoucherPayment(visit);

        assertEquals(VoucherStatus.EXPIRED, v.getStatus());
        verify(voucherRepo).save(v);
    }
    // removePayments
    @Test
    void removePayments_emptyPayments_doesNothing() {
        Visit visit = buildVisit(List.of(), 0.0, false);

        paymentService.removePayments(visit);

        verifyNoInteractions(paymentRepo);
    }
    @Test
    void removePayments_hasPayments_deletesEachAndClearsList() {
        Payment p1 = Payment.builder().id(1L).build();
        Payment p2 = Payment.builder().id(2L).build();
        Visit visit = buildVisit(new ArrayList<>(List.of(p1, p2)), 100.0, false);

        paymentService.removePayments(visit);

        verify(paymentRepo).delete(p1);
        verify(paymentRepo).delete(p2);
        assertTrue(visit.getPayments().isEmpty());
    }
}

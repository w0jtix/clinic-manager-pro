package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.DebtRedemptionDTO;
import com.clinic.clinicmanager.DTO.VisitDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.PaymentStatus;
import com.clinic.clinicmanager.repo.ClientDebtRepo;
import com.clinic.clinicmanager.repo.VisitRepo;
import com.clinic.clinicmanager.utils.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VisitDebtService {

    private final VisitRepo visitRepo;
    private final ClientDebtRepo clientDebtRepo;

    public void processDebtRedemptions(VisitDTO visit, Client client, boolean preview) {
        if (visit.getDebtRedemptions() == null || visit.getDebtRedemptions().isEmpty()) return;
        for (DebtRedemptionDTO redemption : visit.getDebtRedemptions()) {
            ClientDebt clientDebt = clientDebtRepo.findOneById(redemption.getDebtSource().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("ClientDebt not found with id: " + redemption.getDebtSource().getId()));

            if (!Objects.equals(clientDebt.getClient().getId(), client.getId()))
                throw new IllegalStateException("ClientDebt doesn't belong to provided Client.");

            if (clientDebt.getSourceVisit() != null) {
                Visit sourceVisit = visitRepo.findOneById(clientDebt.getSourceVisit().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Visit not found with id: " + clientDebt.getSourceVisit().getId()));
                if(!preview) {
                    sourceVisit.setPaymentStatus(PaymentStatus.PAID);
                    visitRepo.save(sourceVisit);
                }
            }
            if(!preview) {
                clientDebt.setPaymentStatus(PaymentStatus.PAID);
                clientDebtRepo.save(clientDebt);
            }
        }
    }
    public void undoClientDebtRedemption(Visit visit) {

        List<DebtRedemption> redemptions = visit.getDebtRedemptions();
        for(DebtRedemption redemption : redemptions) {
            ClientDebt clientDebt = clientDebtRepo.findOneById(redemption.getDebtSource().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Redeemed ClientDebt not found with id: " + redemption.getDebtSource().getId()));

            if (!Objects.equals(clientDebt.getClient().getId(), visit.getClient().getId()))
                throw new IllegalStateException("Redeemed ClientDebt doesn't belong to provided Client.");

            if (clientDebt.getSourceVisit() != null) {
                Visit sourceVisit = visitRepo.findOneById(clientDebt.getSourceVisit().getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Visit not found with id: " + clientDebt.getSourceVisit().getId()));
                sourceVisit.setPaymentStatus(isPartiallyPaid(sourceVisit) ? PaymentStatus.PARTIAL : PaymentStatus.UNPAID);
                visitRepo.save(sourceVisit);
            }
            clientDebt.setPaymentStatus(PaymentStatus.UNPAID);
            clientDebtRepo.save(clientDebt);
        }
    }
    public void checkIfDebtIsPaid(Visit visit) {

        Optional<Visit> visitWithPaidDebt = visitRepo.findByDebtSourceVisitId(visit.getId());

        if(visitWithPaidDebt.isPresent()) {
            ClientDebt debt = clientDebtRepo.findOneBySourceVisitId(visit.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Debt paid, but was not found in database. SourceVisitId: " + visit.getId()));

            throw new IllegalStateException(
                    "Failed to delete Visit, Reason: Debt(" + debt.getType() + ") from this Visit was redeemed during Visit with Id: "
                            + visitWithPaidDebt.get().getId()
            );
        }
    }
    public void undoAbsenceFeeOrPartialPaymentFee(Visit visit) {
        ClientDebt clientDebt = clientDebtRepo.findOneBySourceVisitId(visit.getId())
                .orElseThrow(() -> new ResourceNotFoundException("ClientDebt not found with sourceVisitId: " + visit.getId()));

        clientDebtRepo.delete(clientDebt);
    }

    private boolean isPartiallyPaid(Visit visit) {
        BigDecimal totalPaid = visit.getPayments().stream()
                .map(Payment::getAmount)
                .map(MoneyUtils::nz)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = MoneyUtils.nz(visit.getTotalValue());
        return totalPaid.signum() > 0 && totalPaid.compareTo(total) < 0;
    }
}

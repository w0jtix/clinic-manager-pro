package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ClientDebtDTO;
import com.clinic.clinicmanager.DTO.DebtRedemptionDTO;
import com.clinic.clinicmanager.DTO.VisitDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.PaymentStatus;
import com.clinic.clinicmanager.repo.ClientDebtRepo;
import com.clinic.clinicmanager.repo.VisitRepo;
import com.clinic.clinicmanager.service.impl.VisitDebtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VisitDebtServiceTest {

    @Mock ClientDebtRepo debtRepo;
    @Mock VisitRepo visitRepo;
    @InjectMocks VisitDebtService debtService;
    private Client client;

    @BeforeEach
    void setUp() {
        client = Client.builder().id(1L).build();
    }

    private DebtRedemptionDTO buildRedemption(Long debtId) {
        ClientDebtDTO debtSource = new ClientDebtDTO();
        debtSource.setId(debtId);
        DebtRedemptionDTO redemption = new DebtRedemptionDTO();
        redemption.setDebtSource(debtSource);
        return redemption;
    }

    private DebtRedemption buildRedemptionEntity(Long debtId) {
        ClientDebt debtSource = ClientDebt.builder().id(debtId).build();
        return DebtRedemption.builder().debtSource(debtSource).build();
    }

    //processDebtRedemptions
    @Test
    void emptyRedemptions_doesNothing() {
        VisitDTO visit = new VisitDTO();
        visit.setDebtRedemptions(new ArrayList<>());

        debtService.processDebtRedemptions(visit, client, false);

        verifyNoInteractions(debtRepo, visitRepo);
    }
    @Test
    void debtNotFound_throws() {
        DebtRedemptionDTO debtRedemptionDTO = buildRedemption(1L);

        VisitDTO visit = new VisitDTO();
        visit.setDebtRedemptions(new ArrayList<>(List.of(debtRedemptionDTO)));

        when(debtRepo.findOneById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> debtService.processDebtRedemptions(visit,client,false));
    }
    @Test
    void clientMismatch_throwsIllegalStateException() {
        DebtRedemptionDTO debtRedemptionDTO = buildRedemption(1L);
        VisitDTO visit = new VisitDTO();
        visit.setDebtRedemptions(new ArrayList<>(List.of(debtRedemptionDTO)));

        Client client2 = new Client();
        client2.setId(2L);

        ClientDebt clientDebt = new ClientDebt();
        clientDebt.setClient(client2);

        when(debtRepo.findOneById(1L)).thenReturn(Optional.of(clientDebt));

        assertThrows(IllegalStateException.class,
                () -> debtService.processDebtRedemptions(visit,client,false));
    }
    @Test
    void noSourceVisit_doesNotCallVisitRepo() {
        DebtRedemptionDTO debtRedemptionDTO = buildRedemption(1L);
        VisitDTO visit = new VisitDTO();
        visit.setDebtRedemptions(new ArrayList<>(List.of(debtRedemptionDTO)));

        ClientDebt clientDebt = new ClientDebt();
        clientDebt.setClient(client);
        clientDebt.setSourceVisit(null);

        when(debtRepo.findOneById(1L)).thenReturn(Optional.of(clientDebt));

        debtService.processDebtRedemptions(visit,client,false);

        verify(debtRepo).save(clientDebt);
        verify(visitRepo, never()).findOneById(any());
    }
    @Test
    void hasSourceVisitAndPreview_neitherSaved() {
        DebtRedemptionDTO debtRedemptionDTO = buildRedemption(1L);
        VisitDTO visit = new VisitDTO();
        visit.setDebtRedemptions(new ArrayList<>(List.of(debtRedemptionDTO)));

        Visit sourceVisit = new Visit();
        sourceVisit.setId(2L);

        when(visitRepo.findOneById(2L)).thenReturn(Optional.of(sourceVisit));

        ClientDebt clientDebt = new ClientDebt();
        clientDebt.setClient(client);
        clientDebt.setSourceVisit(sourceVisit);

        when(debtRepo.findOneById(1L)).thenReturn(Optional.of(clientDebt));

        debtService.processDebtRedemptions(visit,client,true);

        verify(visitRepo, never()).save(any());
        verify(debtRepo, never()).save(any());
    }
    @Test
    void sourceVisitNotFound_throws() {
        DebtRedemptionDTO debtRedemptionDTO = buildRedemption(1L);
        VisitDTO visit = new VisitDTO();
        visit.setDebtRedemptions(new ArrayList<>(List.of(debtRedemptionDTO)));

        Visit sourceVisit = new Visit();
        sourceVisit.setId(2L);

        when(visitRepo.findOneById(2L)).thenReturn(Optional.empty());

        ClientDebt clientDebt = new ClientDebt();
        clientDebt.setClient(client);
        clientDebt.setSourceVisit(sourceVisit);

        when(debtRepo.findOneById(1L)).thenReturn(Optional.of(clientDebt));

        assertThrows(ResourceNotFoundException.class,
                () -> debtService.processDebtRedemptions(visit,client,false));
    }
    @Test
    void hasSourceVisitAndNotPreview_setsPaymentStatusAndSavesSourceVisit() {
        DebtRedemptionDTO debtRedemptionDTO = buildRedemption(1L);
        VisitDTO visit = new VisitDTO();
        visit.setDebtRedemptions(new ArrayList<>(List.of(debtRedemptionDTO)));

        Visit sourceVisit = new Visit();
        sourceVisit.setId(2L);

        when(visitRepo.findOneById(2L)).thenReturn(Optional.of(sourceVisit));

        ClientDebt clientDebt = new ClientDebt();
        clientDebt.setClient(client);
        clientDebt.setSourceVisit(sourceVisit);

        when(debtRepo.findOneById(1L)).thenReturn(Optional.of(clientDebt));

        debtService.processDebtRedemptions(visit, client, false);

        assertEquals(PaymentStatus.PAID, sourceVisit.getPaymentStatus());
        verify(visitRepo).save(sourceVisit);
    }
    @Test
    void notPreview_savesClientDebt() {
        DebtRedemptionDTO debtRedemptionDTO = buildRedemption(1L);
        VisitDTO visit = new VisitDTO();
        visit.setDebtRedemptions(new ArrayList<>(List.of(debtRedemptionDTO)));

        Visit sourceVisit = new Visit();
        sourceVisit.setId(2L);

        when(visitRepo.findOneById(2L)).thenReturn(Optional.of(sourceVisit));

        ClientDebt clientDebt = new ClientDebt();
        clientDebt.setClient(client);
        clientDebt.setSourceVisit(sourceVisit);

        when(debtRepo.findOneById(1L)).thenReturn(Optional.of(clientDebt));

        debtService.processDebtRedemptions(visit,client,false);

        assertEquals(PaymentStatus.PAID, clientDebt.getPaymentStatus());
        verify(debtRepo ).save(any());
    }
    //undoClientDebtRedemption
    @Test
    void undo_func_debtNotFound_throws() {
        DebtRedemption redemption = buildRedemptionEntity(1L);
        Visit visit = Visit.builder().client(client).debtRedemptions(List.of(redemption)).build();

        when(debtRepo.findOneById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> debtService.undoClientDebtRedemption(visit));
    }
    @Test
    void undo_func_clientMismatch_throwsIllegalStateException() {
        DebtRedemption redemption = buildRedemptionEntity(1L);
        Visit visit = Visit.builder().client(client).debtRedemptions(List.of(redemption)).build();
        Client client2 = Client.builder().id(2L).build();
        ClientDebt clientDebt = ClientDebt.builder().client(client2).build();

        when(debtRepo.findOneById(1L)).thenReturn(Optional.of(clientDebt));

        assertThrows(IllegalStateException.class,
                ()-> debtService.undoClientDebtRedemption(visit));
    }
    @Test
    void undo_func_noSourceVisit_doesNotCallVisitRepo() {
        DebtRedemption redemption = buildRedemptionEntity(1L);
        Visit visit = Visit.builder().client(client).debtRedemptions(List.of(redemption)).build();
        ClientDebt clientDebt = ClientDebt.builder().client(client).sourceVisit(null).build();

        when(debtRepo.findOneById(1L)).thenReturn(Optional.of(clientDebt));

        debtService.undoClientDebtRedemption(visit);

        verify(debtRepo).save(clientDebt);
        verify(visitRepo, never()).findOneById(any());
    }
    @Test
    void undo_func_sourceVisitNotFound_throws() {
        DebtRedemption redemption = buildRedemptionEntity(1L);
        Visit visit = Visit.builder().client(client).debtRedemptions(List.of(redemption)).build();
        Visit sourceVisit = Visit.builder().id(2L).build();
        ClientDebt clientDebt = ClientDebt.builder().client(client).sourceVisit(sourceVisit).build();

        when(debtRepo.findOneById(1L)).thenReturn(Optional.of(clientDebt));
        when(visitRepo.findOneById(2L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> debtService.undoClientDebtRedemption(visit));
    }
    @Test
    void undo_func_sourceVisitPartiallyPaid_setsPartialStatus() {
        DebtRedemption redemption = buildRedemptionEntity(1L);
        Visit visit = Visit.builder().client(client).debtRedemptions(List.of(redemption)).build();
        Payment payment = Payment.builder().amount(BigDecimal.valueOf(50.0)).build();
        Visit sourceVisit = Visit.builder().id(2L).totalValue(BigDecimal.valueOf(100.0)).payments(List.of(payment)).build();
        ClientDebt clientDebt = ClientDebt.builder().client(client).sourceVisit(sourceVisit).build();

        when(debtRepo.findOneById(1L)).thenReturn(Optional.of(clientDebt));
        when(visitRepo.findOneById(2L)).thenReturn(Optional.of(sourceVisit));
        debtService.undoClientDebtRedemption(visit);

        assertEquals(PaymentStatus.PARTIAL, sourceVisit.getPaymentStatus());
        verify(visitRepo).save(sourceVisit);
    }
    @Test
    void undo_func_sourceVisitUnpaid_setsUnpaidStatus() {
        DebtRedemption redemption = buildRedemptionEntity(1L);
        Visit visit = Visit.builder().client(client).debtRedemptions(List.of(redemption)).build();
        Visit sourceVisit = Visit.builder().id(2L).totalValue(BigDecimal.valueOf(100.0)).payments(new ArrayList<>()).build();
        ClientDebt clientDebt = ClientDebt.builder().client(client).sourceVisit(sourceVisit).build();

        when(debtRepo.findOneById(1L)).thenReturn(Optional.of(clientDebt));
        when(visitRepo.findOneById(2L)).thenReturn(Optional.of(sourceVisit));
        debtService.undoClientDebtRedemption(visit);

        assertEquals(PaymentStatus.UNPAID, sourceVisit.getPaymentStatus());
        verify(visitRepo).save(sourceVisit);
    }
    @Test
    void undo_func_clientDebtSetUnpaidAndSaved() {
        DebtRedemption redemption = buildRedemptionEntity(1L);
        Visit visit = Visit.builder().client(client).debtRedemptions(List.of(redemption)).build();
        Visit sourceVisit = Visit.builder().id(2L).totalValue(BigDecimal.valueOf(100.0)).payments(new ArrayList<>()).build();
        ClientDebt clientDebt = ClientDebt.builder().client(client).sourceVisit(sourceVisit).build();

        when(debtRepo.findOneById(1L)).thenReturn(Optional.of(clientDebt));
        when(visitRepo.findOneById(2L)).thenReturn(Optional.of(sourceVisit));
        debtService.undoClientDebtRedemption(visit);

        assertEquals(PaymentStatus.UNPAID, sourceVisit.getPaymentStatus());
        verify(visitRepo).save(sourceVisit);
        assertEquals(PaymentStatus.UNPAID, clientDebt.getPaymentStatus());
        verify(debtRepo).save(clientDebt);
    }
    //checkIfDebtIsPaid
    @Test
    void checkIfDebtIsPaid_notRedeemed_doesNothing() {
        Visit visit = Visit.builder().id(1L).build();

        when(visitRepo.findByDebtSourceVisitId(1L)).thenReturn(Optional.empty());

        debtService.checkIfDebtIsPaid(visit);

        verify(debtRepo, never()).findOneBySourceVisitId(any());
    }
    @Test
    void checkIfDebtIsPaid_alreadyRedeemed_throwsIllegalStateException() {
        Visit visit = Visit.builder().id(1L).build();
        Visit redeemingVisit = Visit.builder().id(2L).build();
        ClientDebt debt = ClientDebt.builder().id(1L).build();

        when(visitRepo.findByDebtSourceVisitId(1L)).thenReturn(Optional.of(redeemingVisit));
        when(debtRepo.findOneBySourceVisitId(1L)).thenReturn(Optional.of(debt));

        assertThrows(IllegalStateException.class,
                () -> debtService.checkIfDebtIsPaid(visit));
    }
    @Test
    void checkIfDebtIsPaid_redeemedButDebtRecordNotFound_throwsResourceNotFoundException() {
        Visit visit = Visit.builder().id(1L).build();
        Visit redeemingVisit = Visit.builder().id(2L).build();

        when(visitRepo.findByDebtSourceVisitId(1L)).thenReturn(Optional.of(redeemingVisit));
        when(debtRepo.findOneBySourceVisitId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> debtService.checkIfDebtIsPaid(visit));
    }
    //undoAbsenceFeeOrPartialPaymentFee
    @Test
    void undoAbsenceFee_debtNotFound_throws() {
        Visit visit = Visit.builder().id(1L).build();

        when(debtRepo.findOneBySourceVisitId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> debtService.undoAbsenceFeeOrPartialPaymentFee(visit));
    }
    @Test
    void undoAbsenceFee_debtFound_deletesDebt() {
        Visit visit = Visit.builder().id(1L).build();
        ClientDebt clientDebt = ClientDebt.builder().id(1L).build();

        when(debtRepo.findOneBySourceVisitId(1L)).thenReturn(Optional.of(clientDebt));

        debtService.undoAbsenceFeeOrPartialPaymentFee(visit);

        verify(debtRepo).delete(clientDebt);
    }
}

package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ClientDebtDTO;
import com.clinic.clinicmanager.DTO.request.DebtFilterDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientDebt;
import com.clinic.clinicmanager.model.constants.DebtType;
import com.clinic.clinicmanager.model.constants.PaymentStatus;
import com.clinic.clinicmanager.repo.ClientDebtRepo;
import com.clinic.clinicmanager.service.impl.ClientDebtServiceImpl;
import com.clinic.clinicmanager.service.impl.OwnershipService;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientDebtServiceImplTest {

    @Mock ClientDebtRepo debtRepo;
    @Mock AuditLogService auditLogService;
    @Mock OwnershipService ownershipService;

    @InjectMocks
    ClientDebtServiceImpl debtService;

    private Client client() {
        Client c = new Client();
        c.setId(1L);
        c.setFirstName("Jan");
        c.setLastName("Kowalski");
        return c;
    }

    private ClientDebt debt(Long id) {
        return ClientDebt.builder()
                .id(id)
                .client(client())
                .type(DebtType.ABSENCE_FEE)
                .value(100.0)
                .paymentStatus(PaymentStatus.UNPAID)
                .createdByUserId(10L)
                .build();
    }


    @Test
    void getDebtById_shouldReturnDTO_whenFound() {
        when(debtRepo.findOneById(1L)).thenReturn(Optional.of(debt(1L)));

        ClientDebtDTO result = debtService.getDebtById(1L);

        assertEquals(1L, result.getId());
    }

    @Test
    void getDebtById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(debtRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> debtService.getDebtById(99L));
    }

    @Test
    void getDebtBySourceVisitId_shouldReturnDTO_whenFound() {
        when(debtRepo.findOneBySourceVisitId(5L)).thenReturn(Optional.of(debt(1L)));

        assertNotNull(debtService.getDebtBySourceVisitId(5L));
    }

    @Test
    void getDebtBySourceVisitId_shouldReturnNull_whenNotFound() {
        when(debtRepo.findOneBySourceVisitId(99L)).thenReturn(Optional.empty());

        assertNull(debtService.getDebtBySourceVisitId(99L));
    }

    @Test
    void getDebts_shouldPassNullsToRepo_whenFilterIsNull() {
        when(debtRepo.findAllWithFilters(isNull(), isNull())).thenReturn(List.of());

        debtService.getDebts(null);

        verify(debtRepo).findAllWithFilters(isNull(), isNull());
    }

    @Test
    void getDebts_shouldPassFilterValuesToRepo_whenFilterProvided() {
        DebtFilterDTO filter = new DebtFilterDTO();
        filter.setPaymentStatus(PaymentStatus.UNPAID);
        filter.setKeyword("Jan");
        when(debtRepo.findAllWithFilters(eq(PaymentStatus.UNPAID), eq("Jan"))).thenReturn(List.of());

        debtService.getDebts(filter);

        verify(debtRepo).findAllWithFilters(eq(PaymentStatus.UNPAID), eq("Jan"));
    }

    @Test
    void getUnpaidDebtsByClientId_shouldDelegateToRepo() {
        when(debtRepo.findAllByClientIdAndPaymentStatus(1L, PaymentStatus.UNPAID)).thenReturn(List.of(debt(1L)));

        List<ClientDebtDTO> result = debtService.getUnpaidDebtsByClientId(1L);

        assertEquals(1, result.size());
    }

    @Test
    void createDebt_shouldSetCreatedByUserIdFromSession_andCallAuditLog() {
        ClientDebtDTO input = new ClientDebtDTO();
        input.setType(DebtType.ABSENCE_FEE);
        input.setValue(100.0);
        ClientDebtDTO clientDTO = new ClientDebtDTO();
        clientDTO.setId(1L);

        try (MockedStatic<SessionUtils> mocked = mockStatic(SessionUtils.class)) {
            mocked.when(SessionUtils::getUserIdFromSession).thenReturn(10L);
            when(debtRepo.save(any())).thenReturn(debt(1L));

            ClientDebtDTO result = debtService.createDebt(input);

            assertEquals(1L, result.getId());
            verify(auditLogService).logCreate(eq("ClientDebt"), eq(1L), anyString(), any());
        }
    }

    @Test
    void updateDebt_shouldThrowResourceNotFoundException_whenNotFound() {
        when(debtRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> debtService.updateDebt(99L, new ClientDebtDTO()));
    }

    @Test
    void updateDebt_shouldSaveAndReturnDTO_whenOwnershipPasses() {
        when(debtRepo.findOneById(1L)).thenReturn(Optional.of(debt(1L)));
        when(debtRepo.save(any())).thenReturn(debt(1L));
        doNothing().when(ownershipService).checkOwnershipOrAdmin(anyLong());

        ClientDebtDTO input = new ClientDebtDTO();
        input.setType(DebtType.ABSENCE_FEE);
        input.setValue(200.0);

        ClientDebtDTO result = debtService.updateDebt(1L, input);

        assertEquals(1L, result.getId());
        verify(auditLogService).logUpdate(eq("ClientDebt"), eq(1L), anyString(), any(), any());
    }

    @Test
    void updateDebt_shouldThrowUpdateException_whenOwnershipFails() {
        when(debtRepo.findOneById(1L)).thenReturn(Optional.of(debt(1L)));
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(ownershipService).checkOwnershipOrAdmin(anyLong());

        assertThrows(com.clinic.clinicmanager.exceptions.UpdateException.class,
                () -> debtService.updateDebt(1L, new ClientDebtDTO()));
        verify(debtRepo, never()).save(any());
    }

    @Test
    void deleteDebtById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(debtRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> debtService.deleteDebtById(99L));
    }

    @Test
    void deleteDebtById_shouldDeleteAndAudit_whenOwnershipPasses() {
        when(debtRepo.findOneById(1L)).thenReturn(Optional.of(debt(1L)));
        doNothing().when(ownershipService).checkOwnershipOrAdmin(anyLong());

        debtService.deleteDebtById(1L);

        verify(debtRepo).deleteById(1L);
        verify(auditLogService).logDelete(eq("ClientDebt"), eq(1L), anyString(), any());
    }

    @Test
    void deleteDebtById_shouldThrowDeletionException_whenOwnershipFails() {
        when(debtRepo.findOneById(1L)).thenReturn(Optional.of(debt(1L)));
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(ownershipService).checkOwnershipOrAdmin(anyLong());

        assertThrows(com.clinic.clinicmanager.exceptions.DeletionException.class,
                () -> debtService.deleteDebtById(1L));
        verify(debtRepo, never()).deleteById(any());
    }
}
package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.Discount;
import com.clinic.clinicmanager.repo.*;
import com.clinic.clinicmanager.service.impl.ClientServiceImpl;
import com.clinic.clinicmanager.service.impl.OwnershipService;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceImplTest {

    @Mock ClientRepo clientRepo;
    @Mock VisitRepo visitRepo;
    @Mock ClientDebtRepo clientDebtRepo;
    @Mock VoucherRepo voucherRepo;
    @Mock ReviewRepo reviewRepo;
    @Mock AuditLogService auditLogService;
    @Mock OwnershipService ownershipService;

    @InjectMocks ClientServiceImpl clientService;


    @Test
    void deleteClientById_softDelete_clearsDiscountBeforeSaving() {
        Discount discount = Discount.builder().id(1L).name("VIP10").percentageValue(10).build();
        Client client = Client.builder()
                .id(1L).firstName("Jan").lastName("Kowalski")
                .discount(discount).isDeleted(false).build();

        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        doNothing().when(ownershipService).checkOwnershipOrAdmin(any());
        when(visitRepo.existsByClientId(1L)).thenReturn(true);
        when(clientRepo.save(any())).thenReturn(client);
        doNothing().when(auditLogService).logDelete(any(), any(), any(), any());

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            clientService.deleteClientById(1L);
        }

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepo).save(captor.capture());

        Client saved = captor.getValue();
        assertThat(saved.getDiscount()).isNull();
        assertThat(saved.getIsDeleted()).isTrue();
    }

    @Test
    void deleteClientById_permanentDelete_clientWithDiscountIsDeleted() {
        Discount discount = Discount.builder().id(1L).name("VIP10").percentageValue(10).build();
        Client client = Client.builder()
                .id(1L).firstName("Jan").lastName("Kowalski")
                .discount(discount).isDeleted(false).build();

        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        doNothing().when(ownershipService).checkOwnershipOrAdmin(any());
        when(visitRepo.existsByClientId(1L)).thenReturn(false);
        when(clientDebtRepo.existsByClientId(1L)).thenReturn(false);
        when(voucherRepo.existsByClientId(1L)).thenReturn(false);
        when(reviewRepo.existsByClientId(1L)).thenReturn(false);
        doNothing().when(clientRepo).deleteById(1L);
        doNothing().when(auditLogService).logDelete(any(), any(), any(), any());

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            clientService.deleteClientById(1L);
        }

        verify(clientRepo).deleteById(1L);
        verify(clientRepo, never()).save(any());
    }
}

package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.DTO.VoucherDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.AppSettings;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.Voucher;
import com.clinic.clinicmanager.model.constants.VoucherStatus;
import com.clinic.clinicmanager.repo.AppSettingsRepo;
import com.clinic.clinicmanager.repo.SaleItemRepo;
import com.clinic.clinicmanager.repo.VisitRepo;
import com.clinic.clinicmanager.repo.VoucherRepo;
import com.clinic.clinicmanager.service.impl.OwnershipService;
import com.clinic.clinicmanager.service.impl.VoucherServiceImpl;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoucherServiceImplTest {

    @Mock VoucherRepo voucherRepo;
    @Mock SaleItemRepo saleItemRepo;
    @Mock AppSettingsRepo settingsRepo;
    @Mock VisitRepo visitRepo;
    @Mock AuditLogService auditLogService;
    @Mock OwnershipService ownershipService;

    @InjectMocks
    VoucherServiceImpl voucherService;

    private Client client;
    private Voucher voucher;
    private AppSettings settings;

    @BeforeEach
    void setUp() {
        client = Client.builder()
                .id(1L).firstName("Jan").lastName("Kowalski").isDeleted(false).build();

        voucher = Voucher.builder()
                .id(10L)
                .value(100.0)
                .issueDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusMonths(3))
                .client(client)
                .status(VoucherStatus.ACTIVE)
                .createdByUserId(5L)
                .build();

        settings = AppSettings.builder().id(1L).voucherExpiryTime(3).build();
    }

    @Test
    void getVoucherById_shouldReturnDTO_whenFound() {
        when(voucherRepo.findOneById(10L)).thenReturn(Optional.of(voucher));

        VoucherDTO result = voucherService.getVoucherById(10L);

        assertEquals(10L, result.getId());
        assertEquals(VoucherStatus.ACTIVE, result.getStatus());
    }

    @Test
    void getVoucherById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(voucherRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> voucherService.getVoucherById(99L));
    }

    @Test
    void getVouchers_shouldHandleNullFilter() {
        when(voucherRepo.findAllWithFilters(isNull(), isNull())).thenReturn(List.of(voucher));
        when(visitRepo.findPurchaseVisitIdByVoucherId(10L)).thenReturn(null);

        List<VoucherDTO> result = voucherService.getVouchers(null);

        assertEquals(1, result.size());
    }

    @Test
    void createVoucher_shouldSetStatusActiveAndCalculateExpiryDate() {
        VoucherDTO input = new VoucherDTO();
        input.setClient(new ClientDTO(client));
        input.setValue(100.0);
        input.setIssueDate(LocalDate.of(2025, 1, 1));
        input.setStatus(null);

        when(settingsRepo.getSettings()).thenReturn(settings);
        when(voucherRepo.save(any(Voucher.class))).thenReturn(voucher);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(5L);

            voucherService.createVoucher(input);

            verify(voucherRepo).save(argThat(v ->
                    v.getStatus() == VoucherStatus.ACTIVE &&
                    LocalDate.of(2025, 4, 1).equals(v.getExpiryDate())
            ));
        }
    }

    @Test
    void createVoucher_shouldNotOverrideExpiryDate_whenAlreadySet() {
        LocalDate customExpiry = LocalDate.of(2026, 6, 1);
        VoucherDTO input = new VoucherDTO();
        input.setClient(new ClientDTO(client));
        input.setValue(100.0);
        input.setIssueDate(LocalDate.of(2025, 1, 1));
        input.setExpiryDate(customExpiry);

        when(settingsRepo.getSettings()).thenReturn(settings);
        when(voucherRepo.save(any(Voucher.class))).thenReturn(voucher);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(5L);

            voucherService.createVoucher(input);

            verify(voucherRepo).save(argThat(v -> customExpiry.equals(v.getExpiryDate())));
        }
    }

    @Test
    void createVoucher_shouldSetCreatedByUserIdFromSession() {
        VoucherDTO input = new VoucherDTO();
        input.setClient(new ClientDTO(client));
        input.setIssueDate(LocalDate.now());
        input.setExpiryDate(LocalDate.now().plusMonths(3));

        when(settingsRepo.getSettings()).thenReturn(settings);
        when(voucherRepo.save(any(Voucher.class))).thenReturn(voucher);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(7L);

            voucherService.createVoucher(input);

            verify(voucherRepo).save(argThat(v -> Long.valueOf(7L).equals(v.getCreatedByUserId())));
        }
    }

    @Test
    void updateVoucher_shouldPreserveCreatedByUserId() {
        VoucherDTO input = new VoucherDTO();
        input.setClient(new ClientDTO(client));
        input.setStatus(VoucherStatus.ACTIVE);
        input.setIssueDate(LocalDate.now());
        input.setExpiryDate(LocalDate.now().plusMonths(3));

        when(voucherRepo.findOneById(10L)).thenReturn(Optional.of(voucher));
        when(voucherRepo.save(any(Voucher.class))).thenReturn(voucher);

        voucherService.updateVoucher(10L, input);

        verify(voucherRepo).save(argThat(v -> Long.valueOf(5L).equals(v.getCreatedByUserId())));
    }

    @Test
    void updateVoucher_shouldThrowResourceNotFoundException_whenNotFound() {
        when(voucherRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> voucherService.updateVoucher(99L, new VoucherDTO()));
    }

    @Test
    void deleteVoucherById_shouldDeleteAndLogAudit_whenOwner() {
        when(voucherRepo.findOneById(10L)).thenReturn(Optional.of(voucher));

        voucherService.deleteVoucherById(10L);

        verify(voucherRepo).deleteById(10L);
        verify(auditLogService).logDelete(eq("Voucher"), eq(10L), anyString(), any());
    }

    @Test
    void deleteVoucherById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(voucherRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> voucherService.deleteVoucherById(99L));
        verify(voucherRepo, never()).deleteById(any());
    }

    @Test
    void deleteVoucherById_shouldRethrowAccessDeniedException_whenNotOwner() {
        when(voucherRepo.findOneById(10L)).thenReturn(Optional.of(voucher));
        doThrow(new AccessDeniedException("denied")).when(ownershipService).checkOwnershipOrAdmin(5L);

        assertThrows(Exception.class, () -> voucherService.deleteVoucherById(10L));
        verify(voucherRepo, never()).deleteById(any());
    }

    @Test
    void recalculateStatus_shouldReturnUsed_whenStatusIsUsed() {
        voucher.setStatus(VoucherStatus.USED);
        voucher.setExpiryDate(LocalDate.now().minusDays(1));

        assertEquals(VoucherStatus.USED, voucherService.recalculateStatus(voucher));
    }

    @Test
    void recalculateStatus_shouldReturnExpired_whenExpiryDateIsInPast() {
        voucher.setStatus(VoucherStatus.ACTIVE);
        voucher.setExpiryDate(LocalDate.now().minusDays(1));

        assertEquals(VoucherStatus.EXPIRED, voucherService.recalculateStatus(voucher));
    }

    @Test
    void recalculateStatus_shouldReturnActive_whenNotExpired() {
        voucher.setStatus(VoucherStatus.ACTIVE);
        voucher.setExpiryDate(LocalDate.now().plusDays(10));

        assertEquals(VoucherStatus.ACTIVE, voucherService.recalculateStatus(voucher));
    }

    @Test
    void hasSaleReference_shouldDelegateToSaleItemRepo() {
        when(saleItemRepo.existsByVoucherId(10L)).thenReturn(true);

        assertTrue(voucherService.hasSaleReference(10L));
    }
}

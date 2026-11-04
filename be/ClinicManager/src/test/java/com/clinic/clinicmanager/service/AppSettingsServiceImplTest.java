package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.AppSettingsDTO;
import com.clinic.clinicmanager.DTO.DiscountSettingsDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.model.AppSettings;
import com.clinic.clinicmanager.repo.AppSettingsRepo;
import com.clinic.clinicmanager.service.impl.AppSettingsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppSettingsServiceImplTest {

    @Mock
    AppSettingsRepo settingsRepo;

    @Mock
    AuditLogService auditLogService;

    @InjectMocks
    AppSettingsServiceImpl appSettingsService;

    private AppSettings defaultSettings() {
        return AppSettings.builder()
                .id(1L)
                .voucherExpiryTime(3)
                .visitAbsenceRate(80)
                .visitVipRate(140)
                .boostNetRate(45)
                .googleReviewDiscount(5)
                .booksyHappyHours(5)
                .build();
    }

    @Test
    void getSettings_shouldReturnAppSettingsDTO() {
        when(settingsRepo.getSettings()).thenReturn(defaultSettings());

        AppSettingsDTO result = appSettingsService.getSettings();

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(3, result.getVoucherExpiryTime());
        assertEquals(80, result.getVisitAbsenceRate());
    }

    @Test
    void getDiscountSettings_shouldReturnDiscountSettingsDTO() {
        when(settingsRepo.getSettings()).thenReturn(defaultSettings());

        DiscountSettingsDTO result = appSettingsService.getDiscountSettings();

        assertNotNull(result);
        assertEquals(5, result.getGoogleReviewDiscount());
        assertEquals(5, result.getBooksyHappyHours());
    }

    @Test
    void updateSettings_shouldSaveWithExistingId_andReturnUpdatedDTO() {
        AppSettings saved = AppSettings.builder()
                .id(1L).voucherExpiryTime(6).visitAbsenceRate(90)
                .visitVipRate(150).boostNetRate(50).googleReviewDiscount(10).booksyHappyHours(10)
                .build();

        AppSettingsDTO input = new AppSettingsDTO();
        input.setVoucherExpiryTime(6);

        when(settingsRepo.getSettings()).thenReturn(defaultSettings());
        when(settingsRepo.save(any(AppSettings.class))).thenReturn(saved);

        AppSettingsDTO result = appSettingsService.updateSettings(input);

        assertEquals(1L, result.getId());
        assertEquals(6, result.getVoucherExpiryTime());
        verify(settingsRepo).save(any(AppSettings.class));
    }

    @Test
    void updateSettings_shouldCallAuditLogUpdate() {
        when(settingsRepo.getSettings()).thenReturn(defaultSettings());
        when(settingsRepo.save(any(AppSettings.class))).thenReturn(defaultSettings());

        appSettingsService.updateSettings(new AppSettingsDTO());

        verify(auditLogService).logUpdate(eq("AppSettings"), eq(1L), isNull(), any(), any());
    }

    @Test
    void updateSettings_shouldRethrowResourceNotFoundException_whenGetSettingsFails() {
        when(settingsRepo.getSettings()).thenThrow(new ResourceNotFoundException("Settings not found"));

        assertThrows(ResourceNotFoundException.class,
                () -> appSettingsService.updateSettings(new AppSettingsDTO()));

        verify(settingsRepo, never()).save(any());
    }

    @Test
    void updateSettings_shouldThrowUpdateException_whenSaveFails() {
        when(settingsRepo.getSettings()).thenReturn(defaultSettings());
        when(settingsRepo.save(any(AppSettings.class))).thenThrow(new RuntimeException("DB error"));

        assertThrows(UpdateException.class,
                () -> appSettingsService.updateSettings(new AppSettingsDTO()));
    }
}

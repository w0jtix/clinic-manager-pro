package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.StatSettingsDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.model.StatSettings;
import com.clinic.clinicmanager.repo.StatSettingsRepo;
import com.clinic.clinicmanager.service.impl.StatSettingsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatSettingsServiceImplTest {

    @Mock StatSettingsRepo settingsRepo;
    @Mock AuditLogService auditLogService;

    @InjectMocks
    StatSettingsServiceImpl statSettingsService;

    private StatSettings defaultSettings() {
        return StatSettings.builder()
                .id(1L)
                .bonusThreshold(1000)
                .servicesRevenueGoal(3000)
                .productsRevenueGoal(500)
                .saleBonusPayoutMonths(Set.of(1, 4, 7, 10))
                .build();
    }

    @Test
    void getSettings_shouldReturnDTO_withCorrectValues() {
        when(settingsRepo.getSettings()).thenReturn(defaultSettings());

        StatSettingsDTO result = statSettingsService.getSettings();

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(1000, result.getBonusThreshold());
        assertEquals(3000, result.getServicesRevenueGoal());
        assertEquals(500, result.getProductsRevenueGoal());
    }

    @Test
    void updateSettings_shouldSaveWithExistingId_andReturnDTO() {
        StatSettings saved = StatSettings.builder()
                .id(1L).bonusThreshold(2000).servicesRevenueGoal(4000)
                .productsRevenueGoal(600).saleBonusPayoutMonths(Set.of(1, 4, 7, 10)).build();

        StatSettingsDTO input = new StatSettingsDTO();
        input.setBonusThreshold(2000);
        input.setServicesRevenueGoal(4000);
        input.setProductsRevenueGoal(600);
        input.setSaleBonusPayoutMonths(Set.of(1, 4, 7, 10));

        when(settingsRepo.getSettings()).thenReturn(defaultSettings());
        when(settingsRepo.save(any(StatSettings.class))).thenReturn(saved);

        StatSettingsDTO result = statSettingsService.updateSettings(input);

        assertEquals(1L, result.getId());
        assertEquals(2000, result.getBonusThreshold());
        verify(settingsRepo).save(any(StatSettings.class));
    }

    @Test
    void updateSettings_shouldRethrowResourceNotFoundException_whenGetSettingsFails() {
        when(settingsRepo.getSettings()).thenThrow(new ResourceNotFoundException("not found"));

        assertThrows(ResourceNotFoundException.class,
                () -> statSettingsService.updateSettings(new StatSettingsDTO()));

        verify(settingsRepo, never()).save(any());
    }

    @Test
    void updateSettings_shouldThrowUpdateException_whenSaveFails() {
        when(settingsRepo.getSettings()).thenReturn(defaultSettings());
        when(settingsRepo.save(any(StatSettings.class))).thenThrow(new RuntimeException("DB error"));

        StatSettingsDTO input = new StatSettingsDTO();
        input.setSaleBonusPayoutMonths(Set.of(1, 4, 7, 10));

        assertThrows(UpdateException.class, () -> statSettingsService.updateSettings(input));
    }
}

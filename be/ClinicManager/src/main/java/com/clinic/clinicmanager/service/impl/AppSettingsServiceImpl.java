package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.AppSettingsDTO;
import com.clinic.clinicmanager.DTO.DiscountSettingsDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.repo.AppSettingsRepo;
import com.clinic.clinicmanager.service.AppSettingsService;
import com.clinic.clinicmanager.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AppSettingsServiceImpl implements AppSettingsService {

    private final AppSettingsRepo settingsRepo;
    private final AuditLogService auditLogService;

    @Override
    public AppSettingsDTO getSettings () {
        return new AppSettingsDTO(settingsRepo.getSettings());
    }

    @Override
    public DiscountSettingsDTO getDiscountSettings() {
        return new DiscountSettingsDTO(settingsRepo.getSettings());
    }

    @Override
    public AppSettingsDTO updateSettings(AppSettingsDTO settings) {
        try {
            AppSettingsDTO oldSettings = getSettings();

            settings.setId(oldSettings.getId());
            AppSettingsDTO savedSettings = new AppSettingsDTO(settingsRepo.save(settings.toEntity()));

            auditLogService.logUpdate("AppSettings", savedSettings.getId(),null, oldSettings, savedSettings);
            return savedSettings;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update Settings. Reason: " + e.getMessage(), e);
        }
    }
}

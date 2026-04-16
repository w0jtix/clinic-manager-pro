package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.StatSettingsDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.repo.StatSettingsRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.StatSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StatSettingsServiceImpl implements StatSettingsService {

    private final StatSettingsRepo settingsRepo;
    private final AuditLogService auditLogService;

    @Override
    public StatSettingsDTO getSettings() {
        return new StatSettingsDTO(settingsRepo.getSettings());
    }

    @Override
    public StatSettingsDTO updateSettings(StatSettingsDTO settings) {
        try {
            StatSettingsDTO oldSettings = getSettings();

            settings.setId(oldSettings.getId());
            StatSettingsDTO savedSettings = new StatSettingsDTO(settingsRepo.save(settings.toEntity()));

            auditLogService.logUpdate("StatSettings", savedSettings.getId(), null, oldSettings, savedSettings);
            return savedSettings;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update Stat Settings. Reason: " + e.getMessage(), e);
        }
    }
}

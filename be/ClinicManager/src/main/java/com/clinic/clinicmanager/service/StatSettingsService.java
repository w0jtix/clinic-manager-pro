package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.StatSettingsDTO;

public interface StatSettingsService {

    StatSettingsDTO getSettings();

    StatSettingsDTO updateSettings(StatSettingsDTO settings);
}

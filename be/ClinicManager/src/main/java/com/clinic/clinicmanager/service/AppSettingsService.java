package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.AppSettingsDTO;
import com.clinic.clinicmanager.DTO.DiscountSettingsDTO;

public interface AppSettingsService {

    AppSettingsDTO getSettings();

    DiscountSettingsDTO getDiscountSettings();

    AppSettingsDTO updateSettings(AppSettingsDTO settings);
}

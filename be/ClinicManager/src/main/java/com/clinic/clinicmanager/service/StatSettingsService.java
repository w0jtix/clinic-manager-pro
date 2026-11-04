package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.CompanyLogoDTO;
import com.clinic.clinicmanager.DTO.StatSettingsDTO;
import org.springframework.web.multipart.MultipartFile;

public interface StatSettingsService {

    StatSettingsDTO getSettings();

    StatSettingsDTO updateSettings(StatSettingsDTO settings);

    void uploadCompanyLogo(MultipartFile file);

    void deleteCompanyLogo();

    CompanyLogoDTO getCompanyLogo();

    void uploadEmailFooterLogo(MultipartFile file);

    void deleteEmailFooterLogo();

    CompanyLogoDTO getEmailFooterLogo();
}

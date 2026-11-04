package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.CompanyLogoDTO;
import com.clinic.clinicmanager.DTO.StatSettingsDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.StatSettings;
import com.clinic.clinicmanager.repo.StatSettingsRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.StatSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class StatSettingsServiceImpl implements StatSettingsService {

    private static final long SETTINGS_ID = 1L;
    private static final String ALLOWED_CONTENT_TYPE = "image/png";
    private static final long MAX_LOGO_SIZE_BYTES = 1024L * 1024L;
    private static final int MIN_LOGO_WIDTH_PX = 100;
    private static final int MAX_LOGO_WIDTH_PX = 2000;

    private final StatSettingsRepo settingsRepo;
    private final AuditLogService auditLogService;

    @Override
    public StatSettingsDTO getSettings() {
        return new StatSettingsDTO(settingsRepo.getSettings(),
                settingsRepo.hasCompanyLogo(SETTINGS_ID), settingsRepo.hasEmailFooterLogo(SETTINGS_ID));
    }

    @Override
    public StatSettingsDTO updateSettings(StatSettingsDTO settings) {
        try {
            StatSettings existing = settingsRepo.getSettings();
            StatSettingsDTO oldSettings = new StatSettingsDTO(existing,
                    settingsRepo.hasCompanyLogo(SETTINGS_ID), settingsRepo.hasEmailFooterLogo(SETTINGS_ID));

            settings.setId(oldSettings.getId());
            StatSettings toSave = settings.toEntity();
            toSave.setCompanyLogo(existing.getCompanyLogo());
            toSave.setCompanyLogoContentType(existing.getCompanyLogoContentType());
            toSave.setEmailFooterLogo(existing.getEmailFooterLogo());
            toSave.setEmailFooterLogoContentType(existing.getEmailFooterLogoContentType());

            StatSettings saved = settingsRepo.save(toSave);
            StatSettingsDTO savedSettings = new StatSettingsDTO(saved,
                    saved.getCompanyLogo() != null, saved.getEmailFooterLogo() != null);

            auditLogService.logUpdate("StatSettings", savedSettings.getId(), null, oldSettings, savedSettings);
            return savedSettings;
        } catch (ResourceNotFoundException | ConflictException | ValidationException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update Stat Settings.", e);
        }
    }

    @Override
    @Transactional
    public void uploadCompanyLogo(MultipartFile file) {
        BufferedImage image = validateLogo(file);

        StatSettings settings = settingsRepo.getSettings();
        CompanyLogoAudit oldState = auditState(settings);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Nie udało się odczytać pliku.", e);
        }
        settings.setCompanyLogo(bytes);
        settings.setCompanyLogoContentType(ALLOWED_CONTENT_TYPE);
        StatSettings saved = settingsRepo.save(settings);

        CompanyLogoAudit newState = new CompanyLogoAudit(true, image.getWidth(), image.getHeight(), bytes.length);
        auditLogService.logUpdate("StatSettings", saved.getId(), null, oldState, newState);
    }

    @Override
    @Transactional
    public void deleteCompanyLogo() {
        StatSettings settings = settingsRepo.getSettings();
        CompanyLogoAudit oldState = auditState(settings);

        settings.setCompanyLogo(null);
        settings.setCompanyLogoContentType(null);
        StatSettings saved = settingsRepo.save(settings);

        auditLogService.logUpdate("StatSettings", saved.getId(), null,
                oldState, new CompanyLogoAudit(false, null, null, null));
    }

    private CompanyLogoAudit auditState(StatSettings settings) {
        byte[] logo = settings.getCompanyLogo();
        if (logo == null || logo.length == 0) {
            return new CompanyLogoAudit(false, null, null, null);
        }
        return new CompanyLogoAudit(true, null, null, logo.length);
    }

    record CompanyLogoAudit(boolean companyLogoPresent, Integer companyLogoWidth,
                            Integer companyLogoHeight, Integer companyLogoSizeBytes) {
    }

    @Override
    @Transactional
    public void uploadEmailFooterLogo(MultipartFile file) {
        BufferedImage image = validateLogo(file);

        StatSettings settings = settingsRepo.getSettings();
        EmailFooterLogoAudit oldState = footerAuditState(settings);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Nie udało się odczytać pliku.", e);
        }
        settings.setEmailFooterLogo(bytes);
        settings.setEmailFooterLogoContentType(ALLOWED_CONTENT_TYPE);
        StatSettings saved = settingsRepo.save(settings);

        EmailFooterLogoAudit newState =
                new EmailFooterLogoAudit(true, image.getWidth(), image.getHeight(), bytes.length);
        auditLogService.logUpdate("StatSettings", saved.getId(), null, oldState, newState);
    }

    @Override
    @Transactional
    public void deleteEmailFooterLogo() {
        StatSettings settings = settingsRepo.getSettings();
        EmailFooterLogoAudit oldState = footerAuditState(settings);

        settings.setEmailFooterLogo(null);
        settings.setEmailFooterLogoContentType(null);
        StatSettings saved = settingsRepo.save(settings);

        auditLogService.logUpdate("StatSettings", saved.getId(), null,
                oldState, new EmailFooterLogoAudit(false, null, null, null));
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyLogoDTO getEmailFooterLogo() {
        StatSettings settings = settingsRepo.getSettings();
        byte[] logo = settings.getEmailFooterLogo();
        if (logo == null || logo.length == 0) {
            throw new ResourceNotFoundException("Email footer logo is not set.");
        }
        String contentType = settings.getEmailFooterLogoContentType() != null
                ? settings.getEmailFooterLogoContentType()
                : ALLOWED_CONTENT_TYPE;
        return new CompanyLogoDTO(logo, contentType);
    }

    private EmailFooterLogoAudit footerAuditState(StatSettings settings) {
        byte[] logo = settings.getEmailFooterLogo();
        if (logo == null || logo.length == 0) {
            return new EmailFooterLogoAudit(false, null, null, null);
        }
        return new EmailFooterLogoAudit(true, null, null, logo.length);
    }

    record EmailFooterLogoAudit(boolean emailFooterLogoPresent, Integer emailFooterLogoWidth,
                                Integer emailFooterLogoHeight, Integer emailFooterLogoSizeBytes) {
    }

    @Override
    @Transactional(readOnly = true)
    public CompanyLogoDTO getCompanyLogo() {
        StatSettings settings = settingsRepo.getSettings();
        byte[] logo = settings.getCompanyLogo();
        if (logo == null || logo.length == 0) {
            throw new ResourceNotFoundException("Company logo is not set.");
        }
        String contentType = settings.getCompanyLogoContentType() != null
                ? settings.getCompanyLogoContentType()
                : ALLOWED_CONTENT_TYPE;
        return new CompanyLogoDTO(logo, contentType);
    }

    private BufferedImage validateLogo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Nie wybrano pliku.");
        }
        if (!ALLOWED_CONTENT_TYPE.equalsIgnoreCase(file.getContentType())) {
            throw new ValidationException("Dozwolony format to PNG.");
        }
        if (file.getSize() > MAX_LOGO_SIZE_BYTES) {
            throw new ValidationException("Plik jest za duży (maksymalnie 1 MB).");
        }

        BufferedImage image;
        try (InputStream inputStream = file.getInputStream()) {
            image = ImageIO.read(inputStream);
        } catch (IOException e) {
            throw new ValidationException("Plik nie jest poprawnym obrazem PNG.", e);
        }
        if (image == null) {
            throw new ValidationException("Plik nie jest poprawnym obrazem PNG.");
        }

        if (image.getWidth() < MIN_LOGO_WIDTH_PX) {
            throw new ValidationException("Logo jest za małe (minimum 100 px szerokości).");
        }
        if (image.getWidth() > MAX_LOGO_WIDTH_PX) {
            throw new ValidationException("Logo jest za duże (maksymalnie 2000 px szerokości).");
        }
        return image;
    }
}

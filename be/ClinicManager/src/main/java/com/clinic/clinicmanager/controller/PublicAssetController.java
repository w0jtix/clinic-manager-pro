package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.CompanyLogoDTO;
import com.clinic.clinicmanager.service.StatSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicAssetController {

    private final StatSettingsService settingsService;

    @GetMapping("/email-footer-logo")
    public ResponseEntity<byte[]> footerLogo() {
        CompanyLogoDTO logo = settingsService.getEmailFooterLogo();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(logo.content());
    }
}

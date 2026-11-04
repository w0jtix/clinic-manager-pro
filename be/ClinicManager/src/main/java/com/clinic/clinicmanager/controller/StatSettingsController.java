package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.CompanyLogoDTO;
import com.clinic.clinicmanager.DTO.StatSettingsDTO;
import com.clinic.clinicmanager.service.StatSettingsService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stat-settings")
public class StatSettingsController {

    private final StatSettingsService settingsService;

    @GetMapping
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<StatSettingsDTO> getSettings() {
        StatSettingsDTO settings = settingsService.getSettings();
        return new ResponseEntity<>(settings, HttpStatus.OK);
    }

    @PutMapping
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<StatSettingsDTO> updateSettings(@NonNull @RequestBody StatSettingsDTO settings) {
        StatSettingsDTO newSettings = settingsService.updateSettings(settings);
        return new ResponseEntity<>(newSettings, HttpStatus.OK);
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<Void> uploadLogo(@RequestParam("file") MultipartFile file) {
        settingsService.uploadCompanyLogo(file);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @DeleteMapping("/logo")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<Void> deleteLogo() {
        settingsService.deleteCompanyLogo();
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/logo")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<byte[]> getLogo() {
        CompanyLogoDTO logo = settingsService.getCompanyLogo();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .body(logo.content());
    }

    @PostMapping(value = "/email-footer-logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<Void> uploadEmailFooterLogo(@RequestParam("file") MultipartFile file) {
        settingsService.uploadEmailFooterLogo(file);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @DeleteMapping("/email-footer-logo")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<Void> deleteEmailFooterLogo() {
        settingsService.deleteEmailFooterLogo();
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/email-footer-logo")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<byte[]> getEmailFooterLogo() {
        CompanyLogoDTO logo = settingsService.getEmailFooterLogo();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .body(logo.content());
    }
}

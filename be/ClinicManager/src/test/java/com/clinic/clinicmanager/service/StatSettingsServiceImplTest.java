package com.clinic.clinicmanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.CompanyLogoDTO;
import com.clinic.clinicmanager.DTO.StatSettingsDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.StatSettings;
import com.clinic.clinicmanager.repo.StatSettingsRepo;
import com.clinic.clinicmanager.service.impl.StatSettingsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    private byte[] pngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private MockMultipartFile pngFile(int width, int height) throws IOException {
        return new MockMultipartFile("file", "logo.png", "image/png", pngBytes(width, height));
    }

    @Test
    void uploadCompanyLogo_savesBytesAndContentType_forValidPng() throws IOException {
        StatSettings settings = defaultSettings();
        MockMultipartFile file = pngFile(600, 200);

        when(settingsRepo.getSettings()).thenReturn(settings);
        when(settingsRepo.save(any(StatSettings.class))).thenAnswer(i -> i.getArgument(0));

        statSettingsService.uploadCompanyLogo(file);

        ArgumentCaptor<StatSettings> captor = ArgumentCaptor.forClass(StatSettings.class);
        verify(settingsRepo).save(captor.capture());
        assertArrayEquals(file.getBytes(), captor.getValue().getCompanyLogo());
        assertEquals("image/png", captor.getValue().getCompanyLogoContentType());
    }

    @Test
    void getSettings_reportsLogoPresence_withoutExposingBytes() {
        when(settingsRepo.getSettings()).thenReturn(defaultSettings());
        when(settingsRepo.hasCompanyLogo(1L)).thenReturn(true);

        StatSettingsDTO result = statSettingsService.getSettings();

        assertTrue(result.isHasCompanyLogo());
    }

    @Test
    void uploadCompanyLogo_throwsValidationException_whenFileIsEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", new byte[0]);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> statSettingsService.uploadCompanyLogo(file));

        assertEquals("Nie wybrano pliku.", exception.getMessage());
        verify(settingsRepo, never()).save(any());
    }

    @Test
    void uploadCompanyLogo_throwsValidationException_whenContentTypeIsNotPng() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "logo.jpg", "image/jpeg", pngBytes(600, 200));

        ValidationException exception = assertThrows(ValidationException.class,
                () -> statSettingsService.uploadCompanyLogo(file));

        assertEquals("Dozwolony format to PNG.", exception.getMessage());
        verify(settingsRepo, never()).save(any());
    }

    @Test
    void uploadCompanyLogo_throwsValidationException_whenFileExceedsOneMegabyte() {
        byte[] oversized = new byte[1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png", oversized);

        ValidationException exception = assertThrows(ValidationException.class,
                () -> statSettingsService.uploadCompanyLogo(file));

        assertEquals("Plik jest za duży (maksymalnie 1 MB).", exception.getMessage());
        verify(settingsRepo, never()).save(any());
    }

    @Test
    void uploadCompanyLogo_throwsValidationException_whenBytesAreNotAnImage() {
        MockMultipartFile file = new MockMultipartFile("file", "logo.png", "image/png",
                "to nie jest obrazek".getBytes(StandardCharsets.UTF_8));

        ValidationException exception = assertThrows(ValidationException.class,
                () -> statSettingsService.uploadCompanyLogo(file));

        assertEquals("Plik nie jest poprawnym obrazem PNG.", exception.getMessage());
        verify(settingsRepo, never()).save(any());
    }

    @Test
    void uploadCompanyLogo_throwsValidationException_whenImageIsTooNarrow() throws IOException {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> statSettingsService.uploadCompanyLogo(pngFile(50, 50)));

        assertEquals("Logo jest za małe (minimum 100 px szerokości).", exception.getMessage());
        verify(settingsRepo, never()).save(any());
    }

    @Test
    void uploadCompanyLogo_throwsValidationException_whenImageIsTooWide() throws IOException {
        ValidationException exception = assertThrows(ValidationException.class,
                () -> statSettingsService.uploadCompanyLogo(pngFile(3000, 200)));

        assertEquals("Logo jest za duże (maksymalnie 2000 px szerokości).", exception.getMessage());
        verify(settingsRepo, never()).save(any());
    }

    @Test
    void deleteCompanyLogo_clearsBytesAndContentType() throws IOException {
        StatSettings settings = defaultSettings();
        settings.setCompanyLogo(pngBytes(600, 200));
        settings.setCompanyLogoContentType("image/png");

        when(settingsRepo.getSettings()).thenReturn(settings);
        when(settingsRepo.save(any(StatSettings.class))).thenAnswer(i -> i.getArgument(0));

        statSettingsService.deleteCompanyLogo();

        ArgumentCaptor<StatSettings> captor = ArgumentCaptor.forClass(StatSettings.class);
        verify(settingsRepo).save(captor.capture());
        assertNull(captor.getValue().getCompanyLogo());
        assertNull(captor.getValue().getCompanyLogoContentType());
    }

    @Test
    void getCompanyLogo_returnsBytesWithContentType() throws IOException {
        StatSettings settings = defaultSettings();
        settings.setCompanyLogo(pngBytes(600, 200));
        settings.setCompanyLogoContentType("image/png");

        when(settingsRepo.getSettings()).thenReturn(settings);

        CompanyLogoDTO logo = statSettingsService.getCompanyLogo();

        assertArrayEquals(settings.getCompanyLogo(), logo.content());
        assertEquals("image/png", logo.contentType());
    }

    @Test
    void getCompanyLogo_throwsResourceNotFoundException_whenLogoIsNotSet() {
        when(settingsRepo.getSettings()).thenReturn(defaultSettings());

        assertThrows(ResourceNotFoundException.class, () -> statSettingsService.getCompanyLogo());
    }

    @Test
    void updateSettings_keepsExistingLogo() throws IOException {
        StatSettings existing = defaultSettings();
        existing.setCompanyLogo(pngBytes(600, 200));
        existing.setCompanyLogoContentType("image/png");

        StatSettingsDTO input = new StatSettingsDTO();
        input.setBonusThreshold(2000);
        input.setSaleBonusPayoutMonths(Set.of(1, 4, 7, 10));

        when(settingsRepo.getSettings()).thenReturn(existing);
        when(settingsRepo.save(any(StatSettings.class))).thenAnswer(i -> i.getArgument(0));

        StatSettingsDTO result = statSettingsService.updateSettings(input);

        ArgumentCaptor<StatSettings> captor = ArgumentCaptor.forClass(StatSettings.class);
        verify(settingsRepo).save(captor.capture());
        assertArrayEquals(existing.getCompanyLogo(), captor.getValue().getCompanyLogo());
        assertEquals("image/png", captor.getValue().getCompanyLogoContentType());
        assertTrue(result.isHasCompanyLogo());
    }

    @Test
    void uploadCompanyLogo_logsAuditPayloadConvertibleToMap() throws IOException {
        StatSettings settings = defaultSettings();

        when(settingsRepo.getSettings()).thenReturn(settings);
        when(settingsRepo.save(any(StatSettings.class))).thenAnswer(i -> i.getArgument(0));

        statSettingsService.uploadCompanyLogo(pngFile(600, 200));

        ArgumentCaptor<Object> oldState = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> newState = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).logUpdate(eq("StatSettings"), eq(1L), isNull(),
                oldState.capture(), newState.capture());

        ObjectMapper objectMapper = new ObjectMapper();
        Map<?, ?> oldMap = objectMapper.convertValue(oldState.getValue(), Map.class);
        Map<?, ?> newMap = objectMapper.convertValue(newState.getValue(), Map.class);

        assertEquals(false, oldMap.get("companyLogoPresent"));
        assertEquals(true, newMap.get("companyLogoPresent"));
        assertEquals(600, newMap.get("companyLogoWidth"));
        assertEquals(200, newMap.get("companyLogoHeight"));
    }

    @Test
    void deleteCompanyLogo_logsAuditPayloadConvertibleToMap() throws IOException {
        StatSettings settings = defaultSettings();
        settings.setCompanyLogo(pngBytes(600, 200));
        settings.setCompanyLogoContentType("image/png");

        when(settingsRepo.getSettings()).thenReturn(settings);
        when(settingsRepo.save(any(StatSettings.class))).thenAnswer(i -> i.getArgument(0));

        statSettingsService.deleteCompanyLogo();

        ArgumentCaptor<Object> oldState = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> newState = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).logUpdate(eq("StatSettings"), eq(1L), isNull(),
                oldState.capture(), newState.capture());

        ObjectMapper objectMapper = new ObjectMapper();
        assertEquals(true, objectMapper.convertValue(oldState.getValue(), Map.class).get("companyLogoPresent"));
        assertEquals(false, objectMapper.convertValue(newState.getValue(), Map.class).get("companyLogoPresent"));
    }

    @Test
    void uploadEmailFooterLogo_savesBytesAndContentType_andKeepsCompanyLogoUntouched() throws IOException {
        StatSettings settings = defaultSettings();
        settings.setCompanyLogo(pngBytes(600, 200));
        settings.setCompanyLogoContentType("image/png");

        when(settingsRepo.getSettings()).thenReturn(settings);
        when(settingsRepo.save(any(StatSettings.class))).thenAnswer(i -> i.getArgument(0));

        MockMultipartFile file = pngFile(400, 200);
        statSettingsService.uploadEmailFooterLogo(file);

        ArgumentCaptor<StatSettings> captor = ArgumentCaptor.forClass(StatSettings.class);
        verify(settingsRepo).save(captor.capture());
        assertArrayEquals(file.getBytes(), captor.getValue().getEmailFooterLogo());
        assertEquals("image/png", captor.getValue().getEmailFooterLogoContentType());
        assertArrayEquals(settings.getCompanyLogo(), captor.getValue().getCompanyLogo());
    }

    @Test
    void uploadEmailFooterLogo_throwsValidationException_whenContentTypeIsNotPng() {
        MockMultipartFile file = new MockMultipartFile("file", "logo.jpg", "image/jpeg",
                "x".getBytes(StandardCharsets.UTF_8));

        assertThrows(ValidationException.class, () -> statSettingsService.uploadEmailFooterLogo(file));
        verify(settingsRepo, never()).save(any());
    }

    @Test
    void deleteEmailFooterLogo_clearsBytesAndContentType_andKeepsCompanyLogo() throws IOException {
        StatSettings settings = defaultSettings();
        settings.setEmailFooterLogo(pngBytes(400, 200));
        settings.setEmailFooterLogoContentType("image/png");
        settings.setCompanyLogo(pngBytes(600, 200));
        settings.setCompanyLogoContentType("image/png");

        when(settingsRepo.getSettings()).thenReturn(settings);
        when(settingsRepo.save(any(StatSettings.class))).thenAnswer(i -> i.getArgument(0));

        statSettingsService.deleteEmailFooterLogo();

        ArgumentCaptor<StatSettings> captor = ArgumentCaptor.forClass(StatSettings.class);
        verify(settingsRepo).save(captor.capture());
        assertNull(captor.getValue().getEmailFooterLogo());
        assertNull(captor.getValue().getEmailFooterLogoContentType());
        assertNotNull(captor.getValue().getCompanyLogo());
    }

    @Test
    void getEmailFooterLogo_returnsBytesWithContentType() throws IOException {
        StatSettings settings = defaultSettings();
        settings.setEmailFooterLogo(pngBytes(400, 200));
        settings.setEmailFooterLogoContentType("image/png");

        when(settingsRepo.getSettings()).thenReturn(settings);

        CompanyLogoDTO logo = statSettingsService.getEmailFooterLogo();

        assertArrayEquals(settings.getEmailFooterLogo(), logo.content());
        assertEquals("image/png", logo.contentType());
    }

    @Test
    void getEmailFooterLogo_throwsResourceNotFoundException_whenLogoIsNotSet() {
        when(settingsRepo.getSettings()).thenReturn(defaultSettings());

        assertThrows(ResourceNotFoundException.class, () -> statSettingsService.getEmailFooterLogo());
    }

    @Test
    void updateSettings_keepsExistingEmailFooterLogo() throws IOException {
        StatSettings existing = defaultSettings();
        existing.setEmailFooterLogo(pngBytes(400, 200));
        existing.setEmailFooterLogoContentType("image/png");

        StatSettingsDTO input = new StatSettingsDTO();
        input.setBonusThreshold(2000);
        input.setSaleBonusPayoutMonths(Set.of(1, 4, 7, 10));

        when(settingsRepo.getSettings()).thenReturn(existing);
        when(settingsRepo.save(any(StatSettings.class))).thenAnswer(i -> i.getArgument(0));

        StatSettingsDTO result = statSettingsService.updateSettings(input);

        ArgumentCaptor<StatSettings> captor = ArgumentCaptor.forClass(StatSettings.class);
        verify(settingsRepo).save(captor.capture());
        assertArrayEquals(existing.getEmailFooterLogo(), captor.getValue().getEmailFooterLogo());
        assertEquals("image/png", captor.getValue().getEmailFooterLogoContentType());
        assertTrue(result.isHasEmailFooterLogo());
    }

    @Test
    void updateSettings_persistsFooterAndContactFields() {
        StatSettingsDTO input = new StatSettingsDTO();
        input.setBonusThreshold(1000);
        input.setSaleBonusPayoutMonths(Set.of(1, 4, 7, 10));
        input.setFooterCompanyName("ClinicManager Maria Kowalska");
        input.setFooterAddress("ul. Testowa 12/3");
        input.setFooterPostalCode("61-001");
        input.setFooterCity("Lublin");
        input.setCompanyPhone("+48 61 000 00 00");
        input.setCompanyWebsite("https://clinic.example.com");
        input.setFacebookUrl("https://facebook.com/clinicmanager");
        input.setInstagramUrl("https://instagram.com/clinicmanager");
        input.setBooksyUrl("https://booksy.com/clinicmanager");

        when(settingsRepo.getSettings()).thenReturn(defaultSettings());
        when(settingsRepo.save(any(StatSettings.class))).thenAnswer(i -> i.getArgument(0));

        StatSettingsDTO result = statSettingsService.updateSettings(input);

        ArgumentCaptor<StatSettings> captor = ArgumentCaptor.forClass(StatSettings.class);
        verify(settingsRepo).save(captor.capture());
        StatSettings saved = captor.getValue();
        assertEquals("ClinicManager Maria Kowalska", saved.getFooterCompanyName());
        assertEquals("ul. Testowa 12/3", saved.getFooterAddress());
        assertEquals("61-001", saved.getFooterPostalCode());
        assertEquals("Lublin", saved.getFooterCity());
        assertEquals("+48 61 000 00 00", saved.getCompanyPhone());
        assertEquals("https://clinic.example.com", saved.getCompanyWebsite());
        assertEquals("https://booksy.com/clinicmanager", saved.getBooksyUrl());
        assertEquals("https://instagram.com/clinicmanager", result.getInstagramUrl());
    }
}

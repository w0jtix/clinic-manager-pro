package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.CompanyLogoDTO;
import com.clinic.clinicmanager.DTO.StatSettingsDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.exceptions.GlobalExceptionHandler;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.service.StatSettingsService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.mock.web.MockMultipartFile;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatSettingsController.class)
@Import({WebSecurityConfig.class, GlobalExceptionHandler.class})
class StatSettingsControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean StatSettingsService settingsService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private StatSettingsDTO buildSettingsDTO() {
        StatSettingsDTO dto = new StatSettingsDTO();
        dto.setId(1L);
        dto.setBonusThreshold(1000);
        dto.setServicesRevenueGoal(3000);
        dto.setProductsRevenueGoal(500);
        dto.setSaleBonusPayoutMonths(Set.of(1, 4, 7, 10));
        return dto;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSettings_shouldReturn200_whenAdmin() throws Exception {
        when(settingsService.getSettings()).thenReturn(buildSettingsDTO());

        mockMvc.perform(get("/api/stat-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bonusThreshold").value(1000));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getSettings_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/stat-settings"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSettings_shouldReturn200_whenAdmin() throws Exception {
        StatSettingsDTO input = buildSettingsDTO();
        when(settingsService.updateSettings(any(StatSettingsDTO.class))).thenReturn(input);

        mockMvc.perform(put("/api/stat-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bonusThreshold").value(1000));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateSettings_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(put("/api/stat-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildSettingsDTO())))
                .andExpect(status().isForbidden());
    }

    private MockMultipartFile logoFile() {
        return new MockMultipartFile("file", "logo.png", "image/png", new byte[]{1, 2, 3});
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadLogo_shouldReturn204_whenAdmin() throws Exception {
        doNothing().when(settingsService).uploadCompanyLogo(any());

        mockMvc.perform(multipart("/api/stat-settings/logo").file(logoFile()))
                .andExpect(status().isNoContent());

        verify(settingsService).uploadCompanyLogo(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadLogo_shouldReturn400_whenFileIsRejected() throws Exception {
        doThrow(new ValidationException("Dozwolony format to PNG."))
                .when(settingsService).uploadCompanyLogo(any());

        mockMvc.perform(multipart("/api/stat-settings/logo").file(logoFile()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Dozwolony format to PNG."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void uploadLogo_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(multipart("/api/stat-settings/logo").file(logoFile()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getLogo_shouldReturnBytesWithContentType_whenLogoIsSet() throws Exception {
        byte[] content = new byte[]{1, 2, 3};
        when(settingsService.getCompanyLogo()).thenReturn(new CompanyLogoDTO(content, "image/png"));

        mockMvc.perform(get("/api/stat-settings/logo"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(content));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getLogo_shouldReturn404_whenLogoIsNotSet() throws Exception {
        when(settingsService.getCompanyLogo()).thenThrow(new ResourceNotFoundException("Company logo is not set."));

        mockMvc.perform(get("/api/stat-settings/logo"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteLogo_shouldReturn204_whenAdmin() throws Exception {
        doNothing().when(settingsService).deleteCompanyLogo();

        mockMvc.perform(delete("/api/stat-settings/logo"))
                .andExpect(status().isNoContent());

        verify(settingsService).deleteCompanyLogo();
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteLogo_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(delete("/api/stat-settings/logo"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSettings_shouldExposeLogoFlagOnly_withoutBytes() throws Exception {
        StatSettingsDTO dto = buildSettingsDTO();
        dto.setHasCompanyLogo(true);
        when(settingsService.getSettings()).thenReturn(dto);

        mockMvc.perform(get("/api/stat-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCompanyLogo").value(true))
                .andExpect(jsonPath("$.companyLogo").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void uploadEmailFooterLogo_shouldReturn204_whenAdmin() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "footer.png", "image/png", new byte[]{1, 2, 3});
        doNothing().when(settingsService).uploadEmailFooterLogo(any());

        mockMvc.perform(multipart("/api/stat-settings/email-footer-logo").file(file))
                .andExpect(status().isNoContent());

        verify(settingsService).uploadEmailFooterLogo(any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void uploadEmailFooterLogo_shouldReturn403_whenUserRole() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "footer.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/stat-settings/email-footer-logo").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getEmailFooterLogo_shouldReturnBytesWithContentType_whenLogoIsSet() throws Exception {
        byte[] content = new byte[]{1, 2, 3};
        when(settingsService.getEmailFooterLogo()).thenReturn(new CompanyLogoDTO(content, "image/png"));

        mockMvc.perform(get("/api/stat-settings/email-footer-logo"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(content));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getEmailFooterLogo_shouldReturn404_whenLogoIsNotSet() throws Exception {
        when(settingsService.getEmailFooterLogo())
                .thenThrow(new ResourceNotFoundException("Email footer logo is not set."));

        mockMvc.perform(get("/api/stat-settings/email-footer-logo"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteEmailFooterLogo_shouldReturn204_whenAdmin() throws Exception {
        doNothing().when(settingsService).deleteEmailFooterLogo();

        mockMvc.perform(delete("/api/stat-settings/email-footer-logo"))
                .andExpect(status().isNoContent());

        verify(settingsService).deleteEmailFooterLogo();
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteEmailFooterLogo_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(delete("/api/stat-settings/email-footer-logo"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSettings_shouldExposeEmailFooterLogoFlagOnly_withoutBytes() throws Exception {
        StatSettingsDTO dto = buildSettingsDTO();
        dto.setHasEmailFooterLogo(true);
        when(settingsService.getSettings()).thenReturn(dto);

        mockMvc.perform(get("/api/stat-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasEmailFooterLogo").value(true))
                .andExpect(jsonPath("$.emailFooterLogo").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSettings_shouldAcceptFooterFields() throws Exception {
        StatSettingsDTO dto = buildSettingsDTO();
        dto.setFooterCompanyName("ClinicManager Maria Kowalska");
        dto.setFooterCity("Lublin");
        dto.setCompanyPhone("+48 61 000 00 00");
        when(settingsService.updateSettings(any(StatSettingsDTO.class))).thenReturn(dto);

        mockMvc.perform(put("/api/stat-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.footerCompanyName").value("ClinicManager Maria Kowalska"))
                .andExpect(jsonPath("$.footerCity").value("Lublin"))
                .andExpect(jsonPath("$.companyPhone").value("+48 61 000 00 00"));
    }
}

package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.AppSettingsDTO;
import com.clinic.clinicmanager.DTO.DiscountSettingsDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.service.AppSettingsService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AppSettingsController.class)
@Import(WebSecurityConfig.class)
class AppSettingsControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AppSettingsService appSettingsService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSettings_shouldReturn200_whenAdmin() throws Exception {
        when(appSettingsService.getSettings()).thenReturn(new AppSettingsDTO());

        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getSettings_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isForbidden());
    }

    //visit creation discounts display
    @Test
    @WithMockUser(roles = "USER")
    void getDiscountSettings_shouldReturn200_whenUserRole() throws Exception {
        when(appSettingsService.getDiscountSettings()).thenReturn(new DiscountSettingsDTO());

        mockMvc.perform(get("/api/settings/discounts"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSettings_shouldReturn200_whenAdmin() throws Exception {
        when(appSettingsService.updateSettings(any())).thenReturn(new AppSettingsDTO());

        mockMvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AppSettingsDTO())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateSettings_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(put("/api/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AppSettingsDTO())))
                .andExpect(status().isForbidden());
    }
}

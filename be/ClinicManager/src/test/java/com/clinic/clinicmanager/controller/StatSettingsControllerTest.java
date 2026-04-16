package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.StatSettingsDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
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

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatSettingsController.class)
@Import(WebSecurityConfig.class)
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
}

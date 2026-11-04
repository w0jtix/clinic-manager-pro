package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.CompanyLogoDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.GlobalExceptionHandler;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.service.StatSettingsService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PublicAssetController.class)
@Import({WebSecurityConfig.class, GlobalExceptionHandler.class, AuthEntryPointJwt.class})
class PublicAssetControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean StatSettingsService settingsService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    @Test
    void footerLogo_shouldReturnBytes_withoutAuthentication() throws Exception {
        byte[] content = "png-bytes".getBytes(StandardCharsets.UTF_8);
        when(settingsService.getEmailFooterLogo()).thenReturn(new CompanyLogoDTO(content, "image/png"));

        mockMvc.perform(get("/api/public/email-footer-logo"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(content));
    }

    @Test
    void footerLogo_shouldBePubliclyCacheable() throws Exception {
        when(settingsService.getEmailFooterLogo())
                .thenReturn(new CompanyLogoDTO("png-bytes".getBytes(StandardCharsets.UTF_8), "image/png"));

        mockMvc.perform(get("/api/public/email-footer-logo"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=604800, public"));
    }

    @Test
    void footerLogo_shouldReturn404_whenLogoIsNotSet() throws Exception {
        when(settingsService.getEmailFooterLogo())
                .thenThrow(new ResourceNotFoundException("Email footer logo is not set."));

        mockMvc.perform(get("/api/public/email-footer-logo"))
                .andExpect(status().isNotFound());
    }

    @Test
    void publicPath_shouldRejectNonGetMethods_whenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/public/email-footer-logo"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/public/email-footer-logo"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/public/email-footer-logo"))
                .andExpect(status().isUnauthorized());
    }
}

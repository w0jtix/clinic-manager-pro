package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.TrustedDeviceDTO;
import com.clinic.clinicmanager.DTO.response.MfaBackupCodesResponse;
import com.clinic.clinicmanager.DTO.response.MfaSetupResponse;
import com.clinic.clinicmanager.DTO.response.MfaStatusResponse;
import com.clinic.clinicmanager.config.security.MfaDeviceCookie;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.service.MfaService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MfaController.class)
@Import({WebSecurityConfig.class, MfaDeviceCookie.class})
class MfaControllerTest {

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock businessClock() {
            return Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Europe/Warsaw"));
        }
    }

    @Autowired MockMvc mockMvc;

    @MockBean MfaService mfaService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private static UserDetailsImpl userWith(Long id, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());
        return new UserDetailsImpl(id, "owner", "pass", null, null, authorities);
    }

    private static UsernamePasswordAuthenticationToken masterInCompanyMode() {
        UserDetailsImpl principal = userWith(7L, "MASTER");
        return new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_MASTER"), new SimpleGrantedAuthority("ROLE_COMPANY")));
    }

    @Test
    void getStatus_shouldPassCurrentDeviceCookieAndReturnStatus() throws Exception {
        TrustedDeviceDTO device = new TrustedDeviceDTO(5L, "Chrome · Windows", LocalDateTime.of(2026, 10, 1, 9, 0),
                LocalDateTime.of(2026, 10, 16, 8, 30), LocalDateTime.of(2027, 3, 30, 9, 0), "10.0.0.5", true);
        when(mfaService.getStatus("device-token"))
                .thenReturn(new MfaStatusResponse(true, LocalDateTime.of(2026, 10, 1, 9, 0), 8, List.of(device)));

        mockMvc.perform(get("/api/user/me/mfa")
                        .cookie(new Cookie("pc_mfa_7", "device-token"))
                        .with(authentication(masterInCompanyMode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.enabledAt").value("2026-10-01T09:00:00"))
                .andExpect(jsonPath("$.remainingBackupCodes").value(8))
                .andExpect(jsonPath("$.trustedDevices[0].id").value(5))
                .andExpect(jsonPath("$.trustedDevices[0].label").value("Chrome · Windows"))
                .andExpect(jsonPath("$.trustedDevices[0].lastIp").value("10.0.0.5"))
                .andExpect(jsonPath("$.trustedDevices[0].current").value(true));
    }

    @Test
    void getStatus_shouldReturn403_whenMasterWithoutCompanyMode() throws Exception {
        mockMvc.perform(get("/api/user/me/mfa")
                        .with(user(userWith(7L, "MASTER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mfaService);
    }

    @Test
    void getStatus_shouldReturn403_whenPlainUser() throws Exception {
        mockMvc.perform(get("/api/user/me/mfa")
                        .with(user(userWith(2L, "USER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mfaService);
    }

    @Test
    void setup_shouldReturnSecretAndQr_whenAdmin() throws Exception {
        when(mfaService.setup()).thenReturn(new MfaSetupResponse("JBSWY3DPEHPK3PXP",
                "otpauth://totp/owner?secret=JBSWY3DPEHPK3PXP&issuer=ClinicManager", "data:image/png;base64,AAA"));

        mockMvc.perform(post("/api/user/me/mfa/setup")
                        .with(user(userWith(1L, "ADMIN", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("JBSWY3DPEHPK3PXP"))
                .andExpect(jsonPath("$.otpauthUri").value("otpauth://totp/owner?secret=JBSWY3DPEHPK3PXP&issuer=ClinicManager"))
                .andExpect(jsonPath("$.qrDataUri").value("data:image/png;base64,AAA"));
    }

    @Test
    void enable_shouldReturnBackupCodes() throws Exception {
        when(mfaService.enable("123456")).thenReturn(new MfaBackupCodesResponse(List.of("abcd-2345", "efgh-6789")));

        mockMvc.perform(post("/api/user/me/mfa/enable")
                        .with(authentication(masterInCompanyMode()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backupCodes[0]").value("abcd-2345"))
                .andExpect(jsonPath("$.backupCodes.length()").value(2));
    }

    @Test
    void enable_shouldReturn400_whenCodeMissing() throws Exception {
        mockMvc.perform(post("/api/user/me/mfa/enable")
                        .with(authentication(masterInCompanyMode()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(mfaService);
    }

    @Test
    void disable_shouldReturnMessage() throws Exception {
        mockMvc.perform(post("/api/user/me/mfa/disable")
                        .with(authentication(masterInCompanyMode()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Weryfikacja dwuetapowa została wyłączona."));
        verify(mfaService).disable("123456");
    }

    @Test
    void backupCodes_shouldReturn409_whenMfaNotEnabled() throws Exception {
        when(mfaService.regenerateBackupCodes("123456"))
                .thenThrow(new ConflictException("Weryfikacja dwuetapowa nie jest włączona."));

        mockMvc.perform(post("/api/user/me/mfa/backup-codes")
                        .with(authentication(masterInCompanyMode()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Weryfikacja dwuetapowa nie jest włączona."));
    }

    @Test
    void confirmBackupCodes_shouldReturnMessage() throws Exception {
        mockMvc.perform(post("/api/user/me/mfa/backup-codes/confirm")
                        .with(authentication(masterInCompanyMode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Zapisano nowe kody zapasowe."));
        verify(mfaService).confirmBackupCodes();
    }

    @Test
    void confirmBackupCodes_shouldReturn409_whenPendingCodesExpired() throws Exception {
        doThrow(new ConflictException("Nowe kody wygasły. Wygeneruj je ponownie."))
                .when(mfaService).confirmBackupCodes();

        mockMvc.perform(post("/api/user/me/mfa/backup-codes/confirm")
                        .with(authentication(masterInCompanyMode())))
                .andExpect(status().isConflict())
                .andExpect(content().string("Nowe kody wygasły. Wygeneruj je ponownie."));
    }

    @Test
    void confirmBackupCodes_shouldReturn403_whenMasterWithoutCompanyMode() throws Exception {
        mockMvc.perform(post("/api/user/me/mfa/backup-codes/confirm")
                        .with(user(userWith(7L, "MASTER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mfaService);
    }

    @Test
    void revokeTrustedDevice_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/user/me/mfa/trusted-devices/5")
                        .with(authentication(masterInCompanyMode())))
                .andExpect(status().isNoContent());
        verify(mfaService).revokeTrustedDevice(5L);
    }

    @Test
    void revokeTrustedDevice_shouldReturn404_whenNotOwned() throws Exception {
        doThrow(new ResourceNotFoundException("Nie znaleziono zapamiętanego urządzenia."))
                .when(mfaService).revokeTrustedDevice(99L);

        mockMvc.perform(delete("/api/user/me/mfa/trusted-devices/99")
                        .with(authentication(masterInCompanyMode())))
                .andExpect(status().isNotFound());
    }

    @Test
    void revokeAllTrustedDevices_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/user/me/mfa/trusted-devices")
                        .with(authentication(masterInCompanyMode())))
                .andExpect(status().isNoContent());
        verify(mfaService).revokeAllTrustedDevices();
    }
}

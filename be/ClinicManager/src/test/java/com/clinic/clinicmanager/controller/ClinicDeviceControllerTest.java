package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.ClinicDeviceDTO;
import com.clinic.clinicmanager.DTO.RegistrationCodeDTO;
import com.clinic.clinicmanager.DTO.request.device.CreateRegistrationCodeRequest;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.service.ClinicDeviceService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClinicDeviceController.class)
@Import(WebSecurityConfig.class)
class ClinicDeviceControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean ClinicDeviceService clinicDeviceService;
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

    private static ClinicDeviceDTO deviceDto() {
        return new ClinicDeviceDTO(5L, "Recepcja", "Safari · iPad", "10.0.0.9", LocalDateTime.of(2026, 10, 1, 9, 0),
                "admin", LocalDateTime.of(2026, 10, 18, 8, 30), "anna", "ACTIVE", null, null);
    }

    private static RegistrationCodeDTO codeDto() {
        return new RegistrationCodeDTO(40L, "ABC-234", "Recepcja", null, null, "admin",
                LocalDateTime.of(2026, 10, 19, 10, 0), LocalDateTime.of(2026, 10, 19, 10, 15));
    }

    @Test
    void getDevices_shouldReturnContract_forAdmin() throws Exception {
        when(clinicDeviceService.getDevices(false)).thenReturn(List.of(deviceDto()));

        mockMvc.perform(get("/api/devices").with(user(userWith(1L, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].name").value("Recepcja"))
                .andExpect(jsonPath("$[0].label").value("Safari · iPad"))
                .andExpect(jsonPath("$[0].lastIp").value("10.0.0.9"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-10-01T09:00:00"))
                .andExpect(jsonPath("$[0].registeredBy").value("admin"))
                .andExpect(jsonPath("$[0].lastSeenAt").value("2026-10-18T08:30:00"))
                .andExpect(jsonPath("$[0].lastSeenUsername").value("anna"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].replacedByName").doesNotExist())
                .andExpect(jsonPath("$[0].revokedAt").doesNotExist());
    }

    @Test
    void getDevices_shouldPassIncludeRevoked_forMasterInCompanyMode() throws Exception {
        when(clinicDeviceService.getDevices(true)).thenReturn(List.of());

        mockMvc.perform(get("/api/devices").param("includeRevoked", "true").with(authentication(masterInCompanyMode())))
                .andExpect(status().isOk());

        verify(clinicDeviceService).getDevices(true);
    }

    @Test
    void getDevices_shouldReturn403_whenMasterWithoutCompanyMode() throws Exception {
        mockMvc.perform(get("/api/devices").with(user(userWith(7L, "MASTER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clinicDeviceService);
    }

    @Test
    void getDevices_shouldReturn403_whenPlainUser() throws Exception {
        mockMvc.perform(get("/api/devices").with(user(userWith(2L, "USER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clinicDeviceService);
    }

    @Test
    void getCodes_shouldReturnContract() throws Exception {
        when(clinicDeviceService.getActiveCodes()).thenReturn(List.of(codeDto()));

        mockMvc.perform(get("/api/devices/codes").with(user(userWith(1L, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(40))
                .andExpect(jsonPath("$[0].code").value("ABC-234"))
                .andExpect(jsonPath("$[0].name").value("Recepcja"))
                .andExpect(jsonPath("$[0].replaceDeviceId").doesNotExist())
                .andExpect(jsonPath("$[0].replaceDeviceName").doesNotExist())
                .andExpect(jsonPath("$[0].createdBy").value("admin"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-10-19T10:00:00"))
                .andExpect(jsonPath("$[0].expiresAt").value("2026-10-19T10:15:00"));
    }

    @Test
    void createCode_shouldReturn201() throws Exception {
        when(clinicDeviceService.createCode(any(CreateRegistrationCodeRequest.class))).thenReturn(codeDto());

        mockMvc.perform(post("/api/devices/codes")
                        .with(user(userWith(1L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Recepcja\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ABC-234"));

        verify(clinicDeviceService).createCode(argThat(request ->
                "Recepcja".equals(request.getName()) && request.getReplaceDeviceId() == null));
    }

    @Test
    void createCode_shouldReturn400_whenTargetMissing() throws Exception {
        when(clinicDeviceService.createCode(any(CreateRegistrationCodeRequest.class)))
                .thenThrow(new ValidationException("Podaj nazwę urządzenia albo wybierz urządzenie do zastąpienia."));

        mockMvc.perform(post("/api/devices/codes")
                        .with(user(userWith(1L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Podaj nazwę urządzenia albo wybierz urządzenie do zastąpienia."));
    }

    @Test
    void createCode_shouldReturn409_whenActiveCodeExists() throws Exception {
        when(clinicDeviceService.createCode(any(CreateRegistrationCodeRequest.class)))
                .thenThrow(new ConflictException("Istnieje już aktywny kod rejestracji. Użyj go albo anuluj, zanim wygenerujesz nowy."));

        mockMvc.perform(post("/api/devices/codes")
                        .with(user(userWith(1L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Gabinet 2\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Istnieje już aktywny kod rejestracji. Użyj go albo anuluj, zanim wygenerujesz nowy."));
    }

    @Test
    void createCode_shouldReturn409_whenNameTaken() throws Exception {
        when(clinicDeviceService.createCode(any(CreateRegistrationCodeRequest.class)))
                .thenThrow(new ConflictException("Urządzenie o tej nazwie już istnieje."));

        mockMvc.perform(post("/api/devices/codes")
                        .with(user(userWith(1L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Recepcja\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Urządzenie o tej nazwie już istnieje."));
    }

    @Test
    void createCode_shouldReturn403_whenPlainUser() throws Exception {
        mockMvc.perform(post("/api/devices/codes")
                        .with(user(userWith(2L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Recepcja\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clinicDeviceService);
    }

    @Test
    void cancelCode_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/devices/codes/40").with(user(userWith(1L, "ADMIN"))))
                .andExpect(status().isNoContent());
        verify(clinicDeviceService).cancelCode(40L);
    }

    @Test
    void cancelCode_shouldReturn404_whenInactive() throws Exception {
        doThrow(new ResourceNotFoundException("Nie znaleziono kodu.")).when(clinicDeviceService).cancelCode(40L);

        mockMvc.perform(delete("/api/devices/codes/40").with(user(userWith(1L, "ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Nie znaleziono kodu."));
    }

    @Test
    void renameDevice_shouldReturn200() throws Exception {
        when(clinicDeviceService.renameDevice(5L, "Recepcja")).thenReturn(deviceDto());

        mockMvc.perform(put("/api/devices/5")
                        .with(user(userWith(1L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Recepcja\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Recepcja"));
    }

    @Test
    void revokeDevice_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/devices/5").with(user(userWith(1L, "ADMIN"))))
                .andExpect(status().isNoContent());
        verify(clinicDeviceService).revokeDevice(5L);
    }

    @Test
    void revokeDevice_shouldReturn404_whenNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Nie znaleziono urządzenia.")).when(clinicDeviceService).revokeDevice(9L);

        mockMvc.perform(delete("/api/devices/9").with(user(userWith(1L, "ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Nie znaleziono urządzenia."));
    }

    @Test
    void revokeDevice_shouldReturn403_whenPlainUser() throws Exception {
        mockMvc.perform(delete("/api/devices/5").with(user(userWith(2L, "USER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clinicDeviceService);
    }

    @Test
    void deleteDevice_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/devices/5/permanent").with(authentication(masterInCompanyMode())))
                .andExpect(status().isNoContent());
        verify(clinicDeviceService).deleteDevice(5L);
        verify(clinicDeviceService, never()).revokeDevice(any());
    }

    @Test
    void deleteDevice_shouldReturn409_whenNotRevoked() throws Exception {
        doThrow(new ConflictException("Najpierw unieważnij urządzenie.")).when(clinicDeviceService).deleteDevice(5L);

        mockMvc.perform(delete("/api/devices/5/permanent").with(user(userWith(1L, "ADMIN"))))
                .andExpect(status().isConflict())
                .andExpect(content().string("Najpierw unieważnij urządzenie."));
    }

    @Test
    void deleteDevice_shouldReturn404_whenNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Nie znaleziono urządzenia.")).when(clinicDeviceService).deleteDevice(9L);

        mockMvc.perform(delete("/api/devices/9/permanent").with(user(userWith(1L, "ADMIN"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteDevice_shouldReturn403_whenMasterWithoutCompanyMode() throws Exception {
        mockMvc.perform(delete("/api/devices/5/permanent").with(user(userWith(7L, "MASTER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clinicDeviceService);
    }

    @Test
    void deleteDevice_shouldReturn403_whenPlainUser() throws Exception {
        mockMvc.perform(delete("/api/devices/5/permanent").with(user(userWith(2L, "USER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(clinicDeviceService);
    }
}

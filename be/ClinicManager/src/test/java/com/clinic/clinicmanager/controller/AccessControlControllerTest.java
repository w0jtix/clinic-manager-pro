package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.AccessControlDTO;
import com.clinic.clinicmanager.DTO.WorkingHoursDTO;
import com.clinic.clinicmanager.DTO.request.accesscontrol.GateRequest;
import com.clinic.clinicmanager.DTO.request.accesscontrol.PauseRequest;
import com.clinic.clinicmanager.DTO.request.accesscontrol.WorkingHoursUpdateRequest;
import com.clinic.clinicmanager.exceptions.StepUpException;
import com.clinic.clinicmanager.exceptions.TooManyAttemptsException;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.constants.GatePauseDuration;
import com.clinic.clinicmanager.service.AccessControlService;
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
import java.time.LocalTime;
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

@WebMvcTest(AccessControlController.class)
@Import(WebSecurityConfig.class)
class AccessControlControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AccessControlService accessControlService;
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

    private static AccessControlDTO dto() {
        return new AccessControlDTO(true, false, LocalDateTime.of(2026, 10, 20, 23, 59, 59), "admin", true,
                List.of(new WorkingHoursDTO(1, true, LocalTime.of(8, 0), LocalTime.of(16, 0)),
                        new WorkingHoursDTO(2, false, null, null)),
                2L);
    }

    @Test
    void getSettings_shouldReturnContract_forAdmin() throws Exception {
        when(accessControlService.getSettings()).thenReturn(dto());

        mockMvc.perform(get("/api/access-control").with(user(userWith(1L, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gateEnabled").value(true))
                .andExpect(jsonPath("$.gateActive").value(false))
                .andExpect(jsonPath("$.pausedUntil").value("2026-10-20T23:59:59"))
                .andExpect(jsonPath("$.pausedBy").value("admin"))
                .andExpect(jsonPath("$.hoursEnabled").value(true))
                .andExpect(jsonPath("$.hours[0].dayOfWeek").value(1))
                .andExpect(jsonPath("$.hours[0].open").value(true))
                .andExpect(jsonPath("$.hours[0].startTime").value("08:00"))
                .andExpect(jsonPath("$.hours[0].endTime").value("16:00"))
                .andExpect(jsonPath("$.hours[1].startTime").doesNotExist())
                .andExpect(jsonPath("$.activeDeviceCount").value(2));
    }

    @Test
    void getSettings_shouldReturn403_forMasterInCompanyMode() throws Exception {
        UserDetailsImpl principal = userWith(7L, "MASTER");
        mockMvc.perform(get("/api/access-control").with(authentication(new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_MASTER"), new SimpleGrantedAuthority("ROLE_COMPANY"))))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accessControlService);
    }

    @Test
    void getSettings_shouldReturn403_forUser() throws Exception {
        mockMvc.perform(get("/api/access-control").with(user(userWith(2L, "USER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(accessControlService);
    }

    @Test
    void setGate_shouldPassFlag() throws Exception {
        when(accessControlService.setGateEnabled(any(GateRequest.class))).thenReturn(dto());

        mockMvc.perform(put("/api/access-control/gate")
                        .with(user(userWith(1L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gateEnabled").value(true));

        verify(accessControlService).setGateEnabled(argThat(request -> request.getEnabled() && request.getCode() == null));
    }

    @Test
    void setGate_shouldPassStepUpCredentials_whenDisabling() throws Exception {
        when(accessControlService.setGateEnabled(any(GateRequest.class))).thenReturn(dto());

        mockMvc.perform(put("/api/access-control/gate")
                        .with(user(userWith(1L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"code\":\"123456\",\"password\":\"secret\"}"))
                .andExpect(status().isOk());

        verify(accessControlService).setGateEnabled(argThat(request -> !request.getEnabled()
                && "123456".equals(request.getCode()) && "secret".equals(request.getPassword())));
    }

    @Test
    void setGate_shouldReturn400StepUpRequired_whenDisablingWithoutCredentials() throws Exception {
        when(accessControlService.setGateEnabled(any(GateRequest.class)))
                .thenThrow(new StepUpException(StepUpException.REQUIRED, "Podaj hasło do konta."));

        mockMvc.perform(put("/api/access-control/gate")
                        .with(user(userWith(1L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Podaj hasło do konta."));
    }

    @Test
    void setGate_shouldReturn400StepUpInvalid_whenCodeWrong() throws Exception {
        when(accessControlService.setGateEnabled(any(GateRequest.class)))
                .thenThrow(new StepUpException(StepUpException.INVALID, "Nieprawidłowy kod."));

        mockMvc.perform(put("/api/access-control/gate")
                        .with(user(userWith(1L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STEP_UP_INVALID"))
                .andExpect(jsonPath("$.message").value("Nieprawidłowy kod."));
    }

    @Test
    void setGate_shouldReturn409_whenEnablingWithoutDevices() throws Exception {
        when(accessControlService.setGateEnabled(any(GateRequest.class)))
                .thenThrow(new ConflictException("Nie można włączyć bramki - brak aktywnych urządzeń gabinetu."));

        mockMvc.perform(put("/api/access-control/gate")
                        .with(user(userWith(1L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Nie można włączyć bramki - brak aktywnych urządzeń gabinetu."));
    }

    @Test
    void pause_shouldReturn429_whenStepUpBlocked() throws Exception {
        when(accessControlService.pause(any(PauseRequest.class)))
                .thenThrow(new TooManyAttemptsException("Zbyt wiele nieudanych prób. Spróbuj ponownie za 15 min."));

        mockMvc.perform(post("/api/access-control/pause")
                        .with(user(userWith(1L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duration\":\"HOUR_1\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string("Zbyt wiele nieudanych prób. Spróbuj ponownie za 15 min."));
    }

    @Test
    void pause_shouldPassDurationAndCredentials() throws Exception {
        when(accessControlService.pause(any(PauseRequest.class))).thenReturn(dto());

        mockMvc.perform(post("/api/access-control/pause")
                        .with(user(userWith(1L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duration\":\"END_OF_DAY\",\"code\":\"123456\"}"))
                .andExpect(status().isOk());

        verify(accessControlService).pause(argThat(request -> request.getDuration() == GatePauseDuration.END_OF_DAY
                && "123456".equals(request.getCode())));
    }

    @Test
    void pause_shouldReturn409_whenGateDisabled() throws Exception {
        when(accessControlService.pause(any(PauseRequest.class))).thenThrow(new ConflictException("Bramka urządzeń jest wyłączona."));

        mockMvc.perform(post("/api/access-control/pause")
                        .with(user(userWith(1L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"duration\":\"HOUR_1\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Bramka urządzeń jest wyłączona."));
    }

    @Test
    void resume_shouldReturn200() throws Exception {
        when(accessControlService.resume()).thenReturn(dto());

        mockMvc.perform(delete("/api/access-control/pause").with(user(userWith(1L, "ADMIN"))))
                .andExpect(status().isOk());

        verify(accessControlService).resume();
    }

    @Test
    void updateHours_shouldParseHourMinuteTimes() throws Exception {
        when(accessControlService.updateHours(any(WorkingHoursUpdateRequest.class))).thenReturn(dto());

        mockMvc.perform(put("/api/access-control/hours")
                        .with(user(userWith(1L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hoursEnabled\":true,\"hours\":[{\"dayOfWeek\":1,\"open\":true,\"startTime\":\"08:00\",\"endTime\":\"16:30\"}]}"))
                .andExpect(status().isOk());

        verify(accessControlService).updateHours(argThat(request -> request.getHoursEnabled()
                && request.getHours().get(0).isOpen()
                && LocalTime.of(16, 30).equals(request.getHours().get(0).getEndTime())));
    }

    @Test
    void updateHours_shouldReturn400WithPolishMessage() throws Exception {
        when(accessControlService.updateHours(any(WorkingHoursUpdateRequest.class)))
                .thenThrow(new ValidationException("Godzina rozpoczęcia musi być wcześniejsza niż zakończenia."));

        mockMvc.perform(put("/api/access-control/hours")
                        .with(user(userWith(1L, "ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hoursEnabled\":true,\"hours\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Godzina rozpoczęcia musi być wcześniejsza niż zakończenia."));
    }
}

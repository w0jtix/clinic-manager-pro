package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.AccessControlDTO;
import com.clinic.clinicmanager.DTO.WorkingHoursDTO;
import com.clinic.clinicmanager.DTO.request.accesscontrol.GateRequest;
import com.clinic.clinicmanager.DTO.request.accesscontrol.PauseRequest;
import com.clinic.clinicmanager.DTO.request.accesscontrol.WorkingHoursUpdateRequest;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.StepUpException;
import com.clinic.clinicmanager.exceptions.TooManyAttemptsException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.AccessControlSettings;
import com.clinic.clinicmanager.model.ClinicWorkingHours;
import com.clinic.clinicmanager.model.constants.GatePauseDuration;
import com.clinic.clinicmanager.model.constants.TrustedDeviceKind;
import com.clinic.clinicmanager.repo.AccessControlSettingsRepo;
import com.clinic.clinicmanager.repo.ClinicWorkingHoursRepo;
import com.clinic.clinicmanager.repo.TrustedDeviceRepo;
import com.clinic.clinicmanager.service.impl.AccessControlServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessControlServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 10, 30);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock AccessControlSettingsRepo settingsRepo;
    @Mock ClinicWorkingHoursRepo workingHoursRepo;
    @Mock TrustedDeviceRepo trustedDeviceRepo;
    @Mock AuditLogService auditLogService;
    @Mock StepUpVerificationService stepUpVerificationService;

    private AccessControlServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AccessControlServiceImpl(settingsRepo, workingHoursRepo, trustedDeviceRepo, auditLogService,
                stepUpVerificationService, CLOCK);
        UserDetailsImpl principal = new UserDetailsImpl(1L, "admin", "pass", null, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static AccessControlSettings settings(boolean gateEnabled, LocalDateTime pausedUntil, boolean hoursEnabled) {
        return AccessControlSettings.builder()
                .id(1L)
                .gateEnabled(gateEnabled)
                .pausedUntil(pausedUntil)
                .hoursEnabled(hoursEnabled)
                .build();
    }

    private static List<ClinicWorkingHours> closedWeek() {
        return IntStream.rangeClosed(1, 7)
                .mapToObj(day -> ClinicWorkingHours.builder().id((long) day).dayOfWeek(day).open(false).build())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static List<WorkingHoursDTO> requestWeek() {
        return IntStream.rangeClosed(1, 7)
                .mapToObj(day -> new WorkingHoursDTO(day, false, null, null))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void stubSettings(AccessControlSettings settings) {
        when(settingsRepo.findById(1L)).thenReturn(Optional.of(settings));
    }

    @Test
    void isGateActive_shouldBeFalseByDefault() {
        stubSettings(settings(false, null, false));

        assertFalse(service.isGateActive());
    }

    @Test
    void isGateActive_shouldBeTrue_whenEnabledAndNotPaused() {
        stubSettings(settings(true, null, false));

        assertTrue(service.isGateActive());
    }

    @Test
    void isGateActive_shouldBeFalse_whilePaused() {
        stubSettings(settings(true, NOW.plusMinutes(5), false));

        assertFalse(service.isGateActive());
    }

    @Test
    void isGateActive_shouldBeTrue_afterPauseExpired() {
        stubSettings(settings(true, NOW.minusMinutes(1), false));

        assertTrue(service.isGateActive());
    }

    @Test
    void isGateActive_shouldUseCachedSnapshot_onRepeatedCalls() {
        stubSettings(settings(true, null, false));

        service.isGateActive();
        service.isGateActive();

        verify(settingsRepo, times(1)).findById(1L);
    }

    @Test
    void isWithinWorkingHours_shouldAllow_whenHoursDisabled() {
        stubSettings(settings(true, null, false));

        assertTrue(service.isWithinWorkingHours());
    }

    @Test
    void isWithinWorkingHours_shouldCheckTodayWindowWithExclusiveEnd() {
        stubSettings(settings(true, null, true));
        List<ClinicWorkingHours> week = closedWeek();
        week.get(1).setOpen(true);
        week.get(1).setStartTime(LocalTime.of(8, 0));
        week.get(1).setEndTime(LocalTime.of(10, 30));
        when(workingHoursRepo.findAllByOrderByDayOfWeekAsc()).thenReturn(week);

        assertFalse(service.isWithinWorkingHours());
    }

    @Test
    void isWithinWorkingHours_shouldAllow_insideTodayWindow() {
        stubSettings(settings(true, null, true));
        List<ClinicWorkingHours> week = closedWeek();
        week.get(1).setOpen(true);
        week.get(1).setStartTime(LocalTime.of(8, 0));
        week.get(1).setEndTime(LocalTime.of(16, 0));
        when(workingHoursRepo.findAllByOrderByDayOfWeekAsc()).thenReturn(week);

        assertTrue(service.isWithinWorkingHours());
    }

    @Test
    void isWithinWorkingHours_shouldDeny_whenTodayClosed() {
        stubSettings(settings(true, null, true));
        when(workingHoursRepo.findAllByOrderByDayOfWeekAsc()).thenReturn(closedWeek());

        assertFalse(service.isWithinWorkingHours());
    }

    @Test
    void setGateEnabled_shouldEnableClearPauseAndAudit() {
        AccessControlSettings current = settings(false, NOW.plusHours(1), false);
        stubSettings(current);
        when(workingHoursRepo.findAllByOrderByDayOfWeekAsc()).thenReturn(closedWeek());
        when(trustedDeviceRepo.countAllowed(TrustedDeviceKind.CLINIC_DEVICE)).thenReturn(3L);

        AccessControlDTO dto = service.setGateEnabled(new GateRequest(true, null, null));

        assertTrue(dto.isGateEnabled());
        assertTrue(dto.isGateActive());
        assertNull(dto.getPausedUntil());
        assertEquals(3L, dto.getActiveDeviceCount());
        assertEquals(7, dto.getHours().size());
        assertEquals("admin", current.getUpdatedBy());
        verify(settingsRepo).save(current);
        verify(auditLogService).logSecurity("UserLoginDeviceGate-Enabled", 1L, "AccessControl", Map.of("by", "admin"));
    }

    @Test
    void setGateEnabled_shouldNotAudit_whenUnchanged() {
        stubSettings(settings(true, null, false));
        when(workingHoursRepo.findAllByOrderByDayOfWeekAsc()).thenReturn(closedWeek());

        service.setGateEnabled(new GateRequest(true, null, null));

        verify(settingsRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void setGateEnabled_shouldRejectMissingFlag() {
        assertThrows(ValidationException.class, () -> service.setGateEnabled(new GateRequest(null, null, null)));
    }

    @Test
    void pause_shouldSetUntilForOneHourAndAudit() {
        AccessControlSettings current = settings(true, null, false);
        stubSettings(current);
        when(workingHoursRepo.findAllByOrderByDayOfWeekAsc()).thenReturn(closedWeek());

        AccessControlDTO dto = service.pause(new PauseRequest(GatePauseDuration.HOUR_1, "123456", null));

        assertEquals(NOW.plusHours(1), dto.getPausedUntil());
        assertEquals("admin", dto.getPausedBy());
        assertFalse(dto.isGateActive());
        verify(auditLogService).logSecurity("UserLoginDeviceGate-Paused", 1L, "AccessControl",
                Map.of("until", NOW.plusHours(1).toString(), "duration", "HOUR_1", "by", "admin"));
    }

    @Test
    void pause_shouldPauseUntilEndOfBusinessDay() {
        stubSettings(settings(true, null, false));
        when(workingHoursRepo.findAllByOrderByDayOfWeekAsc()).thenReturn(closedWeek());

        assertEquals(LocalDateTime.of(2026, 10, 20, 23, 59, 59), service.pause(new PauseRequest(GatePauseDuration.END_OF_DAY, "123456", null)).getPausedUntil());
    }

    @Test
    void pause_shouldPauseForFourHours() {
        stubSettings(settings(true, null, false));
        when(workingHoursRepo.findAllByOrderByDayOfWeekAsc()).thenReturn(closedWeek());

        assertEquals(NOW.plusHours(4), service.pause(new PauseRequest(GatePauseDuration.HOUR_4, "123456", null)).getPausedUntil());
    }

    @Test
    void pause_shouldThrowConflict_whenGateDisabled() {
        stubSettings(settings(false, null, false));

        ConflictException ex = assertThrows(ConflictException.class, () -> service.pause(new PauseRequest(GatePauseDuration.HOUR_1, "123456", null)));

        assertEquals("Bramka urządzeń jest wyłączona.", ex.getMessage());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void pause_shouldRejectMissingDuration() {
        assertThrows(ValidationException.class, () -> service.pause(new PauseRequest(null, "123456", null)));
    }

    @Test
    void resume_shouldClearActivePauseAndAudit() {
        AccessControlSettings current = settings(true, NOW.plusHours(2), false);
        current.setPausedBy("admin");
        stubSettings(current);
        when(workingHoursRepo.findAllByOrderByDayOfWeekAsc()).thenReturn(closedWeek());

        AccessControlDTO dto = service.resume();

        assertNull(dto.getPausedUntil());
        assertNull(dto.getPausedBy());
        assertTrue(dto.isGateActive());
        verify(auditLogService).logSecurity("UserLoginDeviceGate-Resumed", 1L, "AccessControl", Map.of("by", "admin"));
    }

    @Test
    void resume_shouldNotAudit_whenNotPaused() {
        stubSettings(settings(true, null, false));
        when(workingHoursRepo.findAllByOrderByDayOfWeekAsc()).thenReturn(closedWeek());

        service.resume();

        verifyNoInteractions(auditLogService);
    }

    @Test
    void updateHours_shouldSaveWeekAndAuditOldAndNew() {
        AccessControlSettings current = settings(true, null, false);
        stubSettings(current);
        when(workingHoursRepo.findAllByOrderByDayOfWeekAsc()).thenReturn(closedWeek());
        when(workingHoursRepo.saveAll(anyCollection())).thenAnswer(invocation -> new ArrayList<>(invocation.getArgument(0)));
        List<WorkingHoursDTO> week = requestWeek();
        week.set(0, new WorkingHoursDTO(1, true, LocalTime.of(8, 0), LocalTime.of(16, 0)));

        AccessControlDTO dto = service.updateHours(new WorkingHoursUpdateRequest(true, week));

        assertTrue(dto.isHoursEnabled());
        assertTrue(current.isHoursEnabled());
        WorkingHoursDTO monday = dto.getHours().get(0);
        assertEquals(1, monday.getDayOfWeek());
        assertTrue(monday.isOpen());
        assertEquals(LocalTime.of(8, 0), monday.getStartTime());
        verify(auditLogService).logSecurity(eq("WorkingHours-Updated"), eq(1L), eq("AccessControl"), anyMap());
    }

    @Test
    void updateHours_shouldRejectStartNotBeforeEnd() {
        List<WorkingHoursDTO> week = requestWeek();
        week.set(2, new WorkingHoursDTO(3, true, LocalTime.of(16, 0), LocalTime.of(16, 0)));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.updateHours(new WorkingHoursUpdateRequest(true, week)));

        assertEquals("Godzina rozpoczęcia musi być wcześniejsza niż zakończenia.", ex.getMessage());
        verifyNoInteractions(settingsRepo, workingHoursRepo, auditLogService);
    }

    @Test
    void updateHours_shouldRejectOpenDayWithoutTimes() {
        List<WorkingHoursDTO> week = requestWeek();
        week.set(0, new WorkingHoursDTO(1, true, LocalTime.of(8, 0), null));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.updateHours(new WorkingHoursUpdateRequest(true, week)));

        assertEquals("Podaj godzinę rozpoczęcia i zakończenia dla dni otwartych.", ex.getMessage());
    }

    @Test
    void updateHours_shouldRejectIncompleteWeek() {
        List<WorkingHoursDTO> week = requestWeek();
        week.remove(6);

        assertThrows(ValidationException.class, () -> service.updateHours(new WorkingHoursUpdateRequest(true, week)));
    }

    @Test
    void updateHours_shouldRejectDuplicateDay() {
        List<WorkingHoursDTO> week = requestWeek();
        week.set(6, new WorkingHoursDTO(1, false, null, null));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.updateHours(new WorkingHoursUpdateRequest(true, week)));

        assertEquals("Nieprawidłowy dzień tygodnia.", ex.getMessage());
    }

    @Test
    void updateHours_shouldRejectMissingFlag() {
        assertThrows(ValidationException.class, () -> service.updateHours(new WorkingHoursUpdateRequest(null, requestWeek())));
    }

    @Test
    void setGateEnabled_shouldThrowConflictWithoutChange_whenNoActiveDevices() {
        AccessControlSettings current = settings(false, null, false);
        stubSettings(current);
        when(trustedDeviceRepo.countAllowed(TrustedDeviceKind.CLINIC_DEVICE)).thenReturn(0L);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.setGateEnabled(new GateRequest(true, null, null)));

        assertEquals("Nie można włączyć bramki - brak aktywnych urządzeń gabinetu.", ex.getMessage());
        assertFalse(current.isGateEnabled());
        verify(settingsRepo, never()).save(any());
        verifyNoInteractions(auditLogService, stepUpVerificationService);
    }

    @Test
    void setGateEnabled_shouldNotRequireStepUp_whenEnabling() {
        stubSettings(settings(false, null, false));
        when(trustedDeviceRepo.countAllowed(TrustedDeviceKind.CLINIC_DEVICE)).thenReturn(1L);
        when(workingHoursRepo.findAllByOrderByDayOfWeekAsc()).thenReturn(closedWeek());

        service.setGateEnabled(new GateRequest(true, null, null));

        verifyNoInteractions(stepUpVerificationService);
    }

    @Test
    void setGateEnabled_shouldVerifyStepUpAndAudit_whenDisabling() {
        AccessControlSettings current = settings(true, null, false);
        stubSettings(current);
        when(workingHoursRepo.findAllByOrderByDayOfWeekAsc()).thenReturn(closedWeek());

        AccessControlDTO dto = service.setGateEnabled(new GateRequest(false, "123456", null));

        assertFalse(dto.isGateEnabled());
        verify(stepUpVerificationService).verify(1L, "123456", null);
        verify(auditLogService).logSecurity("UserLoginDeviceGate-Disabled", 1L, "AccessControl", Map.of("by", "admin"));
    }

    @Test
    void setGateEnabled_shouldKeepGateEnabled_whenStepUpFails() {
        AccessControlSettings current = settings(true, null, false);
        stubSettings(current);
        doThrow(new StepUpException(StepUpException.INVALID, "Nieprawidłowe hasło."))
                .when(stepUpVerificationService).verify(1L, null, "wrong");

        assertThrows(StepUpException.class, () -> service.setGateEnabled(new GateRequest(false, null, "wrong")));

        assertTrue(current.isGateEnabled());
        verify(settingsRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void pause_shouldVerifyStepUpBeforePausing() {
        stubSettings(settings(true, null, false));
        when(workingHoursRepo.findAllByOrderByDayOfWeekAsc()).thenReturn(closedWeek());

        service.pause(new PauseRequest(GatePauseDuration.HOUR_1, null, "secret"));

        verify(stepUpVerificationService).verify(1L, null, "secret");
    }

    @Test
    void pause_shouldNotPause_whenStepUpBlocked() {
        AccessControlSettings current = settings(true, null, false);
        stubSettings(current);
        doThrow(new TooManyAttemptsException("Zbyt wiele nieudanych prób. Spróbuj ponownie za 15 min."))
                .when(stepUpVerificationService).verify(any(), any(), any());

        assertThrows(TooManyAttemptsException.class,
                () -> service.pause(new PauseRequest(GatePauseDuration.HOUR_1, "000000", null)));

        assertNull(current.getPausedUntil());
        verify(settingsRepo, never()).save(any());
    }

    @Test
    void pause_shouldNotRunStepUp_whenGateDisabled() {
        stubSettings(settings(false, null, false));

        assertThrows(ConflictException.class, () -> service.pause(new PauseRequest(GatePauseDuration.HOUR_1, "123456", null)));

        verifyNoInteractions(stepUpVerificationService);
    }

    @Test
    void resume_shouldNotRequireStepUp() {
        stubSettings(settings(true, NOW.plusHours(1), false));
        when(workingHoursRepo.findAllByOrderByDayOfWeekAsc()).thenReturn(closedWeek());

        service.resume();

        verifyNoInteractions(stepUpVerificationService);
    }

    @Test
    void updateHours_shouldSkipSaveAndAudit_whenNothingChanged() {
        stubSettings(settings(true, null, false));
        when(workingHoursRepo.findAllByOrderByDayOfWeekAsc()).thenReturn(closedWeek());

        AccessControlDTO dto = service.updateHours(new WorkingHoursUpdateRequest(false, requestWeek()));

        assertFalse(dto.isHoursEnabled());
        assertEquals(7, dto.getHours().size());
        verify(workingHoursRepo, never()).saveAll(any());
        verify(settingsRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void updateHours_shouldSave_whenOnlyFlagChanged() {
        stubSettings(settings(true, null, false));
        when(workingHoursRepo.findAllByOrderByDayOfWeekAsc()).thenReturn(closedWeek());
        when(workingHoursRepo.saveAll(anyCollection())).thenAnswer(invocation -> new ArrayList<>(invocation.getArgument(0)));

        service.updateHours(new WorkingHoursUpdateRequest(true, requestWeek()));

        verify(auditLogService).logSecurity(eq("WorkingHours-Updated"), eq(1L), eq("AccessControl"), anyMap());
    }
}

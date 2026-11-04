package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.AccessControlDTO;
import com.clinic.clinicmanager.DTO.WorkingHoursDTO;
import com.clinic.clinicmanager.DTO.request.accesscontrol.GateRequest;
import com.clinic.clinicmanager.DTO.request.accesscontrol.PauseRequest;
import com.clinic.clinicmanager.DTO.request.accesscontrol.WorkingHoursUpdateRequest;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.AccessControlSettings;
import com.clinic.clinicmanager.model.ClinicWorkingHours;
import com.clinic.clinicmanager.model.constants.GatePauseDuration;
import com.clinic.clinicmanager.model.constants.TrustedDeviceKind;
import com.clinic.clinicmanager.repo.AccessControlSettingsRepo;
import com.clinic.clinicmanager.repo.ClinicWorkingHoursRepo;
import com.clinic.clinicmanager.repo.TrustedDeviceRepo;
import com.clinic.clinicmanager.service.AccessControlService;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.StepUpVerificationService;
import com.clinic.clinicmanager.utils.SessionUtils;
import com.clinic.clinicmanager.utils.TransactionCallbacks;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccessControlServiceImpl implements AccessControlService {

    private static final long SNAPSHOT_TTL_NANOS = 30_000_000_000L;
    private static final int DAYS_IN_WEEK = 7;
    private static final String AUDIT_KEY = "AccessControl";

    private static final String GATE_FLAG_REQUIRED = "Podaj, czy bramka urządzeń ma być włączona.";
    private static final String DURATION_REQUIRED = "Wybierz czas wstrzymania bramki.";
    private static final String GATE_DISABLED = "Bramka urządzeń jest wyłączona.";
    private static final String NO_ACTIVE_DEVICES = "Nie można włączyć bramki - brak aktywnych urządzeń gabinetu.";
    private static final String HOURS_FLAG_REQUIRED = "Podaj, czy godziny pracy mają obowiązywać.";
    private static final String ALL_DAYS_REQUIRED = "Podaj godziny pracy dla wszystkich 7 dni tygodnia.";
    private static final String INVALID_DAY = "Nieprawidłowy dzień tygodnia.";
    private static final String TIMES_REQUIRED = "Podaj godzinę rozpoczęcia i zakończenia dla dni otwartych.";
    private static final String START_BEFORE_END = "Godzina rozpoczęcia musi być wcześniejsza niż zakończenia.";

    private final AccessControlSettingsRepo settingsRepo;
    private final ClinicWorkingHoursRepo workingHoursRepo;
    private final TrustedDeviceRepo trustedDeviceRepo;
    private final AuditLogService auditLogService;
    private final StepUpVerificationService stepUpVerificationService;
    private final Clock businessClock;

    private volatile Snapshot snapshot;

    @Override
    @Transactional(readOnly = true)
    public AccessControlDTO getSettings() {
        return toDto(loadSettings(), workingHoursRepo.findAllByOrderByDayOfWeekAsc());
    }

    @Override
    @Transactional
    public AccessControlDTO setGateEnabled(GateRequest request) {
        Boolean enabled = request.getEnabled();
        if (enabled == null) {
            throw new ValidationException(GATE_FLAG_REQUIRED);
        }
        AccessControlSettings settings = loadSettings();
        if (settings.isGateEnabled() != enabled) {
            if (enabled) {
                if (trustedDeviceRepo.countAllowed(TrustedDeviceKind.CLINIC_DEVICE) == 0) {
                    throw new ConflictException(NO_ACTIVE_DEVICES);
                }
            } else {
                stepUpVerificationService.verify(SessionUtils.requirePrincipal().getId(), request.getCode(), request.getPassword());
            }
            String by = currentUsername();
            settings.setGateEnabled(enabled);
            settings.setPausedUntil(null);
            settings.setPausedBy(null);
            touch(settings, by);
            settingsRepo.save(settings);
            auditLogService.logSecurity(enabled ? "UserLoginDeviceGate-Enabled" : "UserLoginDeviceGate-Disabled", AccessControlSettings.SINGLETON_ID,
                    AUDIT_KEY, Map.of("by", by));
            invalidateSnapshot();
        }
        return toDto(settings, workingHoursRepo.findAllByOrderByDayOfWeekAsc());
    }

    @Override
    @Transactional
    public AccessControlDTO pause(PauseRequest request) {
        GatePauseDuration duration = request.getDuration();
        if (duration == null) {
            throw new ValidationException(DURATION_REQUIRED);
        }
        AccessControlSettings settings = loadSettings();
        if (!settings.isGateEnabled()) {
            throw new ConflictException(GATE_DISABLED);
        }
        stepUpVerificationService.verify(SessionUtils.requirePrincipal().getId(), request.getCode(), request.getPassword());
        LocalDateTime now = now();
        LocalDateTime until = switch (duration) {
            case HOUR_1 -> now.plusHours(1);
            case HOUR_4 -> now.plusHours(4);
            case END_OF_DAY -> now.toLocalDate().atTime(23, 59, 59);
        };
        String by = currentUsername();
        settings.setPausedUntil(until);
        settings.setPausedBy(by);
        touch(settings, by);
        settingsRepo.save(settings);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("until", until.toString());
        details.put("duration", duration.name());
        details.put("by", by);
        auditLogService.logSecurity("UserLoginDeviceGate-Paused", AccessControlSettings.SINGLETON_ID, AUDIT_KEY, details);
        invalidateSnapshot();
        return toDto(settings, workingHoursRepo.findAllByOrderByDayOfWeekAsc());
    }

    @Override
    @Transactional
    public AccessControlDTO resume() {
        AccessControlSettings settings = loadSettings();
        if (settings.getPausedUntil() != null && settings.getPausedUntil().isAfter(now())) {
            String by = currentUsername();
            settings.setPausedUntil(null);
            settings.setPausedBy(null);
            touch(settings, by);
            settingsRepo.save(settings);
            auditLogService.logSecurity("UserLoginDeviceGate-Resumed", AccessControlSettings.SINGLETON_ID, AUDIT_KEY, Map.of("by", by));
            invalidateSnapshot();
        } else if (settings.getPausedUntil() != null) {
            settings.setPausedUntil(null);
            settings.setPausedBy(null);
            settingsRepo.save(settings);
            invalidateSnapshot();
        }
        return toDto(settings, workingHoursRepo.findAllByOrderByDayOfWeekAsc());
    }

    @Override
    @Transactional
    public AccessControlDTO updateHours(WorkingHoursUpdateRequest request) {
        validateHours(request);
        AccessControlSettings settings = loadSettings();
        List<ClinicWorkingHours> currentRows = workingHoursRepo.findAllByOrderByDayOfWeekAsc();
        Map<Integer, ClinicWorkingHours> existing = currentRows.stream()
                .collect(Collectors.toMap(ClinicWorkingHours::getDayOfWeek, Function.identity()));
        if (settings.isHoursEnabled() == request.getHoursEnabled() && sameHours(existing, request.getHours())) {
            return toDto(settings, currentRows);
        }

        Map<String, Object> oldState = hoursState(settings.isHoursEnabled(), existing.values().stream()
                .sorted((a, b) -> a.getDayOfWeek().compareTo(b.getDayOfWeek()))
                .map(WorkingHoursDTO::new)
                .collect(Collectors.toList()));

        for (WorkingHoursDTO day : request.getHours()) {
            ClinicWorkingHours row = existing.computeIfAbsent(day.getDayOfWeek(),
                    dayOfWeek -> ClinicWorkingHours.builder().dayOfWeek(dayOfWeek).build());
            row.setOpen(day.isOpen());
            row.setStartTime(day.getStartTime());
            row.setEndTime(day.getEndTime());
        }
        List<ClinicWorkingHours> saved = workingHoursRepo.saveAll(existing.values()).stream()
                .sorted((a, b) -> a.getDayOfWeek().compareTo(b.getDayOfWeek()))
                .collect(Collectors.toList());

        String by = currentUsername();
        settings.setHoursEnabled(request.getHoursEnabled());
        touch(settings, by);
        settingsRepo.save(settings);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("old", oldState);
        details.put("new", hoursState(settings.isHoursEnabled(), saved.stream().map(WorkingHoursDTO::new).collect(Collectors.toList())));
        details.put("by", by);
        auditLogService.logSecurity("WorkingHours-Updated", AccessControlSettings.SINGLETON_ID, AUDIT_KEY, details);
        invalidateSnapshot();
        return toDto(settings, saved);
    }

    @Override
    public boolean isGateActive() {
        Snapshot current = currentSnapshot();
        return current.gateEnabled() && (current.pausedUntil() == null || !current.pausedUntil().isAfter(now()));
    }

    @Override
    public boolean isWithinWorkingHours() {
        Snapshot current = currentSnapshot();
        if (!current.hoursEnabled()) {
            return true;
        }
        LocalDateTime now = now();
        DayWindow window = current.days().get(now.getDayOfWeek().getValue());
        return window != null && window.covers(now.toLocalTime());
    }

    private Snapshot currentSnapshot() {
        Snapshot current = snapshot;
        if (current == null || System.nanoTime() - current.loadedAtNanos() > SNAPSHOT_TTL_NANOS) {
            current = loadSnapshot();
            snapshot = current;
        }
        return current;
    }

    private Snapshot loadSnapshot() {
        AccessControlSettings settings = settingsRepo.findById(AccessControlSettings.SINGLETON_ID)
                .orElseGet(AccessControlServiceImpl::defaultSettings);
        Map<Integer, DayWindow> days = workingHoursRepo.findAllByOrderByDayOfWeekAsc().stream()
                .collect(Collectors.toMap(ClinicWorkingHours::getDayOfWeek,
                        hours -> new DayWindow(hours.isOpen(), hours.getStartTime(), hours.getEndTime())));
        return new Snapshot(settings.isGateEnabled(), settings.getPausedUntil(), settings.isHoursEnabled(), days, System.nanoTime());
    }

    private void invalidateSnapshot() {
        TransactionCallbacks.runNowAndAfterCommit(() -> snapshot = null);
    }

    private static boolean sameHours(Map<Integer, ClinicWorkingHours> existing, List<WorkingHoursDTO> requested) {
        return requested.stream().allMatch(day -> {
            ClinicWorkingHours row = existing.get(day.getDayOfWeek());
            return row != null
                    && row.isOpen() == day.isOpen()
                    && Objects.equals(row.getStartTime(), day.getStartTime())
                    && Objects.equals(row.getEndTime(), day.getEndTime());
        });
    }

    private void validateHours(WorkingHoursUpdateRequest request) {
        if (request.getHoursEnabled() == null) {
            throw new ValidationException(HOURS_FLAG_REQUIRED);
        }
        if (request.getHours() == null || request.getHours().size() != DAYS_IN_WEEK) {
            throw new ValidationException(ALL_DAYS_REQUIRED);
        }
        Set<Integer> seen = new HashSet<>();
        for (WorkingHoursDTO day : request.getHours()) {
            Integer dayOfWeek = day.getDayOfWeek();
            if (dayOfWeek == null || dayOfWeek < 1 || dayOfWeek > DAYS_IN_WEEK || !seen.add(dayOfWeek)) {
                throw new ValidationException(INVALID_DAY);
            }
            if (day.isOpen()) {
                if (day.getStartTime() == null || day.getEndTime() == null) {
                    throw new ValidationException(TIMES_REQUIRED);
                }
                if (!day.getStartTime().isBefore(day.getEndTime())) {
                    throw new ValidationException(START_BEFORE_END);
                }
            }
        }
    }

    private AccessControlSettings loadSettings() {
        return settingsRepo.findById(AccessControlSettings.SINGLETON_ID)
                .orElseGet(AccessControlServiceImpl::defaultSettings);
    }

    private static AccessControlSettings defaultSettings() {
        return AccessControlSettings.builder()
                .id(AccessControlSettings.SINGLETON_ID)
                .gateEnabled(false)
                .hoursEnabled(false)
                .build();
    }

    private AccessControlDTO toDto(AccessControlSettings settings, List<ClinicWorkingHours> hours) {
        return new AccessControlDTO(
                settings.isGateEnabled(),
                settings.isGateActive(now()),
                settings.getPausedUntil(),
                settings.getPausedBy(),
                settings.isHoursEnabled(),
                hours.stream().map(WorkingHoursDTO::new).collect(Collectors.toList()),
                trustedDeviceRepo.countAllowed(TrustedDeviceKind.CLINIC_DEVICE));
    }

    private static Map<String, Object> hoursState(boolean hoursEnabled, List<WorkingHoursDTO> hours) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("hoursEnabled", hoursEnabled);
        state.put("hours", hours);
        return state;
    }

    private void touch(AccessControlSettings settings, String by) {
        settings.setUpdatedAt(now());
        settings.setUpdatedBy(by);
    }

    private static String currentUsername() {
        return SessionUtils.requirePrincipal().getUsername();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(businessClock);
    }

    private record DayWindow(boolean open, LocalTime start, LocalTime end) {
        boolean covers(LocalTime time) {
            return open && start != null && end != null && !time.isBefore(start) && time.isBefore(end);
        }
    }

    private record Snapshot(boolean gateEnabled, LocalDateTime pausedUntil, boolean hoursEnabled,
                            Map<Integer, DayWindow> days, long loadedAtNanos) {
    }
}

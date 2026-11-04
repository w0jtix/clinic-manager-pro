package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ClinicDeviceDTO;
import com.clinic.clinicmanager.DTO.ClinicDeviceLoginResult;
import com.clinic.clinicmanager.DTO.ClinicDeviceRegistration;
import com.clinic.clinicmanager.DTO.LoginDeviceCheck;
import com.clinic.clinicmanager.DTO.RegistrationCodeDTO;
import com.clinic.clinicmanager.DTO.request.device.CreateRegistrationCodeRequest;
import com.clinic.clinicmanager.config.security.ClinicDeviceSettings;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.TooManyAttemptsException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.DeviceRegistrationCode;
import com.clinic.clinicmanager.model.TrustedDevice;
import com.clinic.clinicmanager.model.constants.TrustedDeviceKind;
import com.clinic.clinicmanager.repo.DeviceRegistrationCodeRepo;
import com.clinic.clinicmanager.repo.TrustedDeviceRepo;
import com.clinic.clinicmanager.service.impl.ClinicDeviceResolver;
import com.clinic.clinicmanager.service.impl.ClinicDeviceServiceImpl;
import com.clinic.clinicmanager.utils.SecureTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicDeviceServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneId.of("Europe/Warsaw"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final TrustedDeviceKind KIND = TrustedDeviceKind.CLINIC_DEVICE;
    private static final String IP = "10.0.0.9";
    private static final String IP_SUBJECT = "ip:10.0.0.9";
    private static final String IPAD_UA =
            "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1";

    @Mock TrustedDeviceRepo trustedDeviceRepo;
    @Mock DeviceRegistrationCodeRepo registrationCodeRepo;
    @Mock DeviceCodeAttemptService deviceCodeAttemptService;
    @Mock ClinicDeviceResolver clinicDeviceResolver;
    @Mock AuditLogService auditLogService;

    private ClinicDeviceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClinicDeviceServiceImpl(trustedDeviceRepo, registrationCodeRepo, deviceCodeAttemptService,
                clinicDeviceResolver, auditLogService, new ClinicDeviceSettings(120), CLOCK);
        UserDetailsImpl principal = new UserDetailsImpl(1L, "admin", "pass", null, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private TrustedDevice device(Long id, String name, String rawToken) {
        return TrustedDevice.builder()
                .id(id)
                .kind(KIND)
                .tokenHash(SecureTokens.sha256Hex(rawToken))
                .name(name)
                .label("Safari · iPad")
                .createdAt(NOW.minusDays(30))
                .lastUsedAt(NOW.minusDays(1))
                .registeredBy("admin")
                .build();
    }

    private DeviceRegistrationCode code(Long id, String code, String name, Long replaceDeviceId) {
        return DeviceRegistrationCode.builder()
                .id(id)
                .code(code)
                .name(name)
                .replaceDeviceId(replaceDeviceId)
                .createdBy("admin")
                .createdAt(NOW.minusMinutes(5))
                .expiresAt(NOW.plusMinutes(10))
                .build();
    }

    private void stubSaveCode() {
        when(registrationCodeRepo.save(any(DeviceRegistrationCode.class))).thenAnswer(invocation -> {
            DeviceRegistrationCode saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(40L);
            }
            return saved;
        });
    }

    private void stubSaveDevice() {
        when(trustedDeviceRepo.save(any(TrustedDevice.class))).thenAnswer(invocation -> {
            TrustedDevice saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(50L);
            }
            return saved;
        });
    }

    @Test
    void createCode_shouldGenerateSixCharCodeFromSafeAlphabetValidFor15Minutes() {
        when(trustedDeviceRepo.findActiveByName(KIND, "Recepcja")).thenReturn(List.of());
        when(registrationCodeRepo.existsActiveByName("Recepcja", NOW)).thenReturn(false);
        when(registrationCodeRepo.existsActiveByCode(anyString(), eq(NOW))).thenReturn(false);
        stubSaveCode();

        RegistrationCodeDTO dto = service.createCode(new CreateRegistrationCodeRequest("  Recepcja  ", null));

        ArgumentCaptor<DeviceRegistrationCode> saved = ArgumentCaptor.forClass(DeviceRegistrationCode.class);
        verify(registrationCodeRepo).save(saved.capture());
        DeviceRegistrationCode code = saved.getValue();
        assertTrue(code.getCode().matches("[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{6}"));
        assertEquals("Recepcja", code.getName());
        assertNull(code.getReplaceDeviceId());
        assertEquals("admin", code.getCreatedBy());
        assertEquals(NOW, code.getCreatedAt());
        assertEquals(NOW.plusMinutes(15), code.getExpiresAt());
        assertEquals(code.getCode().substring(0, 3) + "-" + code.getCode().substring(3), dto.getCode());
        assertEquals("Recepcja", dto.getName());
        verify(auditLogService).logSecurity("Device-Code-Created", 40L, "Recepcja",
                Map.of("name", "Recepcja", "by", "admin"));
    }

    @Test
    void createCode_shouldRegenerate_whenCodeCollidesWithActiveCode() {
        when(trustedDeviceRepo.findActiveByName(KIND, "Recepcja")).thenReturn(List.of());
        when(registrationCodeRepo.existsActiveByName("Recepcja", NOW)).thenReturn(false);
        when(registrationCodeRepo.existsActiveByCode(anyString(), eq(NOW))).thenReturn(true, true, false);
        stubSaveCode();

        service.createCode(new CreateRegistrationCodeRequest("Recepcja", null));

        verify(registrationCodeRepo, times(3)).existsActiveByCode(anyString(), eq(NOW));
    }

    @Test
    void createCode_shouldThrowConflictWithoutAuditOrSave_whenActiveCodeAlreadyExists() {
        when(registrationCodeRepo.existsActive(NOW)).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.createCode(new CreateRegistrationCodeRequest("Recepcja", null)));

        assertEquals("Istnieje już aktywny kod rejestracji. Użyj go albo anuluj, zanim wygenerujesz nowy.", ex.getMessage());
        InOrder inOrder = inOrder(registrationCodeRepo);
        inOrder.verify(registrationCodeRepo).acquireTransactionLock(anyLong());
        inOrder.verify(registrationCodeRepo).existsActive(NOW);
        verify(registrationCodeRepo, never()).save(any());
        verifyNoInteractions(auditLogService, trustedDeviceRepo);
    }

    @Test
    void createCode_shouldAcquireLockBeforeCheckingActiveCode_whenCreating() {
        when(trustedDeviceRepo.findActiveByName(KIND, "Recepcja")).thenReturn(List.of());
        when(registrationCodeRepo.existsActiveByName("Recepcja", NOW)).thenReturn(false);
        when(registrationCodeRepo.existsActiveByCode(anyString(), eq(NOW))).thenReturn(false);
        stubSaveCode();

        service.createCode(new CreateRegistrationCodeRequest("Recepcja", null));

        InOrder inOrder = inOrder(registrationCodeRepo);
        inOrder.verify(registrationCodeRepo).acquireTransactionLock(anyLong());
        inOrder.verify(registrationCodeRepo).existsActive(NOW);
        inOrder.verify(registrationCodeRepo).save(any(DeviceRegistrationCode.class));
    }

    @Test
    void createCode_shouldThrowValidation_whenNeitherNameNorReplaceGiven() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.createCode(new CreateRegistrationCodeRequest("   ", null)));

        assertEquals("Podaj nazwę urządzenia albo wybierz urządzenie do zastąpienia.", ex.getMessage());
        verify(registrationCodeRepo, never()).save(any());
    }

    @Test
    void createCode_shouldThrowValidation_whenBothNameAndReplaceGiven() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.createCode(new CreateRegistrationCodeRequest("Recepcja", 5L)));

        assertEquals("Podaj nazwę urządzenia albo wybierz urządzenie do zastąpienia.", ex.getMessage());
    }

    @Test
    void createCode_shouldThrowValidation_whenNameLongerThan100() {
        assertThrows(ValidationException.class,
                () -> service.createCode(new CreateRegistrationCodeRequest("x".repeat(101), null)));
        verify(registrationCodeRepo, never()).save(any());
    }

    @Test
    void createCode_shouldThrowConflict_whenActiveDeviceHasSameName() {
        when(trustedDeviceRepo.findActiveByName(KIND, "Recepcja")).thenReturn(List.of(device(5L, "recepcja", "t")));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.createCode(new CreateRegistrationCodeRequest("Recepcja", null)));

        assertEquals("Urządzenie o tej nazwie już istnieje.", ex.getMessage());
        verify(registrationCodeRepo, never()).save(any());
    }

    @Test
    void createCode_shouldThrowConflict_whenActiveCodeReservesSameName() {
        when(trustedDeviceRepo.findActiveByName(KIND, "Recepcja")).thenReturn(List.of());
        when(registrationCodeRepo.existsActiveByName("Recepcja", NOW)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.createCode(new CreateRegistrationCodeRequest("Recepcja", null)));
    }

    @Test
    void createCode_shouldReferenceDeviceToReplace() {
        when(trustedDeviceRepo.findByIdAndKind(5L, KIND)).thenReturn(Optional.of(device(5L, "Gabinet 1", "t")));
        when(registrationCodeRepo.existsActiveByCode(anyString(), eq(NOW))).thenReturn(false);
        stubSaveCode();

        RegistrationCodeDTO dto = service.createCode(new CreateRegistrationCodeRequest(null, 5L));

        assertNull(dto.getName());
        assertEquals(5L, dto.getReplaceDeviceId());
        assertEquals("Gabinet 1", dto.getReplaceDeviceName());
        verify(auditLogService).logSecurity("Device-Code-Created", 40L, "Gabinet 1",
                Map.of("replace", "Gabinet 1", "by", "admin"));
    }

    @Test
    void createCode_shouldThrowNotFound_whenDeviceToReplaceIsRevoked() {
        TrustedDevice revoked = device(5L, "Gabinet 1", "t");
        revoked.setRevokedAt(NOW.minusDays(1));
        when(trustedDeviceRepo.findByIdAndKind(5L, KIND)).thenReturn(Optional.of(revoked));

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.createCode(new CreateRegistrationCodeRequest(null, 5L)));

        assertEquals("Nie znaleziono urządzenia.", ex.getMessage());
    }

    @Test
    void getActiveCodes_shouldFormatCodeAndResolveReplaceName() {
        when(registrationCodeRepo.findActive(NOW)).thenReturn(List.of(code(40L, "ABC234", null, 5L)));
        when(trustedDeviceRepo.findAllById(any())).thenReturn(List.of(device(5L, "Gabinet 1", "t")));

        List<RegistrationCodeDTO> codes = service.getActiveCodes();

        assertEquals(1, codes.size());
        assertEquals("ABC-234", codes.get(0).getCode());
        assertEquals("Gabinet 1", codes.get(0).getReplaceDeviceName());
    }

    @Test
    void getActiveCodes_shouldReturnNullReplaceName_whenCodeHasNoDeviceToReplace() {
        when(registrationCodeRepo.findActive(NOW)).thenReturn(List.of(code(40L, "ABC234", "Recepcja", null)));

        List<RegistrationCodeDTO> codes = service.getActiveCodes();

        assertEquals(1, codes.size());
        assertNull(codes.get(0).getReplaceDeviceId());
        assertNull(codes.get(0).getReplaceDeviceName());
        verify(trustedDeviceRepo, never()).findAllById(any());
    }

    @Test
    void getActiveCodes_shouldResolveOnlyReplaceCodes_whenMixedWithNameCodes() {
        when(registrationCodeRepo.findActive(NOW))
                .thenReturn(List.of(code(41L, "XYZ234", "Recepcja", null), code(40L, "ABC234", null, 5L)));
        when(trustedDeviceRepo.findAllById(any())).thenReturn(List.of(device(5L, "Gabinet 1", "t")));

        List<RegistrationCodeDTO> codes = service.getActiveCodes();

        assertNull(codes.get(0).getReplaceDeviceName());
        assertEquals("Gabinet 1", codes.get(1).getReplaceDeviceName());
    }

    @Test
    void getDevices_shouldReturnNullReplacedByName_whenNoDeviceWasReplaced() {
        when(trustedDeviceRepo.findAllByKindOrdered(KIND, false))
                .thenReturn(List.of(device(6L, "Gabinet 1", "a"), device(7L, "Recepcja", "b")));

        List<ClinicDeviceDTO> devices = service.getDevices(false);

        assertEquals(2, devices.size());
        assertNull(devices.get(0).getReplacedByName());
        assertNull(devices.get(1).getReplacedByName());
        verify(trustedDeviceRepo, never()).findAllById(any());
    }

    @Test
    void cancelCode_shouldSetCancelledAtAndAudit() {
        DeviceRegistrationCode active = code(40L, "ABC234", "Recepcja", null);
        when(registrationCodeRepo.findById(40L)).thenReturn(Optional.of(active));
        stubSaveCode();

        service.cancelCode(40L);

        assertEquals(NOW, active.getCancelledAt());
        verify(auditLogService).logSecurity("Device-Code-Cancelled", 40L, "Recepcja",
                Map.of("name", "Recepcja", "by", "admin"));
    }

    @Test
    void cancelCode_shouldThrowNotFound_whenCodeAlreadyUsed() {
        DeviceRegistrationCode used = code(40L, "ABC234", "Recepcja", null);
        used.setUsedAt(NOW.minusMinutes(1));
        when(registrationCodeRepo.findById(40L)).thenReturn(Optional.of(used));

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> service.cancelCode(40L));

        assertEquals("Nie znaleziono kodu.", ex.getMessage());
        verify(registrationCodeRepo, never()).save(any());
    }

    @Test
    void renameDevice_shouldTrimNameAndAuditOldAndNew() {
        TrustedDevice existing = device(5L, "Gabinet 1", "t");
        when(trustedDeviceRepo.findByIdAndKind(5L, KIND)).thenReturn(Optional.of(existing));
        when(trustedDeviceRepo.findActiveByName(KIND, "Gabinet 2")).thenReturn(List.of());
        when(registrationCodeRepo.existsActiveByName("Gabinet 2", NOW)).thenReturn(false);
        stubSaveDevice();

        ClinicDeviceDTO dto = service.renameDevice(5L, " Gabinet 2 ");

        assertEquals("Gabinet 2", dto.getName());
        assertEquals("ACTIVE", dto.getStatus());
        verify(auditLogService).logSecurity("Device-Renamed", 5L, "Gabinet 2",
                Map.of("oldName", "Gabinet 1", "newName", "Gabinet 2"));
    }

    @Test
    void renameDevice_shouldAllowChangingCaseOfOwnName() {
        TrustedDevice existing = device(5L, "gabinet 1", "t");
        when(trustedDeviceRepo.findByIdAndKind(5L, KIND)).thenReturn(Optional.of(existing));
        when(trustedDeviceRepo.findActiveByName(KIND, "Gabinet 1")).thenReturn(List.of(existing));
        when(registrationCodeRepo.existsActiveByName("Gabinet 1", NOW)).thenReturn(false);
        stubSaveDevice();

        assertEquals("Gabinet 1", service.renameDevice(5L, "Gabinet 1").getName());
    }

    @Test
    void renameDevice_shouldThrowConflict_whenOtherActiveDeviceHasName() {
        when(trustedDeviceRepo.findByIdAndKind(5L, KIND)).thenReturn(Optional.of(device(5L, "Gabinet 1", "t")));
        when(trustedDeviceRepo.findActiveByName(KIND, "Recepcja")).thenReturn(List.of(device(6L, "Recepcja", "u")));

        assertThrows(ConflictException.class, () -> service.renameDevice(5L, "Recepcja"));
        verify(trustedDeviceRepo, never()).save(any());
    }

    @Test
    void renameDevice_shouldThrowNotFound_whenDeviceRevoked() {
        TrustedDevice revoked = device(5L, "Gabinet 1", "t");
        revoked.setRevokedAt(NOW.minusDays(1));
        when(trustedDeviceRepo.findByIdAndKind(5L, KIND)).thenReturn(Optional.of(revoked));

        assertThrows(ResourceNotFoundException.class, () -> service.renameDevice(5L, "Nowa"));
    }

    @Test
    void revokeDevice_shouldSetRevokedAtAndAudit() {
        TrustedDevice existing = device(5L, "Gabinet 1", "t");
        when(trustedDeviceRepo.findByIdAndKind(5L, KIND)).thenReturn(Optional.of(existing));

        service.revokeDevice(5L);

        assertEquals(NOW, existing.getRevokedAt());
        verify(trustedDeviceRepo).save(existing);
        verify(auditLogService).logSecurity("Device-Revoked", 5L, "Gabinet 1", Map.of("name", "Gabinet 1"));
    }

    @Test
    void revokeDevice_shouldThrowNotFound_whenNotClinicDevice() {
        when(trustedDeviceRepo.findByIdAndKind(9L, KIND)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.revokeDevice(9L));
    }

    @Test
    void getDevices_shouldMapStatusesAndReplacementName() {
        TrustedDevice active = device(6L, "Gabinet 1", "a");
        TrustedDevice suspected = device(7L, "Recepcja", "b");
        suspected.setSuspectedCopyAt(NOW.minusHours(1));
        TrustedDevice revoked = device(5L, "Gabinet 1", "c");
        revoked.setRevokedAt(NOW.minusDays(2));
        revoked.setReplacedById(6L);
        when(trustedDeviceRepo.findAllByKindOrdered(KIND, true)).thenReturn(List.of(active, suspected, revoked));
        when(trustedDeviceRepo.findAllById(any())).thenReturn(List.of(active));

        List<ClinicDeviceDTO> devices = service.getDevices(true);

        assertEquals("ACTIVE", devices.get(0).getStatus());
        assertEquals("SUSPECTED_COPY", devices.get(1).getStatus());
        assertEquals("REVOKED", devices.get(2).getStatus());
        assertEquals("Gabinet 1", devices.get(2).getReplacedByName());
        assertNull(devices.get(0).getReplacedByName());
    }

    @Test
    void register_shouldCreateClinicDeviceWithoutUserAndConsumeCode() {
        DeviceRegistrationCode active = code(40L, "ABC234", "Recepcja", null);
        when(deviceCodeAttemptService.isBlocked(IP_SUBJECT)).thenReturn(false);
        when(deviceCodeAttemptService.reserveAttempt(IP_SUBJECT)).thenReturn(OptionalInt.of(1));
        when(registrationCodeRepo.findActiveByCodeForUpdate("ABC234", NOW)).thenReturn(List.of(active));
        when(trustedDeviceRepo.findActiveByName(KIND, "Recepcja")).thenReturn(List.of());
        stubSaveDevice();
        stubSaveCode();

        ClinicDeviceRegistration registration = service.register(" abc-234 ", null, IPAD_UA, IP);

        ArgumentCaptor<TrustedDevice> saved = ArgumentCaptor.forClass(TrustedDevice.class);
        verify(trustedDeviceRepo).save(saved.capture());
        TrustedDevice created = saved.getValue();
        assertEquals(KIND, created.getKind());
        assertNull(created.getUserId());
        assertNull(created.getExpiresAt());
        assertEquals("Recepcja", created.getName());
        assertEquals("admin", created.getRegisteredBy());
        assertEquals("Safari · iPad", created.getLabel());
        assertEquals(IP, created.getLastIp());
        assertEquals(43, registration.token().length());
        assertEquals(SecureTokens.sha256Hex(registration.token()), created.getTokenHash());
        assertEquals("Recepcja", registration.deviceName());
        assertEquals(NOW, active.getUsedAt());
        verify(deviceCodeAttemptService).recordSuccess(IP_SUBJECT);
        verify(deviceCodeAttemptService, never()).recordFailure(anyString(), anyInt());
        verify(auditLogService).logSecurity("Device-Registered", 50L, "Recepcja",
                Map.of("name", "Recepcja", "replaced", false, "ip", IP), "Recepcja");
    }

    @Test
    void register_shouldRevokeReplacedDeviceAndInheritItsName() {
        TrustedDevice old = device(5L, "Gabinet 1", "old");
        when(deviceCodeAttemptService.isBlocked(IP_SUBJECT)).thenReturn(false);
        when(deviceCodeAttemptService.reserveAttempt(IP_SUBJECT)).thenReturn(OptionalInt.of(1));
        when(registrationCodeRepo.findActiveByCodeForUpdate("ABC234", NOW))
                .thenReturn(List.of(code(40L, "ABC234", null, 5L)));
        when(trustedDeviceRepo.findByIdAndKind(5L, KIND)).thenReturn(Optional.of(old));
        stubSaveDevice();
        stubSaveCode();

        ClinicDeviceRegistration registration = service.register("ABC234", null, IPAD_UA, IP);

        assertEquals("Gabinet 1", registration.deviceName());
        assertEquals(NOW, old.getRevokedAt());
        assertEquals(50L, old.getReplacedById());
        verify(trustedDeviceRepo, never()).findActiveByName(any(), any());
        verify(auditLogService).logSecurity("Device-Registered", 50L, "Gabinet 1",
                Map.of("name", "Gabinet 1", "replaced", true, "ip", IP), "Gabinet 1");
    }

    @Test
    void register_shouldThrowValidationAndCountFailure_whenCodeUnknown() {
        when(deviceCodeAttemptService.isBlocked(IP_SUBJECT)).thenReturn(false);
        when(deviceCodeAttemptService.reserveAttempt(IP_SUBJECT)).thenReturn(OptionalInt.of(2));
        when(registrationCodeRepo.findActiveByCodeForUpdate("ZZZ999", NOW)).thenReturn(List.of());
        when(deviceCodeAttemptService.recordFailure(IP_SUBJECT, 2)).thenReturn(3);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.register("ZZZ-999", null, IPAD_UA, IP));

        assertEquals("Kod jest nieprawidłowy albo wygasł.", ex.getMessage());
        verify(trustedDeviceRepo, never()).save(any());
        verify(deviceCodeAttemptService, never()).recordSuccess(anyString());
    }

    @Test
    void register_shouldCountFailureWithoutQuery_whenCodeMalformed() {
        when(deviceCodeAttemptService.isBlocked(IP_SUBJECT)).thenReturn(false);
        when(deviceCodeAttemptService.reserveAttempt(IP_SUBJECT)).thenReturn(OptionalInt.of(1));
        when(deviceCodeAttemptService.recordFailure(IP_SUBJECT, 1)).thenReturn(4);

        assertThrows(ValidationException.class, () -> service.register("AB", null, IPAD_UA, IP));

        verifyNoInteractions(registrationCodeRepo);
    }

    @Test
    void register_shouldThrowTooManyAttempts_whenFailureReachesLimit() {
        when(deviceCodeAttemptService.isBlocked(IP_SUBJECT)).thenReturn(false);
        when(deviceCodeAttemptService.reserveAttempt(IP_SUBJECT)).thenReturn(OptionalInt.of(5));
        when(registrationCodeRepo.findActiveByCodeForUpdate("ZZZ999", NOW)).thenReturn(List.of());
        when(deviceCodeAttemptService.recordFailure(IP_SUBJECT, 5)).thenReturn(0);
        when(deviceCodeAttemptService.getBlockRemainingMinutes(IP_SUBJECT)).thenReturn(15L);

        TooManyAttemptsException ex = assertThrows(TooManyAttemptsException.class,
                () -> service.register("ZZZ999", null, IPAD_UA, IP));

        assertEquals("Zbyt wiele nieudanych prób. Spróbuj ponownie za 15 min.", ex.getMessage());
    }

    @Test
    void register_shouldThrowTooManyAttemptsWithoutLookup_whenIpBlocked() {
        when(deviceCodeAttemptService.isBlocked(IP_SUBJECT)).thenReturn(true);
        when(deviceCodeAttemptService.getBlockRemainingMinutes(IP_SUBJECT)).thenReturn(7L);

        TooManyAttemptsException ex = assertThrows(TooManyAttemptsException.class,
                () -> service.register("ABC234", null, IPAD_UA, IP));

        assertEquals("Zbyt wiele nieudanych prób. Spróbuj ponownie za 7 min.", ex.getMessage());
        verify(deviceCodeAttemptService, never()).reserveAttempt(anyString());
        verifyNoInteractions(registrationCodeRepo);
    }

    @Test
    void register_shouldThrowConflictWithoutConsumingCodeOrCountingFailure_whenBrowserHoldsOtherActiveDevice() {
        DeviceRegistrationCode active = code(40L, "ABC234", "Recepcja", null);
        stubValidCode(active);
        when(clinicDeviceResolver.resolveToken("current-token")).thenReturn(Optional.of(device(5L, "Gabinet 1", "current-token")));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.register("ABC234", "current-token", IPAD_UA, IP));

        assertEquals("To urządzenie jest już zarejestrowane.", ex.getMessage());
        assertNull(active.getUsedAt());
        verify(deviceCodeAttemptService, never()).recordFailure(anyString(), anyInt());
        verify(registrationCodeRepo, never()).save(any());
        verify(trustedDeviceRepo, never()).save(any());
    }

    private void stubValidCode(DeviceRegistrationCode registrationCode) {
        when(deviceCodeAttemptService.isBlocked(IP_SUBJECT)).thenReturn(false);
        when(deviceCodeAttemptService.reserveAttempt(IP_SUBJECT)).thenReturn(OptionalInt.of(1));
        when(registrationCodeRepo.findActiveByCodeForUpdate(registrationCode.getCode(), NOW)).thenReturn(List.of(registrationCode));
    }

    @Test
    void register_shouldReplaceDeviceHeldByThisBrowser_whenCodeReplacesIt() {
        TrustedDevice current = device(5L, "Gabinet 1", "current-token");
        DeviceRegistrationCode replaceCode = code(40L, "ABC234", null, 5L);
        stubValidCode(replaceCode);
        when(clinicDeviceResolver.resolveToken("current-token")).thenReturn(Optional.of(current));
        when(trustedDeviceRepo.findByIdAndKind(5L, KIND)).thenReturn(Optional.of(current));
        stubSaveDevice();
        stubSaveCode();

        ClinicDeviceRegistration registration = service.register("ABC234", "current-token", IPAD_UA, IP);

        assertEquals("Gabinet 1", registration.deviceName());
        assertEquals(NOW, current.getRevokedAt());
        assertEquals(50L, current.getReplacedById());
        assertEquals(NOW, replaceCode.getUsedAt());
        verify(clinicDeviceResolver).invalidateAllowanceCache();
    }

    @Test
    void register_shouldReplaceSuspectedDeviceHeldByThisBrowser_whenCodeReplacesIt() {
        TrustedDevice current = device(5L, "Gabinet 1", "current-token");
        current.setSuspectedCopyAt(NOW.minusHours(2));
        DeviceRegistrationCode replaceCode = code(40L, "ABC234", null, 5L);
        stubValidCode(replaceCode);
        when(clinicDeviceResolver.resolveToken("current-token")).thenReturn(Optional.of(current));
        when(trustedDeviceRepo.findByIdAndKind(5L, KIND)).thenReturn(Optional.of(current));
        stubSaveDevice();
        stubSaveCode();

        service.register("ABC234", "current-token", IPAD_UA, IP);

        assertEquals(NOW, current.getRevokedAt());
        assertEquals(50L, current.getReplacedById());
    }

    @Test
    void register_shouldAllowNewDeviceWithoutRevokingSuspectedOne_whenBrowserHoldsSuspectedDevice() {
        TrustedDevice suspected = device(5L, "Gabinet 1", "current-token");
        suspected.setSuspectedCopyAt(NOW.minusHours(2));
        DeviceRegistrationCode newCode = code(40L, "ABC234", "Recepcja", null);
        stubValidCode(newCode);
        when(clinicDeviceResolver.resolveToken("current-token")).thenReturn(Optional.of(suspected));
        when(trustedDeviceRepo.findActiveByName(KIND, "Recepcja")).thenReturn(List.of());
        stubSaveDevice();
        stubSaveCode();

        ClinicDeviceRegistration registration = service.register("ABC234", "current-token", IPAD_UA, IP);

        assertEquals("Recepcja", registration.deviceName());
        assertNull(suspected.getRevokedAt());
        assertEquals(NOW, newCode.getUsedAt());
    }

    @Test
    void recognizeOnLogin_shouldRotateTokenAndRecordLastSeen_whenCurrentTokenMatches() {
        TrustedDevice existing = device(5L, "Gabinet 1", "device-token");
        String oldHash = existing.getTokenHash();
        when(trustedDeviceRepo.findByTokenHashAndKind(oldHash, KIND)).thenReturn(Optional.of(existing));

        ClinicDeviceLoginResult result = service.recognizeOnLogin("device-token", "anna", IP, IPAD_UA);

        assertEquals(ClinicDeviceLoginResult.Outcome.ROTATED, result.outcome());
        assertEquals("Gabinet 1", result.deviceName());
        assertNotEquals("device-token", result.token());
        assertEquals(SecureTokens.sha256Hex(result.token()), existing.getTokenHash());
        assertEquals(oldHash, existing.getPreviousTokenHash());
        assertEquals(NOW, existing.getLastUsedAt());
        assertEquals("anna", existing.getLastSeenUsername());
        assertEquals(IP, existing.getLastIp());
        verify(trustedDeviceRepo).save(existing);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void recognizeOnLogin_shouldFlagSuspectedCopyWithoutRevokingOrNewToken_whenPreviousTokenReused() {
        TrustedDevice existing = device(5L, "Gabinet 1", "rotated-token");
        String reusedHash = SecureTokens.sha256Hex("stale-token");
        existing.setPreviousTokenHash(reusedHash);
        when(trustedDeviceRepo.findByTokenHashAndKind(reusedHash, KIND)).thenReturn(Optional.empty());
        when(trustedDeviceRepo.findFirstByPreviousTokenHashAndKind(reusedHash, KIND)).thenReturn(Optional.of(existing));

        ClinicDeviceLoginResult result = service.recognizeOnLogin("stale-token", "anna", IP, IPAD_UA);

        assertEquals(ClinicDeviceLoginResult.Outcome.REUSED, result.outcome());
        assertNull(result.token());
        assertEquals(NOW, existing.getSuspectedCopyAt());
        assertNull(existing.getRevokedAt());
        assertEquals(SecureTokens.sha256Hex("rotated-token"), existing.getTokenHash());
        verify(auditLogService).logSecurity("Security-Device-Reuse", 5L, "Gabinet 1",
                Map.of("name", "Gabinet 1", "ip", IP, "username", "anna"), "Gabinet 1");
    }

    @Test
    void recognizeOnLogin_shouldKeepFirstSuspectedTimestamp_whenReusedAgain() {
        TrustedDevice existing = device(5L, "Gabinet 1", "rotated-token");
        String reusedHash = SecureTokens.sha256Hex("stale-token");
        existing.setPreviousTokenHash(reusedHash);
        existing.setSuspectedCopyAt(NOW.minusDays(3));
        when(trustedDeviceRepo.findByTokenHashAndKind(reusedHash, KIND)).thenReturn(Optional.empty());
        when(trustedDeviceRepo.findFirstByPreviousTokenHashAndKind(reusedHash, KIND)).thenReturn(Optional.of(existing));

        service.recognizeOnLogin("stale-token", "anna", IP, IPAD_UA);

        assertEquals(NOW.minusDays(3), existing.getSuspectedCopyAt());
        verify(trustedDeviceRepo, never()).save(any());
    }

    @Test
    void recognizeOnLogin_shouldReturnInvalid_whenDeviceRevoked() {
        TrustedDevice revoked = device(5L, "Gabinet 1", "device-token");
        revoked.setRevokedAt(NOW.minusDays(1));
        when(trustedDeviceRepo.findByTokenHashAndKind(revoked.getTokenHash(), KIND)).thenReturn(Optional.of(revoked));

        assertEquals(ClinicDeviceLoginResult.Outcome.INVALID, service.recognizeOnLogin("device-token", "anna", IP, IPAD_UA).outcome());
        verify(trustedDeviceRepo, never()).save(any());
    }

    @Test
    void recognizeOnLogin_shouldReturnInvalid_whenTokenUnknown() {
        String hash = SecureTokens.sha256Hex("unknown");
        when(trustedDeviceRepo.findByTokenHashAndKind(hash, KIND)).thenReturn(Optional.empty());
        when(trustedDeviceRepo.findFirstByPreviousTokenHashAndKind(hash, KIND)).thenReturn(Optional.empty());

        assertEquals(ClinicDeviceLoginResult.Outcome.INVALID, service.recognizeOnLogin("unknown", "anna", IP, IPAD_UA).outcome());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void recognizeOnLogin_shouldReturnNone_whenNoCookie() {
        assertEquals(ClinicDeviceLoginResult.Outcome.NONE, service.recognizeOnLogin(null, "anna", IP, IPAD_UA).outcome());
        verifyNoInteractions(trustedDeviceRepo);
    }

    @Test
    void deleteDevice_shouldDetachReferencesThenDeleteAndAudit_whenDeviceRevoked() {
        TrustedDevice revoked = device(5L, "Gabinet 1", "t");
        revoked.setRevokedAt(NOW.minusDays(1));
        when(trustedDeviceRepo.findByIdAndKind(5L, KIND)).thenReturn(Optional.of(revoked));

        service.deleteDevice(5L);

        InOrder inOrder = inOrder(trustedDeviceRepo, registrationCodeRepo);
        inOrder.verify(trustedDeviceRepo).clearReplacedBy(5L);
        inOrder.verify(registrationCodeRepo).cancelPendingForReplacedDevice(5L, NOW);
        inOrder.verify(registrationCodeRepo).clearReplaceDevice(5L);
        inOrder.verify(trustedDeviceRepo).delete(revoked);
        verify(auditLogService).logSecurity("Device-Deleted", 5L, "Gabinet 1", Map.of("name", "Gabinet 1"));
    }

    @Test
    void deleteDevice_shouldThrowConflictWithoutDeleting_whenDeviceNotRevoked() {
        TrustedDevice suspected = device(5L, "Gabinet 1", "t");
        suspected.setSuspectedCopyAt(NOW.minusHours(1));
        when(trustedDeviceRepo.findByIdAndKind(5L, KIND)).thenReturn(Optional.of(suspected));

        ConflictException ex = assertThrows(ConflictException.class, () -> service.deleteDevice(5L));

        assertEquals("Najpierw unieważnij urządzenie.", ex.getMessage());
        verify(trustedDeviceRepo, never()).delete(any());
        verify(trustedDeviceRepo, never()).clearReplacedBy(any());
        verifyNoInteractions(registrationCodeRepo, auditLogService);
    }

    @Test
    void deleteDevice_shouldThrowNotFound_whenDeviceMissing() {
        when(trustedDeviceRepo.findByIdAndKind(9L, KIND)).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> service.deleteDevice(9L));

        assertEquals("Nie znaleziono urządzenia.", ex.getMessage());
        verify(trustedDeviceRepo, never()).delete(any());
    }

    private TrustedDevice rotatedDevice(LocalDateTime rotatedAt) {
        TrustedDevice device = device(5L, "Gabinet 1", "new-token");
        device.setPreviousTokenHash(SecureTokens.sha256Hex("old-token"));
        device.setRotatedAt(rotatedAt);
        device.setLastIp(IP);
        device.setLastUserAgentHash(SecureTokens.sha256Hex(IPAD_UA));
        return device;
    }

    private void stubPreviousLookup(TrustedDevice device) {
        String oldHash = SecureTokens.sha256Hex("old-token");
        when(trustedDeviceRepo.findByTokenHashAndKind(oldHash, KIND)).thenReturn(Optional.empty());
        when(trustedDeviceRepo.findFirstByPreviousTokenHashAndKind(oldHash, KIND)).thenReturn(Optional.of(device));
    }

    @Test
    void recognizeOnLogin_shouldRecordRotationTimeAndUserAgentHash_whenRotating() {
        TrustedDevice existing = device(5L, "Gabinet 1", "device-token");
        when(trustedDeviceRepo.findByTokenHashAndKind(existing.getTokenHash(), KIND)).thenReturn(Optional.of(existing));

        service.recognizeOnLogin("device-token", "anna", IP, IPAD_UA);

        assertEquals(NOW, existing.getRotatedAt());
        assertEquals(SecureTokens.sha256Hex(IPAD_UA), existing.getLastUserAgentHash());
        verify(clinicDeviceResolver).invalidateAllowanceCache();
    }

    @Test
    void recognizeOnLogin_shouldReissueTokenWithoutSuspicion_whenRetriedWithinGraceFromSameIpAndAgent() {
        TrustedDevice device = rotatedDevice(NOW.minusSeconds(90));
        stubPreviousLookup(device);

        ClinicDeviceLoginResult result = service.recognizeOnLogin("old-token", "anna", IP, IPAD_UA);

        assertEquals(ClinicDeviceLoginResult.Outcome.ROTATED, result.outcome());
        assertEquals(SecureTokens.sha256Hex(result.token()), device.getTokenHash());
        assertEquals(SecureTokens.sha256Hex("old-token"), device.getPreviousTokenHash());
        assertEquals(NOW.minusSeconds(90), device.getRotatedAt());
        assertNull(device.getSuspectedCopyAt());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void recognizeOnLogin_shouldFlagCopy_whenRetriedAfterGrace() {
        TrustedDevice device = rotatedDevice(NOW.minusSeconds(121));
        stubPreviousLookup(device);

        ClinicDeviceLoginResult result = service.recognizeOnLogin("old-token", "anna", IP, IPAD_UA);

        assertEquals(ClinicDeviceLoginResult.Outcome.REUSED, result.outcome());
        assertEquals(NOW, device.getSuspectedCopyAt());
        verify(auditLogService).logSecurity(eq("Security-Device-Reuse"), eq(5L), eq("Gabinet 1"), any(), eq("Gabinet 1"));
    }

    @Test
    void recognizeOnLogin_shouldFlagCopy_whenRetriedFromDifferentIp() {
        TrustedDevice device = rotatedDevice(NOW.minusSeconds(10));
        stubPreviousLookup(device);

        assertEquals(ClinicDeviceLoginResult.Outcome.REUSED,
                service.recognizeOnLogin("old-token", "anna", "10.0.0.77", IPAD_UA).outcome());
        assertEquals(NOW, device.getSuspectedCopyAt());
    }

    @Test
    void recognizeOnLogin_shouldFlagCopy_whenRetriedWithDifferentUserAgent() {
        TrustedDevice device = rotatedDevice(NOW.minusSeconds(10));
        stubPreviousLookup(device);

        assertEquals(ClinicDeviceLoginResult.Outcome.REUSED,
                service.recognizeOnLogin("old-token", "anna", IP, "Mozilla/5.0 (Windows NT 10.0) Chrome/130.0").outcome());
        assertEquals(NOW, device.getSuspectedCopyAt());
    }

    @Test
    void inspectForLogin_shouldReturnUnknown_whenNoCookie() {
        assertEquals(LoginDeviceCheck.Status.UNKNOWN_DEVICE, service.inspectForLogin(null, IP, IPAD_UA).status());
        verifyNoInteractions(trustedDeviceRepo);
    }

    @Test
    void inspectForLogin_shouldReturnAllowedWithName_whenCurrentTokenOfActiveDevice() {
        TrustedDevice existing = device(5L, "Gabinet 1", "device-token");
        when(trustedDeviceRepo.findByTokenHashAndKind(existing.getTokenHash(), KIND)).thenReturn(Optional.of(existing));

        LoginDeviceCheck check = service.inspectForLogin("device-token", IP, IPAD_UA);

        assertTrue(check.allowed());
        assertEquals("Gabinet 1", check.deviceName());
        verify(trustedDeviceRepo, never()).save(any());
    }

    @Test
    void inspectForLogin_shouldReturnSuspected_whenDeviceFlaggedAsCopy() {
        TrustedDevice existing = device(5L, "Gabinet 1", "device-token");
        existing.setSuspectedCopyAt(NOW.minusDays(1));
        when(trustedDeviceRepo.findByTokenHashAndKind(existing.getTokenHash(), KIND)).thenReturn(Optional.of(existing));

        LoginDeviceCheck check = service.inspectForLogin("device-token", IP, IPAD_UA);

        assertEquals(LoginDeviceCheck.Status.SUSPECTED_COPY, check.status());
        assertFalse(check.previousTokenReuse());
    }

    @Test
    void inspectForLogin_shouldReturnRevoked_whenDeviceRevoked() {
        TrustedDevice existing = device(5L, "Gabinet 1", "device-token");
        existing.setRevokedAt(NOW.minusDays(1));
        when(trustedDeviceRepo.findByTokenHashAndKind(existing.getTokenHash(), KIND)).thenReturn(Optional.of(existing));

        assertEquals(LoginDeviceCheck.Status.REVOKED, service.inspectForLogin("device-token", IP, IPAD_UA).status());
    }

    @Test
    void inspectForLogin_shouldAllowRotationRetryWithinGrace() {
        stubPreviousLookup(rotatedDevice(NOW.minusSeconds(30)));

        assertTrue(service.inspectForLogin("old-token", IP, IPAD_UA).allowed());
    }

    @Test
    void inspectForLogin_shouldReportPreviousTokenReuse_whenOutsideGrace() {
        stubPreviousLookup(rotatedDevice(NOW.minusMinutes(10)));

        LoginDeviceCheck check = service.inspectForLogin("old-token", IP, IPAD_UA);

        assertEquals(LoginDeviceCheck.Status.SUSPECTED_COPY, check.status());
        assertTrue(check.previousTokenReuse());
        assertEquals(5L, check.deviceId());
        verify(trustedDeviceRepo, never()).save(any());
    }

    @Test
    void recordLoginReuse_shouldFlagDeviceAndAudit() {
        TrustedDevice existing = device(5L, "Gabinet 1", "device-token");
        when(trustedDeviceRepo.findByIdAndKind(5L, KIND)).thenReturn(Optional.of(existing));

        service.recordLoginReuse(5L, "anna", IP);

        assertEquals(NOW, existing.getSuspectedCopyAt());
        verify(clinicDeviceResolver).invalidateAllowanceCache();
        verify(auditLogService).logSecurity(eq("Security-Device-Reuse"), eq(5L), eq("Gabinet 1"), any(), eq("Gabinet 1"));
    }

    @Test
    void revokeDevice_shouldInvalidateAllowanceCache() {
        when(trustedDeviceRepo.findByIdAndKind(5L, KIND)).thenReturn(Optional.of(device(5L, "Gabinet 1", "t")));

        service.revokeDevice(5L);

        verify(clinicDeviceResolver).invalidateAllowanceCache();
    }
}

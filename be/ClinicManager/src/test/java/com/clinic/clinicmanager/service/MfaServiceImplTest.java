package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.TrustedDeviceDTO;
import com.clinic.clinicmanager.DTO.response.MfaBackupCodesResponse;
import com.clinic.clinicmanager.DTO.response.MfaSetupResponse;
import com.clinic.clinicmanager.DTO.response.MfaStatusResponse;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.Role;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.service.impl.MfaBackupCodeManager;
import com.clinic.clinicmanager.service.impl.MfaCodeVerifier;
import com.clinic.clinicmanager.service.impl.MfaServiceImpl;
import com.clinic.clinicmanager.utils.MfaEncryption;
import com.clinic.clinicmanager.utils.TotpUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MfaServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final String USERNAME = "owner";
    private static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
    private static final String TEST_KEY = "PVdbHsyDQc4T9zo/QHAi/aBb3XpnEkxwqT8riR2+G4E=";
    private static final String SETUP_KEY = "mfa:setup:user:7";
    private static final List<String> CODES = List.of("abcd-2345", "efgh-6789");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Europe/Warsaw"));

    @Mock UserRepo userRepo;
    @Mock MfaCodeVerifier mfaCodeVerifier;
    @Mock MfaBackupCodeManager mfaBackupCodeManager;
    @Mock TrustedDeviceService trustedDeviceService;
    @Mock AuditLogService auditLogService;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private final MfaEncryption mfaEncryption = new MfaEncryption(TEST_KEY);
    private final TotpUtil totpUtil = new TotpUtil(CLOCK);

    private MfaServiceImpl mfaService;
    private User user;

    @BeforeEach
    void setUp() {
        mfaService = new MfaServiceImpl(userRepo, mfaCodeVerifier, mfaBackupCodeManager, trustedDeviceService,
                auditLogService, redisTemplate, mfaEncryption, totpUtil, CLOCK);
        Role masterRole = Role.builder().id(3L).name(RoleType.ROLE_MASTER).build();
        user = User.builder().id(USER_ID).username(USERNAME).password("hash").roles(Set.of(masterRole)).build();
        authenticateAs(new UserDetailsImpl(USER_ID, USERNAME, "hash", null, null,
                List.of(new SimpleGrantedAuthority("ROLE_MASTER"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(UserDetailsImpl principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void enableMfaOnUser() {
        user.setMfaSecretEnc(mfaEncryption.encrypt(SECRET));
        user.setMfaEnabledAt(LocalDateTime.of(2026, 10, 1, 9, 0));
    }

    private void verifyPurged() {
        verify(mfaBackupCodeManager).purge(USER_ID);
        verify(trustedDeviceService).revokeAllRemembered(USER_ID);
        verify(redisTemplate).delete(SETUP_KEY);
        verify(mfaCodeVerifier).reset(USER_ID);
    }

    @Test
    void setup_shouldStoreEncryptedPendingSecretAndReturnQr() {
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(SETUP_KEY), anyString(), eq(10L), eq(TimeUnit.MINUTES))).thenReturn(true);

        MfaSetupResponse response = mfaService.setup();

        assertTrue(response.getSecret().matches("[A-Z2-7]{32}"));
        assertTrue(response.getOtpauthUri().startsWith("otpauth://totp/"));
        assertTrue(response.getOtpauthUri().contains("issuer=ClinicManager"));
        assertTrue(response.getOtpauthUri().contains("secret=" + response.getSecret()));
        assertTrue(response.getQrDataUri().startsWith("data:image/png;base64,"));
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(valueOps).setIfAbsent(eq(SETUP_KEY), stored.capture(), eq(10L), eq(TimeUnit.MINUTES));
        assertNotEquals(response.getSecret(), stored.getValue());
        assertEquals(response.getSecret(), mfaEncryption.decrypt(stored.getValue()));
    }

    @Test
    void setup_shouldReturnSamePendingSecretOnRepeatedCallsAndNewOneAfterExpiry() {
        Map<String, String> redis = new HashMap<>();
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(SETUP_KEY), anyString(), eq(10L), eq(TimeUnit.MINUTES)))
                .thenAnswer(invocation -> redis.putIfAbsent(SETUP_KEY, invocation.getArgument(1)) == null);
        when(valueOps.get(SETUP_KEY)).thenAnswer(invocation -> redis.get(SETUP_KEY));

        MfaSetupResponse first = mfaService.setup();
        MfaSetupResponse second = mfaService.setup();

        assertEquals(first.getSecret(), second.getSecret());
        assertEquals(first.getOtpauthUri(), second.getOtpauthUri());
        assertEquals(first.getQrDataUri(), second.getQrDataUri());
        verify(redisTemplate).expire(SETUP_KEY, 10L, TimeUnit.MINUTES);

        redis.clear();
        MfaSetupResponse afterExpiry = mfaService.setup();

        assertNotEquals(first.getSecret(), afterExpiry.getSecret());
        assertEquals(afterExpiry.getSecret(), mfaEncryption.decrypt(redis.get(SETUP_KEY)));
    }

    @Test
    void setup_shouldStoreNewSecret_whenPendingExpiredBetweenChecks() {
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(SETUP_KEY), anyString(), eq(10L), eq(TimeUnit.MINUTES))).thenReturn(false);
        when(valueOps.get(SETUP_KEY)).thenReturn(null);

        MfaSetupResponse response = mfaService.setup();

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(SETUP_KEY), stored.capture(), eq(10L), eq(TimeUnit.MINUTES));
        assertEquals(response.getSecret(), mfaEncryption.decrypt(stored.getValue()));
    }

    @Test
    void setup_shouldReturn409_whenAlreadyEnabled() {
        enableMfaOnUser();
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));

        ConflictException ex = assertThrows(ConflictException.class, () -> mfaService.setup());

        assertEquals("Weryfikacja dwuetapowa jest już włączona.", ex.getMessage());
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void setup_shouldThrowAccessDenied_whenNoPrincipal() {
        SecurityContextHolder.clearContext();

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> mfaService.setup());

        assertEquals("Brak zalogowanego użytkownika.", ex.getMessage());
    }

    @Test
    void enable_shouldPersistEncryptedSecretAndActivateBackupCodes() {
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(SETUP_KEY)).thenReturn(mfaEncryption.encrypt(SECRET));
        when(mfaBackupCodeManager.replace(USER_ID)).thenReturn(CODES);

        MfaBackupCodesResponse response = mfaService.enable("123456");

        assertEquals(CODES, response.getBackupCodes());
        InOrder inOrder = inOrder(mfaCodeVerifier, userRepo);
        inOrder.verify(mfaCodeVerifier).verifyCode(user, SECRET, "123456");
        inOrder.verify(userRepo).save(user);
        assertEquals(SECRET, mfaEncryption.decrypt(user.getMfaSecretEnc()));
        assertEquals(LocalDateTime.now(CLOCK), user.getMfaEnabledAt());
        verify(redisTemplate).delete(SETUP_KEY);
        verify(trustedDeviceService).revokeAllRemembered(USER_ID);
        verify(auditLogService).logSecurity(eq("User-2FA-Enabled"), eq(USER_ID), eq(USERNAME), any());
    }

    @Test
    void enable_shouldReturn400_whenSetupExpired() {
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(SETUP_KEY)).thenReturn(null);

        ValidationException ex = assertThrows(ValidationException.class, () -> mfaService.enable("123456"));

        assertEquals("Konfiguracja wygasła. Zacznij od nowa.", ex.getMessage());
        verifyNoInteractions(mfaCodeVerifier, mfaBackupCodeManager);
        verify(userRepo, never()).save(any());
    }

    @Test
    void enable_shouldNotPersist_whenCodeRejected() {
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(SETUP_KEY)).thenReturn(mfaEncryption.encrypt(SECRET));
        doThrow(new ValidationException("Nieprawidłowy kod. Pozostało prób: 3."))
                .when(mfaCodeVerifier).verifyCode(user, SECRET, "000000");

        assertThrows(ValidationException.class, () -> mfaService.enable("000000"));

        assertNull(user.getMfaSecretEnc());
        verify(userRepo, never()).save(any());
        verifyNoInteractions(mfaBackupCodeManager, auditLogService);
    }

    @Test
    void enable_shouldReturn409_whenAlreadyEnabled() {
        enableMfaOnUser();
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));

        assertThrows(ConflictException.class, () -> mfaService.enable("123456"));
        verifyNoInteractions(mfaCodeVerifier);
    }

    @Test
    void disable_shouldClearSecretCodesAndDevices_whenCodeValid() {
        enableMfaOnUser();
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));

        mfaService.disable("123456");

        verify(mfaCodeVerifier).verifyCode(user, "123456");
        assertNull(user.getMfaSecretEnc());
        assertNull(user.getMfaEnabledAt());
        verify(userRepo).save(user);
        verifyPurged();
        verify(auditLogService).logSecurity(eq("User-2FA-Disabled"), eq(USER_ID), eq(USERNAME), any());
    }

    @Test
    void disable_shouldKeepEverything_whenCodeRejected() {
        enableMfaOnUser();
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));
        doThrow(new ValidationException("Nieprawidłowy kod. Pozostało prób: 4."))
                .when(mfaCodeVerifier).verifyCode(user, "000000");

        assertThrows(ValidationException.class, () -> mfaService.disable("000000"));

        assertNotNull(user.getMfaSecretEnc());
        verify(userRepo, never()).save(any());
        verifyNoInteractions(mfaBackupCodeManager, trustedDeviceService);
    }

    @Test
    void disable_shouldReturn409_whenMfaNotEnabled() {
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));

        ConflictException ex = assertThrows(ConflictException.class, () -> mfaService.disable("123456"));

        assertEquals("Weryfikacja dwuetapowa nie jest włączona.", ex.getMessage());
        verifyNoInteractions(mfaCodeVerifier);
    }

    @Test
    void regenerateBackupCodes_shouldPreparePendingCodesWithoutAudit() {
        enableMfaOnUser();
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));
        when(mfaBackupCodeManager.preparePending(USER_ID)).thenReturn(CODES);

        MfaBackupCodesResponse response = mfaService.regenerateBackupCodes("123456");

        assertEquals(CODES, response.getBackupCodes());
        verify(mfaCodeVerifier).verifyCode(user, "123456");
        verify(mfaBackupCodeManager, never()).replace(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void regenerateBackupCodes_shouldReturn409_whenMfaNotEnabled() {
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));

        ConflictException ex = assertThrows(ConflictException.class, () -> mfaService.regenerateBackupCodes("123456"));

        assertEquals("Weryfikacja dwuetapowa nie jest włączona.", ex.getMessage());
        verifyNoInteractions(mfaCodeVerifier, mfaBackupCodeManager);
    }

    @Test
    void confirmBackupCodes_shouldActivatePendingCodesAndAudit() {
        enableMfaOnUser();
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));
        when(mfaBackupCodeManager.confirmPending(USER_ID)).thenReturn(10);

        mfaService.confirmBackupCodes();

        verify(auditLogService).logSecurity("User-2FA-BackupCodes-Regenerated", USER_ID, USERNAME,
                Map.of("username", USERNAME, "count", 10));
    }

    @Test
    void confirmBackupCodes_shouldPropagate409_whenPendingCodesExpired() {
        enableMfaOnUser();
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));
        when(mfaBackupCodeManager.confirmPending(USER_ID))
                .thenThrow(new ConflictException("Nowe kody wygasły. Wygeneruj je ponownie."));

        ConflictException ex = assertThrows(ConflictException.class, () -> mfaService.confirmBackupCodes());

        assertEquals("Nowe kody wygasły. Wygeneruj je ponownie.", ex.getMessage());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void confirmBackupCodes_shouldReturn409_whenMfaNotEnabled() {
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));

        assertThrows(ConflictException.class, () -> mfaService.confirmBackupCodes());
        verifyNoInteractions(mfaBackupCodeManager);
    }

    @Test
    void getStatus_shouldReportEnabledStateCodesAndDevices() {
        enableMfaOnUser();
        TrustedDeviceDTO device = new TrustedDeviceDTO();
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));
        when(mfaBackupCodeManager.countRemaining(USER_ID)).thenReturn(8);
        when(trustedDeviceService.getActiveRemembered(USER_ID, "device-token")).thenReturn(List.of(device));

        MfaStatusResponse status = mfaService.getStatus("device-token");

        assertTrue(status.isEnabled());
        assertEquals(LocalDateTime.of(2026, 10, 1, 9, 0), status.getEnabledAt());
        assertEquals(8, status.getRemainingBackupCodes());
        assertEquals(List.of(device), status.getTrustedDevices());
    }

    @Test
    void getStatus_shouldReportDisabledWithoutCountingCodes() {
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));
        when(trustedDeviceService.getActiveRemembered(USER_ID, null)).thenReturn(List.of());

        MfaStatusResponse status = mfaService.getStatus(null);

        assertFalse(status.isEnabled());
        assertNull(status.getEnabledAt());
        assertEquals(0, status.getRemainingBackupCodes());
        verifyNoInteractions(mfaBackupCodeManager);
    }

    @Test
    void resetByAdmin_shouldReturn403_whenTargetIsAdmin() {
        Role adminRole = Role.builder().id(1L).name(RoleType.ROLE_ADMIN).build();
        User admin = User.builder().id(1L).username("admin").password("hash").roles(Set.of(adminRole)).build();
        admin.setMfaSecretEnc("v1:x");
        admin.setMfaEnabledAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        when(userRepo.findOneById(1L)).thenReturn(Optional.of(admin));

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> mfaService.resetByAdmin(1L));

        assertEquals("Nie można zresetować 2FA administratora w aplikacji.", ex.getMessage());
        verify(userRepo, never()).save(any());
        verifyNoInteractions(mfaBackupCodeManager, trustedDeviceService);
    }

    @Test
    void resetByAdmin_shouldReturn409_whenTargetHasNoMfa() {
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));

        ConflictException ex = assertThrows(ConflictException.class, () -> mfaService.resetByAdmin(USER_ID));

        assertEquals("Użytkownik nie ma włączonej weryfikacji dwuetapowej.", ex.getMessage());
    }

    @Test
    void resetByAdmin_shouldClearEverythingAndAuditPerformer() {
        enableMfaOnUser();
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));
        authenticateAs(new UserDetailsImpl(1L, "admin", "hash", null, null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        String username = mfaService.resetByAdmin(USER_ID);

        assertEquals(USERNAME, username);
        assertNull(user.getMfaSecretEnc());
        assertNull(user.getMfaEnabledAt());
        verifyPurged();
        verify(auditLogService).logSecurity("User-2FA-Reset", USER_ID, USERNAME, Map.of("username", USERNAME, "by", "admin"));
    }

    @Test
    void revokeTrustedDevice_shouldRevokeOwnDeviceAndAudit() {
        when(trustedDeviceService.revokeRemembered(USER_ID, 5L)).thenReturn("Chrome · Windows");

        mfaService.revokeTrustedDevice(5L);

        verify(auditLogService).logSecurity("User-2FA-TrustedDevice-Revoked", USER_ID, USERNAME,
                Map.of("username", USERNAME, "device", "Chrome · Windows"));
    }

    @Test
    void revokeAllTrustedDevices_shouldRevokeOwnDevicesAndAudit() {
        when(trustedDeviceService.revokeAllRemembered(USER_ID)).thenReturn(2);

        mfaService.revokeAllTrustedDevices();

        verify(auditLogService).logSecurity("User-2FA-TrustedDevice-Revoked", USER_ID, USERNAME,
                Map.of("username", USERNAME, "device", "wszystkie", "count", 2));
    }

    @Test
    void getTrustedDevicesOfUser_shouldReturnDevicesWithoutCurrentMarkerAndAudit() {
        List<TrustedDeviceDTO> devices = List.of(new TrustedDeviceDTO());
        when(userRepo.existsById(2L)).thenReturn(true);
        when(trustedDeviceService.getActiveRemembered(2L, null)).thenReturn(devices);

        assertSame(devices, mfaService.getTrustedDevicesOfUser(2L));
        verifyNoInteractions(auditLogService);
    }

    @Test
    void getTrustedDevicesOfUser_shouldReturn404_whenUserMissing() {
        when(userRepo.existsById(99L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> mfaService.getTrustedDevicesOfUser(99L));
        verifyNoInteractions(trustedDeviceService);
    }

    @Test
    void purgeMfaArtifacts_shouldClearCodesDevicesSetupAndVerifierState() {
        mfaService.purgeMfaArtifacts(USER_ID);

        verifyPurged();
    }
}

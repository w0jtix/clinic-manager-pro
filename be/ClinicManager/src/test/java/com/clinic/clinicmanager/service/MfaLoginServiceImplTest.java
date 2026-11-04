package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.MfaLoginResult;
import com.clinic.clinicmanager.exceptions.MfaSessionExpiredException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.service.impl.MfaBackupCodeManager;
import com.clinic.clinicmanager.service.impl.MfaCodeVerifier;
import com.clinic.clinicmanager.service.impl.MfaLoginServiceImpl;
import com.clinic.clinicmanager.utils.SecureTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MfaLoginServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final String USERNAME = "owner";
    private static final String PENDING_KEY = "mfa:pending:challenge";
    private static final String PENDING_VALUE = "7:" + SecureTokens.sha256Hex("hash");

    @Mock UserRepo userRepo;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock MfaCodeVerifier mfaCodeVerifier;
    @Mock MfaBackupCodeManager mfaBackupCodeManager;
    @Mock AuditLogService auditLogService;

    private MfaLoginServiceImpl loginService;
    private User user;

    @BeforeEach
    void setUp() {
        loginService = new MfaLoginServiceImpl(userRepo, redisTemplate, mfaCodeVerifier, mfaBackupCodeManager, auditLogService);
        user = User.builder().id(USER_ID).username(USERNAME).password("hash").build();
        user.setMfaSecretEnc("v1:secret");
        user.setMfaEnabledAt(LocalDateTime.of(2026, 10, 1, 9, 0));
    }

    private void stubPendingChallenge() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(PENDING_KEY)).thenReturn(PENDING_VALUE);
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    void isMfaEnabled_shouldReflectUserState() {
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));
        when(userRepo.findOneById(8L)).thenReturn(Optional.empty());

        assertTrue(loginService.isMfaEnabled(USER_ID));
        assertFalse(loginService.isMfaEnabled(8L));
    }

    @Test
    void startLoginChallenge_shouldStoreRandomTokenWithPasswordFingerprintAndFiveMinuteTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));

        String first = loginService.startLoginChallenge(USER_ID);
        String second = loginService.startLoginChallenge(USER_ID);

        assertNotEquals(first, second);
        assertTrue(first.length() >= 43);
        verify(valueOps).set("mfa:pending:" + first, PENDING_VALUE, 5L, TimeUnit.MINUTES);
    }

    @Test
    void verifyLoginCode_shouldAccept_whenPasswordFingerprintMatches() {
        stubPendingChallenge();

        assertEquals(new MfaLoginResult(USER_ID, USERNAME, null), loginService.verifyLoginCode("challenge", "123456"));
        verify(mfaCodeVerifier).verifyCode(user, "123456");
    }

    @Test
    void verifyLoginCode_shouldExpireAndDropToken_whenPasswordChangedAfterChallenge() {
        stubPendingChallenge();
        user.setPassword("new-hash");

        assertThrows(MfaSessionExpiredException.class, () -> loginService.verifyLoginCode("challenge", "123456"));

        verify(redisTemplate).delete(PENDING_KEY);
        verifyNoInteractions(mfaCodeVerifier);
    }

    @Test
    void verifyLoginBackupCode_shouldExpireAndDropToken_whenPasswordChangedAfterChallenge() {
        stubPendingChallenge();
        user.setPassword("admin-forced-hash");

        assertThrows(MfaSessionExpiredException.class, () -> loginService.verifyLoginBackupCode("challenge", "abcd-2345"));

        verify(redisTemplate).delete(PENDING_KEY);
        verifyNoInteractions(mfaBackupCodeManager, auditLogService);
    }

    @Test
    void verifyLoginCode_shouldTreatLegacyValueWithoutFingerprintAsExpired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(PENDING_KEY)).thenReturn("7");

        assertThrows(MfaSessionExpiredException.class, () -> loginService.verifyLoginCode("challenge", "123456"));

        verify(redisTemplate).delete(PENDING_KEY);
        verifyNoInteractions(userRepo, mfaCodeVerifier);
    }

    @Test
    void verifyLoginCode_shouldThrowSessionExpired_whenTokenUnknown() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("mfa:pending:missing")).thenReturn(null);

        MfaSessionExpiredException ex = assertThrows(MfaSessionExpiredException.class,
                () -> loginService.verifyLoginCode("missing", "123456"));

        assertEquals("Sesja weryfikacji wygasła. Zaloguj się ponownie.", ex.getMessage());
        verifyNoInteractions(mfaCodeVerifier);
    }

    @Test
    void verifyLoginCode_shouldThrowSessionExpiredAndDropToken_whenMfaNoLongerEnabled() {
        user.setMfaEnabledAt(null);
        stubPendingChallenge();

        assertThrows(MfaSessionExpiredException.class, () -> loginService.verifyLoginCode("challenge", "123456"));

        verify(redisTemplate).delete(PENDING_KEY);
        verifyNoInteractions(mfaCodeVerifier);
    }

    @Test
    void verifyLoginCode_shouldConsumeChallenge_whenCodeValid() {
        stubPendingChallenge();

        MfaLoginResult result = loginService.verifyLoginCode("challenge", "123456");

        assertEquals(new MfaLoginResult(USER_ID, USERNAME, null), result);
        verify(mfaCodeVerifier).verifyCode(user, "123456");
        verify(redisTemplate).delete(PENDING_KEY);
    }

    @Test
    void verifyLoginCode_shouldKeepChallenge_whenCodeWrong() {
        stubPendingChallenge();
        doThrow(new ValidationException("Nieprawidłowy kod. Pozostało prób: 4."))
                .when(mfaCodeVerifier).verifyCode(user, "000000");

        assertThrows(ValidationException.class, () -> loginService.verifyLoginCode("challenge", "000000"));

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void verifyLoginBackupCode_shouldReturnRemainingAndAudit() {
        stubPendingChallenge();
        when(mfaBackupCodeManager.consume(user, "abcd-2345")).thenReturn(9);

        MfaLoginResult result = loginService.verifyLoginBackupCode("challenge", "abcd-2345");

        assertEquals(new MfaLoginResult(USER_ID, USERNAME, 9), result);
        verify(redisTemplate).delete(PENDING_KEY);
        verify(auditLogService).logSecurity("User-2FA-BackupCode-Used", USER_ID, USERNAME,
                Map.of("username", USERNAME, "remainingBackupCodes", 9));
    }

    @Test
    void verifyLoginBackupCode_shouldKeepChallengeAndSkipAudit_whenCodeRejected() {
        stubPendingChallenge();
        when(mfaBackupCodeManager.consume(user, "zzzz-zzzz"))
                .thenThrow(new ValidationException("Kod zapasowy jest nieprawidłowy albo został już użyty."));

        assertThrows(ValidationException.class, () -> loginService.verifyLoginBackupCode("challenge", "zzzz-zzzz"));

        verify(redisTemplate, never()).delete(anyString());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void verifyLoginBackupCode_shouldThrowSessionExpired_whenTokenUnknown() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("mfa:pending:missing")).thenReturn(null);

        assertThrows(MfaSessionExpiredException.class, () -> loginService.verifyLoginBackupCode("missing", "abcd-2345"));
        verifyNoInteractions(mfaBackupCodeManager);
    }
}

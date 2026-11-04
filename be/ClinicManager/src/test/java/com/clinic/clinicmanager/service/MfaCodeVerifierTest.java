package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.exceptions.TooManyAttemptsException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.service.impl.MfaCodeVerifier;
import com.clinic.clinicmanager.utils.MfaEncryption;
import com.clinic.clinicmanager.utils.TotpUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MfaCodeVerifierTest {

    private static final Long USER_ID = 7L;
    private static final String USERNAME = "owner";
    private static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
    private static final String TEST_KEY = "PVdbHsyDQc4T9zo/QHAi/aBb3XpnEkxwqT8riR2+G4E=";
    private static final String LAST_STEP_KEY = "mfa:last-step:user:7";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Europe/Warsaw"));

    @Mock MfaAttemptService mfaAttemptService;
    @Mock StringRedisTemplate redisTemplate;

    private final MfaEncryption mfaEncryption = new MfaEncryption(TEST_KEY);
    private final TotpUtil totpUtil = new TotpUtil(CLOCK);

    private MfaCodeVerifier verifier;
    private User user;

    @BeforeEach
    void setUp() {
        verifier = new MfaCodeVerifier(mfaAttemptService, redisTemplate, mfaEncryption, totpUtil);
        user = User.builder().id(USER_ID).username(USERNAME).password("hash").build();
        user.setMfaSecretEnc(mfaEncryption.encrypt(SECRET));
    }

    private String currentCode() {
        return totpUtil.codeForStep(SECRET, totpUtil.currentStep());
    }

    private String wrongCode() {
        return currentCode().equals("000000") ? "111111" : "000000";
    }

    @SuppressWarnings("unchecked")
    private void stubStepConsumption(Long result) {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(LAST_STEP_KEY)), any(), any())).thenReturn(result);
    }

    @SuppressWarnings("unchecked")
    private void verifyCodeNeverCompared() {
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(), any());
    }

    @Test
    void verifyCode_shouldAcceptValidCodeWithSpacesAndRecordSuccess() {
        when(mfaAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.of(1));
        stubStepConsumption(1L);
        String code = currentCode();

        verifier.verifyCode(user, code.substring(0, 3) + " " + code.substring(3));

        verify(mfaAttemptService).recordSuccess(USER_ID);
        verify(mfaAttemptService, never()).recordFailure(any(), any(), anyInt());
    }

    @Test
    void verifyCode_shouldUseExplicitSecret_whenProvided() {
        user.setMfaSecretEnc(null);
        when(mfaAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.of(1));
        stubStepConsumption(1L);

        verifier.verifyCode(user, SECRET, currentCode());

        verify(mfaAttemptService).recordSuccess(USER_ID);
    }

    @Test
    void verifyCode_shouldReturnRemainingAttempts_whenCodeWrong() {
        when(mfaAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.of(2));
        when(mfaAttemptService.recordFailure(USER_ID, USERNAME, 2)).thenReturn(3);

        ValidationException ex = assertThrows(ValidationException.class, () -> verifier.verifyCode(user, wrongCode()));

        assertEquals("Nieprawidłowy kod. Pozostało prób: 3.", ex.getMessage());
        verify(mfaAttemptService, never()).recordSuccess(any());
    }

    @Test
    void verifyCode_shouldRejectReplayedCode_whenStepAlreadyUsed() {
        when(mfaAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.of(1));
        when(mfaAttemptService.recordFailure(USER_ID, USERNAME, 1)).thenReturn(4);
        stubStepConsumption(0L);

        ValidationException ex = assertThrows(ValidationException.class, () -> verifier.verifyCode(user, currentCode()));

        assertEquals("Nieprawidłowy kod. Pozostało prób: 4.", ex.getMessage());
        verify(mfaAttemptService, never()).recordSuccess(any());
    }

    @Test
    void verifyCode_shouldBlockWithoutComparingCode_whenUserIsBlocked() {
        when(mfaAttemptService.isBlocked(USER_ID)).thenReturn(true);
        when(mfaAttemptService.getBlockRemainingMinutes(USER_ID)).thenReturn(15L);

        TooManyAttemptsException ex = assertThrows(TooManyAttemptsException.class, () -> verifier.verifyCode(user, currentCode()));

        assertEquals("Zbyt wiele nieudanych prób. Weryfikacja jest zablokowana, spróbuj ponownie za 15 min.", ex.getMessage());
        verify(mfaAttemptService, never()).reserveAttempt(any());
        verify(mfaAttemptService, never()).recordFailure(any(), any(), anyInt());
        verifyCodeNeverCompared();
    }

    @Test
    void verifyCode_shouldBlockWithoutComparingCode_whenReservationRejected() {
        when(mfaAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.empty());
        when(mfaAttemptService.getBlockRemainingMinutes(USER_ID)).thenReturn(12L);

        TooManyAttemptsException ex = assertThrows(TooManyAttemptsException.class, () -> verifier.verifyCode(user, currentCode()));

        assertTrue(ex.getMessage().contains("za 12 min"));
        verify(mfaAttemptService, never()).recordFailure(any(), any(), anyInt());
        verifyCodeNeverCompared();
    }

    @Test
    void verifyCode_shouldBlock_whenFifthAttemptFails() {
        when(mfaAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.of(5));
        when(mfaAttemptService.recordFailure(USER_ID, USERNAME, 5)).thenReturn(0);
        when(mfaAttemptService.getBlockRemainingMinutes(USER_ID)).thenReturn(15L);

        assertThrows(TooManyAttemptsException.class, () -> verifier.verifyCode(user, wrongCode()));
    }

    @Test
    void registerFailure_shouldThrowCustomMessage_belowThreshold() {
        when(mfaAttemptService.recordFailure(USER_ID, USERNAME, 1)).thenReturn(4);

        ValidationException ex = assertThrows(ValidationException.class,
                () -> verifier.registerFailure(user, 1, remaining -> "Zły kod zapasowy."));

        assertEquals("Zły kod zapasowy.", ex.getMessage());
    }

    @Test
    void reset_shouldClearReplayStateAndAttempts() {
        verifier.reset(USER_ID);

        verify(redisTemplate).delete(LAST_STEP_KEY);
        verify(mfaAttemptService).reset(USER_ID);
    }
}

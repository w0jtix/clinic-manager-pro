package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.service.impl.MfaAttemptServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MfaAttemptServiceImplTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock AuditLogService auditLogService;

    @InjectMocks
    MfaAttemptServiceImpl attemptService;

    private static final Long USER_ID = 7L;
    private static final String USERNAME = "owner";
    private static final String ATTEMPTS_KEY = "mfa:attempts:user:7";
    private static final String BLOCK_KEY = "mfa:block:user:7";

    @Test
    @SuppressWarnings("unchecked")
    void reserveAttempt_shouldUseMfaKeysAndLimits() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(ATTEMPTS_KEY, BLOCK_KEY)),
                eq("86400"), eq("5"), eq("900"))).thenReturn(2L);

        assertEquals(OptionalInt.of(2), attemptService.reserveAttempt(USER_ID));
    }

    @Test
    void recordFailure_shouldAuditMfaFailureWithAttempts_belowThreshold() {
        assertEquals(3, attemptService.recordFailure(USER_ID, USERNAME, 2));

        verify(auditLogService).logSecurityInNewTransaction("Security-2FA-Failure", USER_ID, USERNAME,
                Map.of("username", USERNAME, "attempts", 2));
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void recordFailure_shouldBlockFor15MinutesAndAudit_atFifthAttempt() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        assertEquals(0, attemptService.recordFailure(USER_ID, USERNAME, 5));

        verify(valueOps).set(BLOCK_KEY, "1", 15L, TimeUnit.MINUTES);
        verify(redisTemplate).delete(ATTEMPTS_KEY);
        verify(auditLogService).logSecurityInNewTransaction("Security-2FA-Block", USER_ID, USERNAME,
                Map.of("username", USERNAME, "attempts", 5, "blockMinutes", 15L));
    }

    @Test
    void isBlocked_shouldCheckMfaBlockKey() {
        when(redisTemplate.hasKey(BLOCK_KEY)).thenReturn(true);

        assertTrue(attemptService.isBlocked(USER_ID));
    }

    @Test
    void getBlockRemainingMinutes_shouldFallbackTo15Minutes_whenNoTtl() {
        when(redisTemplate.getExpire(BLOCK_KEY, TimeUnit.SECONDS)).thenReturn(null);

        assertEquals(15L, attemptService.getBlockRemainingMinutes(USER_ID));
    }
}

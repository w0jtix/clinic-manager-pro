package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.service.impl.CompanyPinAttemptServiceImpl;
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
class CompanyPinAttemptServiceImplTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock AuditLogService auditLogService;

    @InjectMocks
    CompanyPinAttemptServiceImpl attemptService;

    private static final Long USER_ID = 7L;
    private static final String USERNAME = "owner";
    private static final String ATTEMPTS_KEY = "company-pin:attempts:user:7";
    private static final String BLOCK_KEY = "company-pin:block:user:7";

    @Test
    @SuppressWarnings("unchecked")
    void reserveAttempt_shouldUseCompanyPinKeysAndLimits() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(ATTEMPTS_KEY, BLOCK_KEY)),
                eq("86400"), eq("5"), eq("900"))).thenReturn(3L);

        assertEquals(OptionalInt.of(3), attemptService.reserveAttempt(USER_ID));
    }

    @Test
    void recordFailure_shouldAuditPinFailureWithAttempts_belowThreshold() {
        assertEquals(4, attemptService.recordFailure(USER_ID, USERNAME, 1));

        verify(auditLogService).logSecurityInNewTransaction("Security-PinFailure", USER_ID, USERNAME,
                Map.of("username", USERNAME, "attempts", 1));
    }

    @Test
    void recordFailure_shouldBlockFor15MinutesAndAuditPinBlock_atFifthAttempt() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        assertEquals(0, attemptService.recordFailure(USER_ID, USERNAME, 5));

        verify(valueOps).set(BLOCK_KEY, "1", 15L, TimeUnit.MINUTES);
        verify(redisTemplate).delete(ATTEMPTS_KEY);
        verify(auditLogService).logSecurityInNewTransaction("Security-PinBlock", USER_ID, USERNAME,
                Map.of("username", USERNAME, "attempts", 5, "blockMinutes", 15L));
    }

    @Test
    void getBlockRemainingMinutes_shouldFallbackTo15Minutes_whenNoTtl() {
        when(redisTemplate.getExpire(BLOCK_KEY, TimeUnit.SECONDS)).thenReturn(-2L);

        assertEquals(15L, attemptService.getBlockRemainingMinutes(USER_ID));
    }

    @Test
    void reset_shouldClearCompanyPinAttemptsAndBlock() {
        attemptService.reset(USER_ID);

        verify(redisTemplate).delete(ATTEMPTS_KEY);
        verify(redisTemplate).delete(BLOCK_KEY);
    }
}

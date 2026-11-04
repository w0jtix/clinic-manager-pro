package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.service.impl.RedisAttemptLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
class RedisAttemptLimiterTest {

    private static final RedisAttemptLimiter.Config CONFIG =
            new RedisAttemptLimiter.Config("test-limiter", 3, 10L, 2L, "Test-Failure", "Test-Block");
    private static final Long USER_ID = 7L;
    private static final String USERNAME = "owner";
    private static final String ATTEMPTS_KEY = "test-limiter:attempts:user:7";
    private static final String BLOCK_KEY = "test-limiter:block:user:7";
    private static final String IP_SUBJECT = SubjectAttemptLimiter.ipSubject("10.0.0.9");
    private static final String IP_ATTEMPTS_KEY = "test-limiter:attempts:ip:10.0.0.9";
    private static final String IP_BLOCK_KEY = "test-limiter:block:ip:10.0.0.9";

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock AuditLogService auditLogService;

    private RedisAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new RedisAttemptLimiter(redisTemplate, auditLogService, CONFIG) {
        };
    }

    @SuppressWarnings("unchecked")
    private void stubReserveScript(Long result) {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(ATTEMPTS_KEY, BLOCK_KEY)),
                eq("7200"), eq("3"), eq("600"))).thenReturn(result);
    }

    @Test
    void reserveAttempt_shouldReturnAttemptNumber_fromAtomicScriptWithConfiguredLimits() {
        stubReserveScript(2L);

        assertEquals(OptionalInt.of(2), limiter.reserveAttempt(USER_ID));
        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void reserveAttempt_shouldReturnEmpty_whenScriptRejectsOverLimitOrBlocked() {
        stubReserveScript(-1L);

        assertTrue(limiter.reserveAttempt(USER_ID).isEmpty());
    }

    @Test
    void reserveAttempt_shouldReturnEmpty_whenScriptReturnsNull() {
        stubReserveScript(null);

        assertTrue(limiter.reserveAttempt(USER_ID).isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reserveAttempt_scriptShouldCheckBlockIncrementAndSetTtlAtomically() {
        ArgumentCaptor<RedisScript<Long>> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        when(redisTemplate.execute(scriptCaptor.capture(), anyList(), any(), any(), any())).thenReturn(1L);

        limiter.reserveAttempt(USER_ID);

        String script = scriptCaptor.getValue().getScriptAsString();
        assertTrue(script.contains("EXISTS"));
        assertTrue(script.contains("INCR"));
        assertTrue(script.contains("EXPIRE"));
        assertTrue(script.indexOf("EXISTS") < script.indexOf("INCR"));
        assertEquals(Long.class, scriptCaptor.getValue().getResultType());
    }

    @Test
    void recordFailure_shouldAuditFailureAndReturnRemaining_belowThreshold() {
        assertEquals(2, limiter.recordFailure(USER_ID, USERNAME, 1));

        verify(auditLogService).logSecurityInNewTransaction("Test-Failure", USER_ID, USERNAME,
                Map.of("username", USERNAME, "attempts", 1));
        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void recordFailure_shouldNotBlock_onLastAllowedFailure() {
        assertEquals(1, limiter.recordFailure(USER_ID, USERNAME, 2));

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void recordFailure_shouldBlockForConfiguredDurationAndAudit_atThreshold() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        assertEquals(0, limiter.recordFailure(USER_ID, USERNAME, 3));

        InOrder inOrder = inOrder(valueOps, redisTemplate);
        inOrder.verify(valueOps).set(BLOCK_KEY, "1", 10L, TimeUnit.MINUTES);
        inOrder.verify(redisTemplate).delete(ATTEMPTS_KEY);
        verify(auditLogService).logSecurityInNewTransaction("Test-Block", USER_ID, USERNAME,
                Map.of("username", USERNAME, "attempts", 3, "blockMinutes", 10L));
        verify(auditLogService, never()).logSecurityInNewTransaction(eq("Test-Failure"), any(), any(), any());
    }

    @Test
    void isBlocked_shouldReflectBlockKey() {
        when(redisTemplate.hasKey(BLOCK_KEY)).thenReturn(true, false);

        assertTrue(limiter.isBlocked(USER_ID));
        assertFalse(limiter.isBlocked(USER_ID));
    }

    @Test
    void getBlockRemainingMinutes_shouldRoundUp() {
        when(redisTemplate.getExpire(BLOCK_KEY, TimeUnit.SECONDS)).thenReturn(61L);

        assertEquals(2L, limiter.getBlockRemainingMinutes(USER_ID));
    }

    @Test
    void getBlockRemainingMinutes_shouldFallbackToConfiguredDuration_whenNoTtl() {
        when(redisTemplate.getExpire(BLOCK_KEY, TimeUnit.SECONDS)).thenReturn(-2L);

        assertEquals(10L, limiter.getBlockRemainingMinutes(USER_ID));
    }

    @Test
    void recordSuccess_shouldClearAttemptsOnly() {
        limiter.recordSuccess(USER_ID);

        verify(redisTemplate).delete(ATTEMPTS_KEY);
        verify(redisTemplate, never()).delete(BLOCK_KEY);
    }

    @Test
    void reset_shouldClearAttemptsAndBlock() {
        limiter.reset(USER_ID);

        verify(redisTemplate).delete(ATTEMPTS_KEY);
        verify(redisTemplate).delete(BLOCK_KEY);
    }

    @Test
    void userSubject_shouldKeepExistingUserKeyFormat() {
        assertEquals("user:7", SubjectAttemptLimiter.userSubject(USER_ID));
        assertEquals("ip:10.0.0.9", IP_SUBJECT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reserveAttempt_shouldUseSubjectKeys_forTextSubject() {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(IP_ATTEMPTS_KEY, IP_BLOCK_KEY)),
                eq("7200"), eq("3"), eq("600"))).thenReturn(1L);

        assertEquals(OptionalInt.of(1), limiter.reserveAttempt(IP_SUBJECT));
    }

    @Test
    void recordFailure_shouldAuditWithSubjectAndNoEntityId_forTextSubject() {
        assertEquals(2, limiter.recordFailure(IP_SUBJECT, 1));

        verify(auditLogService).logSecurityInNewTransaction("Test-Failure", null, IP_SUBJECT,
                Map.of("subject", IP_SUBJECT, "attempts", 1));
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void recordFailure_shouldBlockSubjectAndAudit_atThreshold_forTextSubject() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        assertEquals(0, limiter.recordFailure(IP_SUBJECT, 3));

        verify(valueOps).set(IP_BLOCK_KEY, "1", 10L, TimeUnit.MINUTES);
        verify(redisTemplate).delete(IP_ATTEMPTS_KEY);
        verify(auditLogService).logSecurityInNewTransaction("Test-Block", null, IP_SUBJECT,
                Map.of("subject", IP_SUBJECT, "attempts", 3, "blockMinutes", 10L));
    }

    @Test
    void isBlockedAndRemainingMinutes_shouldUseSubjectBlockKey_forTextSubject() {
        when(redisTemplate.hasKey(IP_BLOCK_KEY)).thenReturn(true);
        when(redisTemplate.getExpire(IP_BLOCK_KEY, TimeUnit.SECONDS)).thenReturn(120L);

        assertTrue(limiter.isBlocked(IP_SUBJECT));
        assertEquals(2L, limiter.getBlockRemainingMinutes(IP_SUBJECT));
        verify(redisTemplate, never()).hasKey(BLOCK_KEY);
    }

    @Test
    void recordSuccessAndReset_shouldUseSubjectKeys_forTextSubject() {
        limiter.recordSuccess(IP_SUBJECT);
        limiter.reset(IP_SUBJECT);

        verify(redisTemplate, times(2)).delete(IP_ATTEMPTS_KEY);
        verify(redisTemplate).delete(IP_BLOCK_KEY);
        verify(redisTemplate, never()).delete(ATTEMPTS_KEY);
    }

    @Test
    void recordFailure_shouldStillReturnRemaining_whenAuditFails() {
        doThrow(new RuntimeException("audit down")).when(auditLogService)
                .logSecurityInNewTransaction(anyString(), any(), anyString(), any());

        assertEquals(2, limiter.recordFailure(IP_SUBJECT, 1));
    }

    @Test
    void recordFailure_shouldStillBlock_whenBlockAuditFails() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        doThrow(new RuntimeException("audit down")).when(auditLogService)
                .logSecurityInNewTransaction(anyString(), any(), anyString(), any());

        assertEquals(0, limiter.recordFailure(IP_SUBJECT, 3));
        verify(valueOps).set(IP_BLOCK_KEY, "1", 10L, TimeUnit.MINUTES);
    }

    @Test
    void recordFailure_shouldSkipFailureAuditButKeepBlockAudit_whenFailureAuditDisabled() {
        RedisAttemptLimiter quietLimiter = new RedisAttemptLimiter(redisTemplate, auditLogService,
                new RedisAttemptLimiter.Config("quiet", 3, 10L, 2L, "Quiet-Failure", "Quiet-Block", false)) {
        };
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        assertEquals(2, quietLimiter.recordFailure(IP_SUBJECT, 1));
        assertEquals(0, quietLimiter.recordFailure(IP_SUBJECT, 3));

        verify(auditLogService, never()).logSecurityInNewTransaction(eq("Quiet-Failure"), any(), any(), any());
        verify(auditLogService).logSecurityInNewTransaction("Quiet-Block", null, IP_SUBJECT,
                Map.of("subject", IP_SUBJECT, "attempts", 3, "blockMinutes", 10L));
    }
}

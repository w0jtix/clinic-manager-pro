package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.model.constants.LoginAttemptScope;
import com.clinic.clinicmanager.service.impl.LoginAttemptServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceImplTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock AuditLogService auditLogService;

    @InjectMocks
    LoginAttemptServiceImpl loginAttemptService;

    private static final String IP       = "192.168.1.1";
    private static final String USERNAME = "testuser";

    private void stubIncrement(long ipCount, long userCount) {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("login:attempts:ip:" + IP)).thenReturn(ipCount);
        when(valueOps.increment("login:attempts:username:" + USERNAME)).thenReturn(userCount);
    }

    @Test
    void recordFailure_shouldIncrementCounters_andNotBlock_whenBelowThreshold() {
        stubIncrement(2L, 2L);

        loginAttemptService.recordFailure(IP, USERNAME, LoginAttemptScope.IP_AND_USER);

        verify(valueOps).increment("login:attempts:ip:" + IP);
        verify(valueOps).increment("login:attempts:username:" + USERNAME);
        verify(valueOps, never()).set(contains("block"), any(), anyLong(), any());
        verify(valueOps, never()).set(contains("shadowban"), any(), anyLong(), any());
    }

    @Test
    void recordFailure_shouldSetExpiry_whenCounterReachesOne() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("login:attempts:ip:" + IP)).thenReturn(1L);
        when(valueOps.increment("login:attempts:username:" + USERNAME)).thenReturn(1L);

        loginAttemptService.recordFailure(IP, USERNAME, LoginAttemptScope.IP_AND_USER);

        verify(redisTemplate).expire("login:attempts:ip:" + IP, 24, TimeUnit.HOURS);
        verify(redisTemplate).expire("login:attempts:username:" + USERNAME, 24, TimeUnit.HOURS);
    }

    @Test
    void recordFailure_shouldBlock_whenIpHitsBlockThreshold() {
        stubIncrement(5L, 3L);

        loginAttemptService.recordFailure(IP, USERNAME, LoginAttemptScope.IP_AND_USER);

        verify(valueOps).set(eq("login:block:ip:" + IP), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(valueOps).set(eq("login:block:username:" + USERNAME), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(auditLogService).logSecurity(eq("Security-Block"), anyLong(), eq(USERNAME), any());
    }

    @Test
    void recordFailure_shouldBlock_whenUsernameHitsBlockThreshold() {
        stubIncrement(3L, 5L);

        loginAttemptService.recordFailure(IP, USERNAME, LoginAttemptScope.IP_AND_USER);

        verify(valueOps).set(eq("login:block:ip:" + IP), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(valueOps).set(eq("login:block:username:" + USERNAME), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void recordFailure_shouldShadowBan_whenIpHitsShadowBanThreshold() {
        stubIncrement(10L, 3L);

        loginAttemptService.recordFailure(IP, USERNAME, LoginAttemptScope.IP_AND_USER);

        verify(valueOps).set(eq("login:shadowban:ip:" + IP), eq("1"), eq(24L), eq(TimeUnit.HOURS));
        verify(valueOps).set(eq("login:shadowban:username:" + USERNAME), eq("1"), eq(24L), eq(TimeUnit.HOURS));
        verify(auditLogService).logSecurity(eq("Security-ShadowBan"), anyLong(), eq(USERNAME), any());
    }

    @Test
    void recordFailure_shouldShadowBan_whenUsernameHitsShadowBanThreshold() {
        stubIncrement(3L, 10L);

        loginAttemptService.recordFailure(IP, USERNAME, LoginAttemptScope.IP_AND_USER);

        verify(valueOps).set(eq("login:shadowban:ip:" + IP), eq("1"), eq(24L), eq(TimeUnit.HOURS));
        verify(valueOps).set(eq("login:shadowban:username:" + USERNAME), eq("1"), eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    void recordFailure_shouldNotBlock_whenCountAbove5ButBelow10() {
        stubIncrement(7L, 7L);

        loginAttemptService.recordFailure(IP, USERNAME, LoginAttemptScope.IP_AND_USER);

        verify(valueOps, never()).set(contains("block"), any(), anyLong(), any());
        verify(valueOps, never()).set(contains("shadowban"), any(), anyLong(), any());
    }

    @Test
    void recordSuccess_shouldDeleteAllCountersAndBlocks() {
        loginAttemptService.recordSuccess(IP, USERNAME, LoginAttemptScope.IP_AND_USER);

        verify(redisTemplate).delete("login:attempts:ip:" + IP);
        verify(redisTemplate).delete("login:attempts:username:" + USERNAME);
        verify(redisTemplate).delete("login:block:ip:" + IP);
        verify(redisTemplate).delete("login:block:username:" + USERNAME);
    }

    @Test
    void isBlocked_shouldReturnTrue_whenIpIsBlocked() {
        when(redisTemplate.hasKey("login:block:ip:" + IP)).thenReturn(true);

        assertTrue(loginAttemptService.isBlocked(IP, USERNAME, LoginAttemptScope.IP_AND_USER));
    }

    @Test
    void isBlocked_shouldReturnTrue_whenUsernameIsBlocked() {
        when(redisTemplate.hasKey("login:block:ip:" + IP)).thenReturn(false);
        when(redisTemplate.hasKey("login:block:username:" + USERNAME)).thenReturn(true);

        assertTrue(loginAttemptService.isBlocked(IP, USERNAME, LoginAttemptScope.IP_AND_USER));
    }

    @Test
    void isBlocked_shouldReturnFalse_whenNeitherIsBlocked() {
        when(redisTemplate.hasKey("login:block:ip:" + IP)).thenReturn(false);
        when(redisTemplate.hasKey("login:block:username:" + USERNAME)).thenReturn(false);

        assertFalse(loginAttemptService.isBlocked(IP, USERNAME, LoginAttemptScope.IP_AND_USER));
    }

    @Test
    void isShadowBanned_shouldReturnTrue_whenIpIsShadowBanned() {
        when(redisTemplate.hasKey("login:shadowban:ip:" + IP)).thenReturn(true);

        assertTrue(loginAttemptService.isShadowBanned(IP, USERNAME, LoginAttemptScope.IP_AND_USER));
    }

    @Test
    void isShadowBanned_shouldReturnTrue_whenUsernameIsShadowBanned() {
        when(redisTemplate.hasKey("login:shadowban:ip:" + IP)).thenReturn(false);
        when(redisTemplate.hasKey("login:shadowban:username:" + USERNAME)).thenReturn(true);

        assertTrue(loginAttemptService.isShadowBanned(IP, USERNAME, LoginAttemptScope.IP_AND_USER));
    }

    @Test
    void isShadowBanned_shouldReturnFalse_whenNeitherIsShadowBanned() {
        when(redisTemplate.hasKey("login:shadowban:ip:" + IP)).thenReturn(false);
        when(redisTemplate.hasKey("login:shadowban:username:" + USERNAME)).thenReturn(false);

        assertFalse(loginAttemptService.isShadowBanned(IP, USERNAME, LoginAttemptScope.IP_AND_USER));
    }

    @Test
    void recordFailure_shouldOnlyCountUsername_whenUserOnlyScope() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("login:attempts:clinic:username:" + USERNAME)).thenReturn(2L);

        loginAttemptService.recordFailure(IP, USERNAME, LoginAttemptScope.USER_ONLY);

        verify(valueOps, never()).increment("login:attempts:ip:" + IP);
        verify(valueOps, never()).set(contains("block"), any(), anyLong(), any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void recordFailure_shouldBlockOnlyUsernameAndAuditScope_whenUserOnlyScopeHitsBlockThreshold() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("login:attempts:clinic:username:" + USERNAME)).thenReturn(5L);

        loginAttemptService.recordFailure(IP, USERNAME, LoginAttemptScope.USER_ONLY);

        verify(valueOps).set(eq("login:block:clinic:username:" + USERNAME), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(valueOps, never()).set(eq("login:block:ip:" + IP), any(), anyLong(), any());
        verify(auditLogService).logSecurity("Security-Block", 0L, USERNAME,
                Map.of("ip", IP, "username", USERNAME, "userAttempts", 5L, "scope", "USER_ONLY"));
    }

    @Test
    void recordFailure_shouldShadowBanOnlyUsername_whenUserOnlyScopeHitsShadowBanThreshold() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("login:attempts:clinic:username:" + USERNAME)).thenReturn(10L);

        loginAttemptService.recordFailure(IP, USERNAME, LoginAttemptScope.USER_ONLY);

        verify(valueOps).set("login:shadowban:clinic:username:" + USERNAME, "1", 24L, TimeUnit.HOURS);
        verify(valueOps, never()).set(eq("login:shadowban:ip:" + IP), any(), anyLong(), any());
        verify(auditLogService).logSecurity("Security-ShadowBan", 0L, USERNAME,
                Map.of("ip", IP, "username", USERNAME, "userAttempts", 10L, "scope", "USER_ONLY"));
    }

    @Test
    void isBlocked_shouldIgnoreIpBlock_whenUserOnlyScope() {
        when(redisTemplate.hasKey("login:block:clinic:username:" + USERNAME)).thenReturn(false);

        assertFalse(loginAttemptService.isBlocked(IP, USERNAME, LoginAttemptScope.USER_ONLY));
        verify(redisTemplate, never()).hasKey("login:block:ip:" + IP);
    }

    @Test
    void isBlocked_shouldHonourUsernameBlock_whenUserOnlyScope() {
        when(redisTemplate.hasKey("login:block:clinic:username:" + USERNAME)).thenReturn(true);

        assertTrue(loginAttemptService.isBlocked(IP, USERNAME, LoginAttemptScope.USER_ONLY));
    }

    @Test
    void isShadowBanned_shouldIgnoreIpShadowBan_whenUserOnlyScope() {
        when(redisTemplate.hasKey("login:shadowban:clinic:username:" + USERNAME)).thenReturn(false);

        assertFalse(loginAttemptService.isShadowBanned(IP, USERNAME, LoginAttemptScope.USER_ONLY));
        verify(redisTemplate, never()).hasKey("login:shadowban:ip:" + IP);
    }

    @Test
    void isShadowBanned_shouldHonourUsernameShadowBan_whenUserOnlyScope() {
        when(redisTemplate.hasKey("login:shadowban:clinic:username:" + USERNAME)).thenReturn(true);

        assertTrue(loginAttemptService.isShadowBanned(IP, USERNAME, LoginAttemptScope.USER_ONLY));
    }

    @Test
    void recordFailure_shouldNotTouchExternalUsernameCounter_whenUserOnlyScope() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment("login:attempts:clinic:username:" + USERNAME)).thenReturn(1L);

        loginAttemptService.recordFailure(IP, USERNAME, LoginAttemptScope.USER_ONLY);

        verify(valueOps, never()).increment("login:attempts:username:" + USERNAME);
        verify(redisTemplate).expire("login:attempts:clinic:username:" + USERNAME, 24, TimeUnit.HOURS);
    }

    @Test
    void isBlocked_shouldIgnoreExternalUsernameBlock_whenUserOnlyScope() {
        when(redisTemplate.hasKey("login:block:clinic:username:" + USERNAME)).thenReturn(false);

        assertFalse(loginAttemptService.isBlocked(IP, USERNAME, LoginAttemptScope.USER_ONLY));
        verify(redisTemplate, never()).hasKey("login:block:username:" + USERNAME);
    }

    @Test
    void isBlocked_shouldIgnoreClinicBlock_whenIpAndUserScope() {
        when(redisTemplate.hasKey("login:block:ip:" + IP)).thenReturn(false);
        when(redisTemplate.hasKey("login:block:username:" + USERNAME)).thenReturn(false);

        assertFalse(loginAttemptService.isBlocked(IP, USERNAME, LoginAttemptScope.IP_AND_USER));
        verify(redisTemplate, never()).hasKey("login:block:clinic:username:" + USERNAME);
    }

    @Test
    void recordSuccess_shouldClearOnlyClinicKeys_whenUserOnlyScope() {
        loginAttemptService.recordSuccess(IP, USERNAME, LoginAttemptScope.USER_ONLY);

        verify(redisTemplate).delete("login:attempts:clinic:username:" + USERNAME);
        verify(redisTemplate).delete("login:block:clinic:username:" + USERNAME);
        verify(redisTemplate, never()).delete("login:attempts:ip:" + IP);
        verify(redisTemplate, never()).delete("login:attempts:username:" + USERNAME);
        verify(redisTemplate, never()).delete("login:block:username:" + USERNAME);
    }
}

package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.service.impl.LoginAttemptServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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

        loginAttemptService.recordFailure(IP, USERNAME);

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

        loginAttemptService.recordFailure(IP, USERNAME);

        verify(redisTemplate).expire("login:attempts:ip:" + IP, 24, TimeUnit.HOURS);
        verify(redisTemplate).expire("login:attempts:username:" + USERNAME, 24, TimeUnit.HOURS);
    }

    @Test
    void recordFailure_shouldBlock_whenIpHitsBlockThreshold() {
        stubIncrement(5L, 3L);

        loginAttemptService.recordFailure(IP, USERNAME);

        verify(valueOps).set(eq("login:block:ip:" + IP), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(valueOps).set(eq("login:block:username:" + USERNAME), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(auditLogService).logCreate(eq("Security-Block"), anyLong(), eq(USERNAME), any());
    }

    @Test
    void recordFailure_shouldBlock_whenUsernameHitsBlockThreshold() {
        stubIncrement(3L, 5L);

        loginAttemptService.recordFailure(IP, USERNAME);

        verify(valueOps).set(eq("login:block:ip:" + IP), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
        verify(valueOps).set(eq("login:block:username:" + USERNAME), eq("1"), anyLong(), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void recordFailure_shouldShadowBan_whenIpHitsShadowBanThreshold() {
        stubIncrement(10L, 3L);

        loginAttemptService.recordFailure(IP, USERNAME);

        verify(valueOps).set(eq("login:shadowban:ip:" + IP), eq("1"), eq(24L), eq(TimeUnit.HOURS));
        verify(valueOps).set(eq("login:shadowban:username:" + USERNAME), eq("1"), eq(24L), eq(TimeUnit.HOURS));
        verify(auditLogService).logCreate(eq("Security-ShadowBan"), anyLong(), eq(USERNAME), any());
    }

    @Test
    void recordFailure_shouldShadowBan_whenUsernameHitsShadowBanThreshold() {
        stubIncrement(3L, 10L);

        loginAttemptService.recordFailure(IP, USERNAME);

        verify(valueOps).set(eq("login:shadowban:ip:" + IP), eq("1"), eq(24L), eq(TimeUnit.HOURS));
        verify(valueOps).set(eq("login:shadowban:username:" + USERNAME), eq("1"), eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    void recordFailure_shouldNotBlock_whenCountAbove5ButBelow10() {
        stubIncrement(7L, 7L);

        loginAttemptService.recordFailure(IP, USERNAME);

        verify(valueOps, never()).set(contains("block"), any(), anyLong(), any());
        verify(valueOps, never()).set(contains("shadowban"), any(), anyLong(), any());
    }

    @Test
    void recordSuccess_shouldDeleteAllCountersAndBlocks() {
        loginAttemptService.recordSuccess(IP, USERNAME);

        verify(redisTemplate).delete("login:attempts:ip:" + IP);
        verify(redisTemplate).delete("login:attempts:username:" + USERNAME);
        verify(redisTemplate).delete("login:block:ip:" + IP);
        verify(redisTemplate).delete("login:block:username:" + USERNAME);
    }

    @Test
    void isBlocked_shouldReturnTrue_whenIpIsBlocked() {
        when(redisTemplate.hasKey("login:block:ip:" + IP)).thenReturn(true);

        assertTrue(loginAttemptService.isBlocked(IP, USERNAME));
    }

    @Test
    void isBlocked_shouldReturnTrue_whenUsernameIsBlocked() {
        when(redisTemplate.hasKey("login:block:ip:" + IP)).thenReturn(false);
        when(redisTemplate.hasKey("login:block:username:" + USERNAME)).thenReturn(true);

        assertTrue(loginAttemptService.isBlocked(IP, USERNAME));
    }

    @Test
    void isBlocked_shouldReturnFalse_whenNeitherIsBlocked() {
        when(redisTemplate.hasKey("login:block:ip:" + IP)).thenReturn(false);
        when(redisTemplate.hasKey("login:block:username:" + USERNAME)).thenReturn(false);

        assertFalse(loginAttemptService.isBlocked(IP, USERNAME));
    }

    @Test
    void isShadowBanned_shouldReturnTrue_whenIpIsShadowBanned() {
        when(redisTemplate.hasKey("login:shadowban:ip:" + IP)).thenReturn(true);

        assertTrue(loginAttemptService.isShadowBanned(IP, USERNAME));
    }

    @Test
    void isShadowBanned_shouldReturnTrue_whenUsernameIsShadowBanned() {
        when(redisTemplate.hasKey("login:shadowban:ip:" + IP)).thenReturn(false);
        when(redisTemplate.hasKey("login:shadowban:username:" + USERNAME)).thenReturn(true);

        assertTrue(loginAttemptService.isShadowBanned(IP, USERNAME));
    }

    @Test
    void isShadowBanned_shouldReturnFalse_whenNeitherIsShadowBanned() {
        when(redisTemplate.hasKey("login:shadowban:ip:" + IP)).thenReturn(false);
        when(redisTemplate.hasKey("login:shadowban:username:" + USERNAME)).thenReturn(false);

        assertFalse(loginAttemptService.isShadowBanned(IP, USERNAME));
    }
}

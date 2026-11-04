package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.service.impl.TokenBlacklistServiceImpl;
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
class TokenBlacklistServiceImplTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOperations;

    @InjectMocks
    TokenBlacklistServiceImpl tokenBlacklistService;

    @Test
    void blacklist_shouldSetKeyWithTtlInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        tokenBlacklistService.blacklist("someToken", 60000L);

        verify(valueOperations).set("jwt:blacklist:someToken", "1", 60000L, TimeUnit.MILLISECONDS);
    }

    @Test
    void isBlacklisted_shouldReturnTrue_whenKeyExists() {
        when(redisTemplate.hasKey("jwt:blacklist:someToken")).thenReturn(true);

        assertTrue(tokenBlacklistService.isBlacklisted("someToken"));
    }

    @Test
    void isBlacklisted_shouldReturnFalse_whenKeyNotExists() {
        when(redisTemplate.hasKey("jwt:blacklist:someToken")).thenReturn(false);

        assertFalse(tokenBlacklistService.isBlacklisted("someToken"));
    }

    @Test
    void isBlacklisted_shouldReturnFalse_whenRedisReturnsNull() {
        when(redisTemplate.hasKey("jwt:blacklist:someToken")).thenReturn(null);

        assertFalse(tokenBlacklistService.isBlacklisted("someToken"));
    }
}

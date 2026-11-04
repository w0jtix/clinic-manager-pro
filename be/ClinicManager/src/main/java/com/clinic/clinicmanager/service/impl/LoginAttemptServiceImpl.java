package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.model.constants.LoginAttemptScope;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private static final String ATTEMPTS_IP_PREFIX      = "login:attempts:ip:";
    private static final String ATTEMPTS_USER_PREFIX    = "login:attempts:username:";
    private static final String BLOCK_IP_PREFIX         = "login:block:ip:";
    private static final String BLOCK_USER_PREFIX       = "login:block:username:";
    private static final String SHADOWBAN_IP_PREFIX     = "login:shadowban:ip:";
    private static final String SHADOWBAN_USER_PREFIX   = "login:shadowban:username:";
    private static final String CLINIC_ATTEMPTS_PREFIX  = "login:attempts:clinic:username:";
    private static final String CLINIC_BLOCK_PREFIX     = "login:block:clinic:username:";
    private static final String CLINIC_SHADOWBAN_PREFIX = "login:shadowban:clinic:username:";

    private static final int  BLOCK_THRESHOLD      = 5;
    private static final int  SHADOWBAN_THRESHOLD  = 10;
    private static final long BLOCK_DURATION_MS    = 3 * 60 * 1000L;
    private static final long SHADOWBAN_DURATION_H = 24L;

    private final StringRedisTemplate redisTemplate;
    private final AuditLogService auditLogService;

    @Override
    public void recordFailure(String ip, String username, LoginAttemptScope scope) {
        if (scope == LoginAttemptScope.USER_ONLY) {
            recordUserOnlyFailure(ip, username);
            return;
        }

        long ipCount   = incrementCounter(ATTEMPTS_IP_PREFIX + ip);
        long userCount = incrementCounter(ATTEMPTS_USER_PREFIX + username);

        if (ipCount >= SHADOWBAN_THRESHOLD || userCount >= SHADOWBAN_THRESHOLD) {
            redisTemplate.opsForValue().set(SHADOWBAN_IP_PREFIX + ip, "1", SHADOWBAN_DURATION_H, TimeUnit.HOURS);
            redisTemplate.opsForValue().set(SHADOWBAN_USER_PREFIX + username, "1", SHADOWBAN_DURATION_H, TimeUnit.HOURS);
            auditLogService.logSecurity("Security-ShadowBan", 0L, username,
                    Map.of("ip", ip, "username", username, "ipAttempts", ipCount, "userAttempts", userCount));
        } else if (ipCount == BLOCK_THRESHOLD || userCount == BLOCK_THRESHOLD) {
            redisTemplate.opsForValue().set(BLOCK_IP_PREFIX + ip, "1", BLOCK_DURATION_MS, TimeUnit.MILLISECONDS);
            redisTemplate.opsForValue().set(BLOCK_USER_PREFIX + username, "1", BLOCK_DURATION_MS, TimeUnit.MILLISECONDS);
            auditLogService.logSecurity("Security-Block", 0L, username,
                    Map.of("ip", ip, "username", username, "ipAttempts", ipCount, "userAttempts", userCount));
        }
    }

    @Override
    public void recordSuccess(String ip, String username, LoginAttemptScope scope) {
        if (scope == LoginAttemptScope.USER_ONLY) {
            redisTemplate.delete(CLINIC_ATTEMPTS_PREFIX + username);
            redisTemplate.delete(CLINIC_BLOCK_PREFIX + username);
            return;
        }
        redisTemplate.delete(ATTEMPTS_IP_PREFIX + ip);
        redisTemplate.delete(ATTEMPTS_USER_PREFIX + username);
        redisTemplate.delete(BLOCK_IP_PREFIX + ip);
        redisTemplate.delete(BLOCK_USER_PREFIX + username);
    }

    @Override
    public boolean isBlocked(String ip, String username, LoginAttemptScope scope) {
        if (scope == LoginAttemptScope.USER_ONLY) {
            return hasKey(CLINIC_BLOCK_PREFIX + username);
        }
        return hasKey(BLOCK_IP_PREFIX + ip) || hasKey(BLOCK_USER_PREFIX + username);
    }

    @Override
    public boolean isShadowBanned(String ip, String username, LoginAttemptScope scope) {
        if (scope == LoginAttemptScope.USER_ONLY) {
            return hasKey(CLINIC_SHADOWBAN_PREFIX + username);
        }
        return hasKey(SHADOWBAN_IP_PREFIX + ip) || hasKey(SHADOWBAN_USER_PREFIX + username);
    }

    private void recordUserOnlyFailure(String ip, String username) {
        long userCount = incrementCounter(CLINIC_ATTEMPTS_PREFIX + username);

        if (userCount >= SHADOWBAN_THRESHOLD) {
            redisTemplate.opsForValue().set(CLINIC_SHADOWBAN_PREFIX + username, "1", SHADOWBAN_DURATION_H, TimeUnit.HOURS);
            auditLogService.logSecurity("Security-ShadowBan", 0L, username, userOnlyDetails(ip, username, userCount));
        } else if (userCount == BLOCK_THRESHOLD) {
            redisTemplate.opsForValue().set(CLINIC_BLOCK_PREFIX + username, "1", BLOCK_DURATION_MS, TimeUnit.MILLISECONDS);
            auditLogService.logSecurity("Security-Block", 0L, username, userOnlyDetails(ip, username, userCount));
        }
    }

    private static Map<String, Object> userOnlyDetails(String ip, String username, long userCount) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("ip", ip);
        details.put("username", username);
        details.put("userAttempts", userCount);
        details.put("scope", LoginAttemptScope.USER_ONLY.name());
        return details;
    }

    private boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private long incrementCounter(String key) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, 24, TimeUnit.HOURS);
        }
        return count != null ? count : 0;
    }
}

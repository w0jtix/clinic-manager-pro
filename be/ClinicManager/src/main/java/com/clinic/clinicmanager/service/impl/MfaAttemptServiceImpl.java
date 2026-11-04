package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.MfaAttemptService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class MfaAttemptServiceImpl extends RedisAttemptLimiter implements MfaAttemptService {

    private static final Config CONFIG = new Config("mfa", 5, 15L, 24L, "Security-2FA-Failure", "Security-2FA-Block");

    public MfaAttemptServiceImpl(StringRedisTemplate redisTemplate, AuditLogService auditLogService) {
        super(redisTemplate, auditLogService, CONFIG);
    }
}

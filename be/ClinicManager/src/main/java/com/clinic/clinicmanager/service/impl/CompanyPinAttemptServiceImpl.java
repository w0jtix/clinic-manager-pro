package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.CompanyPinAttemptService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class CompanyPinAttemptServiceImpl extends RedisAttemptLimiter implements CompanyPinAttemptService {

    private static final Config CONFIG = new Config("company-pin", 5, 15L, 24L, "Security-PinFailure", "Security-PinBlock");

    public CompanyPinAttemptServiceImpl(StringRedisTemplate redisTemplate, AuditLogService auditLogService) {
        super(redisTemplate, auditLogService, CONFIG);
    }
}

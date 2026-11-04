package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.DeviceCodeAttemptService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class DeviceCodeAttemptServiceImpl extends RedisAttemptLimiter implements DeviceCodeAttemptService {

    private static final Config CONFIG = new Config("device-code", 5, 15L, 24L, "Security-Device-Code-Failure", "Security-Device-Code-Block", false);

    public DeviceCodeAttemptServiceImpl(StringRedisTemplate redisTemplate, AuditLogService auditLogService) {
        super(redisTemplate, auditLogService, CONFIG);
    }
}

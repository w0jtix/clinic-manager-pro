package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.StepUpAttemptService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class StepUpAttemptServiceImpl extends RedisAttemptLimiter implements StepUpAttemptService {

    private static final Config CONFIG = new Config("stepup", 5, 15L, 24L, "Security-StepUp-Failure", "Security-StepUp-Block");

    public StepUpAttemptServiceImpl(StringRedisTemplate redisTemplate, AuditLogService auditLogService) {
        super(redisTemplate, auditLogService, CONFIG);
    }
}

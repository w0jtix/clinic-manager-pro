package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.service.AttemptLimiter;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.SubjectAttemptLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class RedisAttemptLimiter implements AttemptLimiter, SubjectAttemptLimiter {

    public record Config(
            String keyPrefix,
            int blockThreshold,
            long blockDurationMinutes,
            long attemptsTtlHours,
            String failureAuditType,
            String blockAuditType,
            boolean auditFailures
    ) {
        public Config(String keyPrefix, int blockThreshold, long blockDurationMinutes, long attemptsTtlHours,
                      String failureAuditType, String blockAuditType) {
            this(keyPrefix, blockThreshold, blockDurationMinutes, attemptsTtlHours, failureAuditType, blockAuditType, true);
        }
    }

    private static final RedisScript<Long> RESERVE_ATTEMPT_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[2]) == 1 then return -1 end "
                    + "local attempts = redis.call('INCR', KEYS[1]) "
                    + "if attempts == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "if attempts > tonumber(ARGV[2]) then "
                    + "redis.call('SET', KEYS[2], '1', 'EX', ARGV[3]) "
                    + "redis.call('DEL', KEYS[1]) "
                    + "return -1 "
                    + "end "
                    + "return attempts",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AuditLogService auditLogService;
    private final Config config;

    protected RedisAttemptLimiter(StringRedisTemplate redisTemplate, AuditLogService auditLogService, Config config) {
        this.redisTemplate = redisTemplate;
        this.auditLogService = auditLogService;
        this.config = config;
    }

    @Override
    public boolean isBlocked(Long userId) {
        return isBlocked(SubjectAttemptLimiter.userSubject(userId));
    }

    @Override
    public long getBlockRemainingMinutes(Long userId) {
        return getBlockRemainingMinutes(SubjectAttemptLimiter.userSubject(userId));
    }

    @Override
    public OptionalInt reserveAttempt(Long userId) {
        return reserveAttempt(SubjectAttemptLimiter.userSubject(userId));
    }

    @Override
    public int recordFailure(Long userId, String username, int attempt) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", username);
        details.put("attempts", attempt);
        return registerFailure(SubjectAttemptLimiter.userSubject(userId), attempt, userId, username, details);
    }

    @Override
    public void recordSuccess(Long userId) {
        recordSuccess(SubjectAttemptLimiter.userSubject(userId));
    }

    @Override
    public void reset(Long userId) {
        reset(SubjectAttemptLimiter.userSubject(userId));
    }

    @Override
    public boolean isBlocked(String subject) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(blockKey(subject)));
    }

    @Override
    public long getBlockRemainingMinutes(String subject) {
        Long seconds = redisTemplate.getExpire(blockKey(subject), TimeUnit.SECONDS);
        if (seconds == null || seconds <= 0) {
            return config.blockDurationMinutes();
        }
        return (seconds + 59) / 60;
    }

    @Override
    public OptionalInt reserveAttempt(String subject) {
        Long attempt = redisTemplate.execute(RESERVE_ATTEMPT_SCRIPT,
                List.of(attemptsKey(subject), blockKey(subject)),
                String.valueOf(TimeUnit.HOURS.toSeconds(config.attemptsTtlHours())),
                String.valueOf(config.blockThreshold()),
                String.valueOf(TimeUnit.MINUTES.toSeconds(config.blockDurationMinutes())));
        if (attempt == null || attempt <= 0) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(attempt.intValue());
    }

    @Override
    public int recordFailure(String subject, int attempt) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("subject", subject);
        details.put("attempts", attempt);
        return registerFailure(subject, attempt, null, subject, details);
    }

    @Override
    public void recordSuccess(String subject) {
        redisTemplate.delete(attemptsKey(subject));
    }

    @Override
    public void reset(String subject) {
        redisTemplate.delete(attemptsKey(subject));
        redisTemplate.delete(blockKey(subject));
    }

    private int registerFailure(String subject, int attempt, Long entityId, String keyTrait, Map<String, Object> details) {
        if (attempt >= config.blockThreshold()) {
            redisTemplate.opsForValue().set(blockKey(subject), "1", config.blockDurationMinutes(), TimeUnit.MINUTES);
            redisTemplate.delete(attemptsKey(subject));
            details.put("blockMinutes", config.blockDurationMinutes());
            audit(config.blockAuditType(), entityId, keyTrait, details);
            return 0;
        }

        if (config.auditFailures()) {
            audit(config.failureAuditType(), entityId, keyTrait, details);
        }
        return config.blockThreshold() - attempt;
    }

    private void audit(String auditType, Long entityId, String keyTrait, Map<String, Object> details) {
        try {
            auditLogService.logSecurityInNewTransaction(auditType, entityId, keyTrait, details);
        } catch (RuntimeException e) {
            log.error("Failed to write {} audit entry for {}", auditType, keyTrait, e);
        }
    }

    private String attemptsKey(String subject) {
        return config.keyPrefix() + ":attempts:" + subject;
    }

    private String blockKey(String subject) {
        return config.keyPrefix() + ":block:" + subject;
    }
}

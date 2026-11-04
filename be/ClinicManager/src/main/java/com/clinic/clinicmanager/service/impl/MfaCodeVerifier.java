package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.exceptions.TooManyAttemptsException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.service.MfaAttemptService;
import com.clinic.clinicmanager.utils.MfaEncryption;
import com.clinic.clinicmanager.utils.TotpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.IntFunction;

@Component
@RequiredArgsConstructor
public class MfaCodeVerifier {

    private static final String LAST_STEP_PREFIX = "mfa:last-step:user:";
    private static final long LAST_STEP_TTL_S = TotpUtil.PERIOD_SECONDS * 4L;

    private static final RedisScript<Long> CONSUME_STEP_SCRIPT = new DefaultRedisScript<>(
            "local last = redis.call('GET', KEYS[1]) "
                    + "if last and tonumber(last) >= tonumber(ARGV[1]) then return 0 end "
                    + "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2]) "
                    + "return 1",
            Long.class);

    private final MfaAttemptService mfaAttemptService;
    private final StringRedisTemplate redisTemplate;
    private final MfaEncryption mfaEncryption;
    private final TotpUtil totpUtil;

    public void verifyCode(User user, String code) {
        verifyCode(user, mfaEncryption.decrypt(user.getMfaSecretEnc()), code);
    }

    public void verifyCode(User user, String secret, String code) {
        int attempt = reserveAttempt(user);
        if (!matches(user.getId(), secret, code)) {
            registerFailure(user, attempt, remaining -> "Nieprawidłowy kod. Pozostało prób: " + remaining + ".");
        }
        mfaAttemptService.recordSuccess(user.getId());
    }

    public boolean checkCode(User user, String code) {
        return matches(user.getId(), mfaEncryption.decrypt(user.getMfaSecretEnc()), code);
    }

    private boolean matches(Long userId, String secret, String code) {
        String normalized = code != null ? code.replaceAll("\\s", "") : null;
        OptionalLong step = totpUtil.matchStep(secret, normalized);
        return step.isPresent() && consumeStep(userId, step.getAsLong());
    }

    public int reserveAttempt(User user) {
        if (mfaAttemptService.isBlocked(user.getId())) {
            throw blockedException(user.getId());
        }
        OptionalInt attempt = mfaAttemptService.reserveAttempt(user.getId());
        if (attempt.isEmpty()) {
            throw blockedException(user.getId());
        }
        return attempt.getAsInt();
    }

    public void registerFailure(User user, int attempt, IntFunction<String> message) {
        int remaining = mfaAttemptService.recordFailure(user.getId(), user.getUsername(), attempt);
        if (remaining <= 0) {
            throw blockedException(user.getId());
        }
        throw new ValidationException(message.apply(remaining));
    }

    public void recordSuccess(User user) {
        mfaAttemptService.recordSuccess(user.getId());
    }

    public void reset(Long userId) {
        redisTemplate.delete(LAST_STEP_PREFIX + userId);
        mfaAttemptService.reset(userId);
    }

    private boolean consumeStep(Long userId, long step) {
        Long result = redisTemplate.execute(CONSUME_STEP_SCRIPT,
                List.of(LAST_STEP_PREFIX + userId),
                String.valueOf(step),
                String.valueOf(LAST_STEP_TTL_S));
        return result != null && result == 1L;
    }

    private TooManyAttemptsException blockedException(Long userId) {
        long minutes = mfaAttemptService.getBlockRemainingMinutes(userId);
        return new TooManyAttemptsException("Zbyt wiele nieudanych prób. Weryfikacja jest zablokowana, spróbuj ponownie za "
                + minutes + " min.");
    }
}

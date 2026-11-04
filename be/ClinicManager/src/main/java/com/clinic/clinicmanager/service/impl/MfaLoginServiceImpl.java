package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.MfaLoginResult;
import com.clinic.clinicmanager.exceptions.MfaSessionExpiredException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.TooManyAttemptsException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.MfaLoginService;
import com.clinic.clinicmanager.utils.SecureTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MfaLoginServiceImpl implements MfaLoginService {

    private static final String PENDING_LOGIN_PREFIX = "mfa:pending:";
    private static final String PENDING_VALUE_SEPARATOR = ":";
    private static final long PENDING_LOGIN_TTL_MIN = 5L;
    private static final int PENDING_TOKEN_BYTES = 32;

    private final UserRepo userRepo;
    private final StringRedisTemplate redisTemplate;
    private final MfaCodeVerifier mfaCodeVerifier;
    private final MfaBackupCodeManager mfaBackupCodeManager;
    private final AuditLogService auditLogService;

    @Override
    public boolean isMfaEnabled(Long userId) {
        return userRepo.findOneById(userId).map(User::isMfaEnabled).orElse(false);
    }

    @Override
    public String startLoginChallenge(Long userId) {
        User user = userRepo.findOneById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with given id: " + userId));
        String token = SecureTokens.randomToken(PENDING_TOKEN_BYTES);
        redisTemplate.opsForValue().set(PENDING_LOGIN_PREFIX + token,
                userId + PENDING_VALUE_SEPARATOR + passwordFingerprint(user),
                PENDING_LOGIN_TTL_MIN, TimeUnit.MINUTES);
        return token;
    }

    @Override
    public MfaLoginResult verifyLoginCode(String mfaToken, String code) {
        User user = resolvePendingUser(mfaToken);
        mfaCodeVerifier.verifyCode(user, code);
        redisTemplate.delete(PENDING_LOGIN_PREFIX + mfaToken);
        return new MfaLoginResult(user.getId(), user.getUsername(), null);
    }

    @Override
    @Transactional(noRollbackFor = {ValidationException.class, TooManyAttemptsException.class})
    public MfaLoginResult verifyLoginBackupCode(String mfaToken, String code) {
        User user = resolvePendingUser(mfaToken);
        int remaining = mfaBackupCodeManager.consume(user, code);
        redisTemplate.delete(PENDING_LOGIN_PREFIX + mfaToken);
        auditLogService.logSecurity("User-2FA-BackupCode-Used", user.getId(), user.getUsername(),
                Map.of("username", user.getUsername(), "remainingBackupCodes", remaining));
        return new MfaLoginResult(user.getId(), user.getUsername(), remaining);
    }

    private User resolvePendingUser(String mfaToken) {
        String value = mfaToken != null ? redisTemplate.opsForValue().get(PENDING_LOGIN_PREFIX + mfaToken) : null;
        if (value == null) {
            throw new MfaSessionExpiredException();
        }
        String[] parts = value.split(PENDING_VALUE_SEPARATOR, 2);
        User user = parts.length == 2 && parts[0].matches("\\d+")
                ? userRepo.findOneById(Long.valueOf(parts[0]))
                        .filter(User::isMfaEnabled)
                        .filter(found -> fingerprintMatches(found, parts[1]))
                        .orElse(null)
                : null;
        if (user == null) {
            redisTemplate.delete(PENDING_LOGIN_PREFIX + mfaToken);
            throw new MfaSessionExpiredException();
        }
        return user;
    }

    private static boolean fingerprintMatches(User user, String storedFingerprint) {
        return MessageDigest.isEqual(
                passwordFingerprint(user).getBytes(StandardCharsets.US_ASCII),
                storedFingerprint.getBytes(StandardCharsets.US_ASCII));
    }

    private static String passwordFingerprint(User user) {
        return SecureTokens.sha256Hex(user.getPassword());
    }
}

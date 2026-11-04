package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.UserBackupCode;
import com.clinic.clinicmanager.repo.UserBackupCodeRepo;
import com.clinic.clinicmanager.utils.SecureTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MfaBackupCodeManager {

    private static final String PENDING_CODES_PREFIX = "mfa:pending-codes:user:";
    private static final String PENDING_CODES_SEPARATOR = ",";
    private static final long PENDING_CODES_TTL_MIN = 15L;
    private static final int BACKUP_CODE_COUNT = 10;
    private static final int BACKUP_CODE_GROUP = 4;
    private static final String BACKUP_CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final String BACKUP_CODE_INVALID = "Kod zapasowy jest nieprawidłowy albo został już użyty.";
    private static final String PENDING_CODES_EXPIRED = "Nowe kody wygasły. Wygeneruj je ponownie.";

    private final UserBackupCodeRepo userBackupCodeRepo;
    private final PasswordEncoder passwordEncoder;
    private final MfaCodeVerifier mfaCodeVerifier;
    private final StringRedisTemplate redisTemplate;
    private final Clock businessClock;

    public List<String> replace(Long userId) {
        List<String> codes = generate();
        store(userId, hash(codes));
        return codes;
    }

    public List<String> preparePending(Long userId) {
        List<String> codes = generate();
        redisTemplate.opsForValue().set(PENDING_CODES_PREFIX + userId,
                String.join(PENDING_CODES_SEPARATOR, hash(codes)),
                PENDING_CODES_TTL_MIN, TimeUnit.MINUTES);
        return codes;
    }

    public int confirmPending(Long userId) {
        String pendingHashes = redisTemplate.opsForValue().getAndDelete(PENDING_CODES_PREFIX + userId);
        if (pendingHashes == null || pendingHashes.isEmpty()) {
            throw new ConflictException(PENDING_CODES_EXPIRED);
        }
        List<String> hashes = List.of(pendingHashes.split(PENDING_CODES_SEPARATOR));
        store(userId, hashes);
        return hashes.size();
    }

    public int consume(User user, String code) {
        int attempt = mfaCodeVerifier.reserveAttempt(user);
        String normalized = normalize(code);
        boolean consumed = false;
        if (normalized != null) {
            for (UserBackupCode backupCode : userBackupCodeRepo.findAllByUserIdAndUsedAtIsNull(user.getId())) {
                if (passwordEncoder.matches(normalized, backupCode.getCodeHash())) {
                    consumed = userBackupCodeRepo.markUsed(backupCode.getId(), LocalDateTime.now(businessClock)) == 1;
                    break;
                }
            }
        }
        if (!consumed) {
            mfaCodeVerifier.registerFailure(user, attempt, remaining -> BACKUP_CODE_INVALID);
        }
        mfaCodeVerifier.recordSuccess(user);
        return countRemaining(user.getId());
    }

    public int countRemaining(Long userId) {
        return userBackupCodeRepo.countByUserIdAndUsedAtIsNull(userId);
    }

    public void purge(Long userId) {
        userBackupCodeRepo.deleteAllByUserId(userId);
        redisTemplate.delete(PENDING_CODES_PREFIX + userId);
    }

    private List<String> generate() {
        List<String> codes = new ArrayList<>(BACKUP_CODE_COUNT);
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String raw = SecureTokens.randomFromAlphabet(BACKUP_CODE_ALPHABET, BACKUP_CODE_GROUP * 2);
            codes.add(raw.substring(0, BACKUP_CODE_GROUP) + "-" + raw.substring(BACKUP_CODE_GROUP));
        }
        return codes;
    }

    private List<String> hash(List<String> codes) {
        return codes.stream()
                .map(code -> passwordEncoder.encode(code.replace("-", "")))
                .collect(Collectors.toList());
    }

    private void store(Long userId, List<String> hashes) {
        userBackupCodeRepo.deleteAllByUserId(userId);
        LocalDateTime now = LocalDateTime.now(businessClock);
        userBackupCodeRepo.saveAll(hashes.stream()
                .map(hash -> UserBackupCode.builder()
                        .userId(userId)
                        .codeHash(hash)
                        .createdAt(now)
                        .build())
                .collect(Collectors.toList()));
    }

    private static String normalize(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toLowerCase().replace("-", "").replaceAll("\\s", "");
        return normalized.matches("[a-z0-9]{" + (BACKUP_CODE_GROUP * 2) + "}") ? normalized : null;
    }
}

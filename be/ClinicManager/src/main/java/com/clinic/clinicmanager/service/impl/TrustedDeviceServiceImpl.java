package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.RememberedDeviceToken;
import com.clinic.clinicmanager.DTO.TrustedDeviceDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.TrustedDevice;
import com.clinic.clinicmanager.model.constants.TrustedDeviceKind;
import com.clinic.clinicmanager.repo.TrustedDeviceRepo;
import com.clinic.clinicmanager.repo.projection.UserDeviceCountProjection;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.TrustedDeviceService;
import com.clinic.clinicmanager.utils.DeviceLabelParser;
import com.clinic.clinicmanager.utils.SecureTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrustedDeviceServiceImpl implements TrustedDeviceService {

    public static final long REMEMBER_DAYS = 180L;
    private static final int TOKEN_BYTES = 32;
    private static final int USER_AGENT_MAX_LENGTH = 512;
    private static final int IP_MAX_LENGTH = 64;
    private static final TrustedDeviceKind KIND = TrustedDeviceKind.MFA_REMEMBER;

    private final TrustedDeviceRepo trustedDeviceRepo;
    private final AuditLogService auditLogService;
    private final Clock businessClock;

    @Override
    @Transactional
    public Optional<RememberedDeviceToken> rotateRemembered(Long userId, String username, String rawToken, String userAgent, String ip) {
        if (!StringUtils.hasText(rawToken)) {
            return Optional.empty();
        }
        String tokenHash = SecureTokens.sha256Hex(rawToken);
        LocalDateTime now = now();

        Optional<TrustedDevice> current = trustedDeviceRepo.findByTokenHashAndUserIdAndKind(tokenHash, userId, KIND);
        if (current.isPresent()) {
            TrustedDevice device = current.get();
            if (!device.isActive(now)) {
                return Optional.empty();
            }
            String newToken = SecureTokens.randomToken(TOKEN_BYTES);
            device.setPreviousTokenHash(tokenHash);
            device.setTokenHash(SecureTokens.sha256Hex(newToken));
            device.setLastUsedAt(now);
            device.setLastIp(truncate(ip, IP_MAX_LENGTH));
            trustedDeviceRepo.save(device);
            return Optional.of(new RememberedDeviceToken(newToken, device.getExpiresAt()));
        }

        trustedDeviceRepo.findFirstByPreviousTokenHashAndUserIdAndKind(tokenHash, userId, KIND)
                .ifPresent(device -> handleReuse(device, username, ip, now));
        return Optional.empty();
    }

    @Override
    @Transactional
    public RememberedDeviceToken rememberDevice(Long userId, String userAgent, String ip) {
        LocalDateTime now = now();
        String token = SecureTokens.randomToken(TOKEN_BYTES);
        TrustedDevice device = trustedDeviceRepo.save(TrustedDevice.builder()
                .userId(userId)
                .kind(KIND)
                .tokenHash(SecureTokens.sha256Hex(token))
                .label(DeviceLabelParser.label(userAgent))
                .userAgent(truncate(userAgent, USER_AGENT_MAX_LENGTH))
                .lastIp(truncate(ip, IP_MAX_LENGTH))
                .createdAt(now)
                .lastUsedAt(now)
                .expiresAt(now.plusDays(REMEMBER_DAYS))
                .build());
        return new RememberedDeviceToken(token, device.getExpiresAt());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrustedDeviceDTO> getActiveRemembered(Long userId, String currentRawToken) {
        String currentHash = StringUtils.hasText(currentRawToken) ? SecureTokens.sha256Hex(currentRawToken) : null;
        return trustedDeviceRepo.findActiveOrderByLastUsed(userId, KIND, now()).stream()
                .map(device -> new TrustedDeviceDTO(device, device.getTokenHash().equals(currentHash)))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public String revokeRemembered(Long userId, Long deviceId) {
        LocalDateTime now = now();
        TrustedDevice device = trustedDeviceRepo.findByIdAndUserIdAndKind(deviceId, userId, KIND)
                .filter(found -> found.isActive(now))
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono zapamiętanego urządzenia."));
        device.setRevokedAt(now);
        trustedDeviceRepo.save(device);
        return device.getLabel();
    }

    @Override
    @Transactional
    public int revokeAllRemembered(Long userId) {
        return trustedDeviceRepo.revokeAll(userId, KIND, now());
    }

    @Override
    @Transactional(readOnly = true)
    public int countActiveRemembered(Long userId) {
        return (int) trustedDeviceRepo.countActive(userId, KIND, now());
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Integer> countActiveRememberedByUser() {
        return trustedDeviceRepo.countActiveGroupedByUser(KIND, now()).stream()
                .collect(Collectors.toMap(UserDeviceCountProjection::getUserId, row -> row.getDeviceCount().intValue()));
    }

    private void handleReuse(TrustedDevice device, String username, String ip, LocalDateTime now) {
        if (device.getRevokedAt() == null) {
            device.setRevokedAt(now);
            trustedDeviceRepo.save(device);
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("username", username);
        details.put("deviceId", device.getId());
        details.put("label", device.getLabel());
        details.put("ip", ip);
        auditLogService.logSecurity("Security-2FA-TrustedDevice-Reuse", device.getUserId(), username, details);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(businessClock);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}

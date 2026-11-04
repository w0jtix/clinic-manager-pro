package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.TrustedDeviceDTO;
import com.clinic.clinicmanager.DTO.response.MfaBackupCodesResponse;
import com.clinic.clinicmanager.DTO.response.MfaSetupResponse;
import com.clinic.clinicmanager.DTO.response.MfaStatusResponse;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.TooManyAttemptsException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.MfaService;
import com.clinic.clinicmanager.service.TrustedDeviceService;
import com.clinic.clinicmanager.utils.MfaEncryption;
import com.clinic.clinicmanager.utils.RoleAssignmentRules;
import com.clinic.clinicmanager.utils.SessionUtils;
import com.clinic.clinicmanager.utils.TotpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MfaServiceImpl implements MfaService {

    private static final String SETUP_PREFIX = "mfa:setup:user:";
    private static final long SETUP_TTL_MIN = 10L;

    private static final String SETUP_EXPIRED = "Konfiguracja wygasła. Zacznij od nowa.";
    private static final String ALREADY_ENABLED = "Weryfikacja dwuetapowa jest już włączona.";
    private static final String NOT_ENABLED = "Weryfikacja dwuetapowa nie jest włączona.";
    private static final String ADMIN_RESET_FORBIDDEN = "Nie można zresetować 2FA administratora w aplikacji.";
    private static final String TARGET_NOT_ENABLED = "Użytkownik nie ma włączonej weryfikacji dwuetapowej.";

    private final UserRepo userRepo;
    private final MfaCodeVerifier mfaCodeVerifier;
    private final MfaBackupCodeManager mfaBackupCodeManager;
    private final TrustedDeviceService trustedDeviceService;
    private final AuditLogService auditLogService;
    private final StringRedisTemplate redisTemplate;
    private final MfaEncryption mfaEncryption;
    private final TotpUtil totpUtil;
    private final Clock businessClock;

    @Override
    @Transactional(readOnly = true)
    public MfaStatusResponse getStatus(String currentDeviceToken) {
        User user = currentUser();
        boolean enabled = user.isMfaEnabled();
        return new MfaStatusResponse(
                enabled,
                enabled ? user.getMfaEnabledAt() : null,
                enabled ? mfaBackupCodeManager.countRemaining(user.getId()) : 0,
                trustedDeviceService.getActiveRemembered(user.getId(), currentDeviceToken));
    }

    @Override
    public MfaSetupResponse setup() {
        User user = currentUser();
        if (user.isMfaEnabled()) {
            throw new ConflictException(ALREADY_ENABLED);
        }
        String secret = resolvePendingSetupSecret(user.getId());
        return new MfaSetupResponse(secret,
                totpUtil.otpauthUri(secret, user.getUsername()),
                totpUtil.qrDataUri(secret, user.getUsername()));
    }

    @Override
    @Transactional(noRollbackFor = {ValidationException.class, TooManyAttemptsException.class})
    public MfaBackupCodesResponse enable(String code) {
        User user = currentUser();
        if (user.isMfaEnabled()) {
            throw new ConflictException(ALREADY_ENABLED);
        }
        String pendingSecret = redisTemplate.opsForValue().get(SETUP_PREFIX + user.getId());
        if (pendingSecret == null) {
            throw new ValidationException(SETUP_EXPIRED);
        }
        String secret = mfaEncryption.decrypt(pendingSecret);
        mfaCodeVerifier.verifyCode(user, secret, code);

        user.setMfaSecretEnc(mfaEncryption.encrypt(secret));
        user.setMfaEnabledAt(LocalDateTime.now(businessClock));
        userRepo.save(user);
        redisTemplate.delete(SETUP_PREFIX + user.getId());
        trustedDeviceService.revokeAllRemembered(user.getId());
        List<String> backupCodes = mfaBackupCodeManager.replace(user.getId());

        auditLogService.logSecurity("User-2FA-Enabled", user.getId(), user.getUsername(),
                Map.of("username", user.getUsername(), "action", "Włączono weryfikację dwuetapową"));
        return new MfaBackupCodesResponse(backupCodes);
    }

    @Override
    @Transactional(noRollbackFor = {ValidationException.class, TooManyAttemptsException.class})
    public void disable(String code) {
        User user = currentUser();
        requireEnabled(user);
        mfaCodeVerifier.verifyCode(user, code);

        clearUserMfa(user);
        auditLogService.logSecurity("User-2FA-Disabled", user.getId(), user.getUsername(),
                Map.of("username", user.getUsername(), "action", "Wyłączono weryfikację dwuetapową"));
    }

    @Override
    @Transactional(noRollbackFor = {ValidationException.class, TooManyAttemptsException.class})
    public MfaBackupCodesResponse regenerateBackupCodes(String code) {
        User user = currentUser();
        requireEnabled(user);
        mfaCodeVerifier.verifyCode(user, code);
        return new MfaBackupCodesResponse(mfaBackupCodeManager.preparePending(user.getId()));
    }

    @Override
    @Transactional
    public void confirmBackupCodes() {
        User user = currentUser();
        requireEnabled(user);
        int count = mfaBackupCodeManager.confirmPending(user.getId());
        auditLogService.logSecurity("User-2FA-BackupCodes-Regenerated", user.getId(), user.getUsername(),
                Map.of("username", user.getUsername(), "count", count));
    }

    @Override
    public void revokeTrustedDevice(Long deviceId) {
        UserDetailsImpl principal = SessionUtils.requirePrincipal();
        String label = trustedDeviceService.revokeRemembered(principal.getId(), deviceId);
        auditLogService.logSecurity("User-2FA-TrustedDevice-Revoked", principal.getId(), principal.getUsername(),
                Map.of("username", principal.getUsername(), "device", label != null ? label : ""));
    }

    @Override
    public void revokeAllTrustedDevices() {
        UserDetailsImpl principal = SessionUtils.requirePrincipal();
        int revoked = trustedDeviceService.revokeAllRemembered(principal.getId());
        auditLogService.logSecurity("User-2FA-TrustedDevice-Revoked", principal.getId(), principal.getUsername(),
                Map.of("username", principal.getUsername(), "device", "wszystkie", "count", revoked));
    }

    @Override
    @Transactional
    public String resetByAdmin(Long userId) {
        User target = userRepo.findOneById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with given id: " + userId));
        if (RoleAssignmentRules.hasRole(target.getRoles(), RoleType.ROLE_ADMIN)) {
            throw new AccessDeniedException(ADMIN_RESET_FORBIDDEN);
        }
        if (!target.isMfaEnabled()) {
            throw new ConflictException(TARGET_NOT_ENABLED);
        }

        clearUserMfa(target);
        UserDetailsImpl principal = SessionUtils.getUserDetailsFromSession();
        String performedBy = principal != null ? principal.getUsername() : "SYSTEM";
        auditLogService.logSecurity("User-2FA-Reset", target.getId(), target.getUsername(),
                Map.of("username", target.getUsername(), "by", performedBy));
        return target.getUsername();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrustedDeviceDTO> getTrustedDevicesOfUser(Long userId) {
        if (!userRepo.existsById(userId)) {
            throw new ResourceNotFoundException("User not found with given id: " + userId);
        }
        return trustedDeviceService.getActiveRemembered(userId, null);
    }

    @Override
    @Transactional
    public void purgeMfaArtifacts(Long userId) {
        mfaBackupCodeManager.purge(userId);
        trustedDeviceService.revokeAllRemembered(userId);
        redisTemplate.delete(SETUP_PREFIX + userId);
        mfaCodeVerifier.reset(userId);
    }

    private String resolvePendingSetupSecret(Long userId) {
        String key = SETUP_PREFIX + userId;
        String candidate = totpUtil.generateSecret();
        if (Boolean.TRUE.equals(redisTemplate.opsForValue()
                .setIfAbsent(key, mfaEncryption.encrypt(candidate), SETUP_TTL_MIN, TimeUnit.MINUTES))) {
            return candidate;
        }
        String existing = redisTemplate.opsForValue().get(key);
        if (existing == null) {
            redisTemplate.opsForValue().set(key, mfaEncryption.encrypt(candidate), SETUP_TTL_MIN, TimeUnit.MINUTES);
            return candidate;
        }
        redisTemplate.expire(key, SETUP_TTL_MIN, TimeUnit.MINUTES);
        return mfaEncryption.decrypt(existing);
    }

    private void clearUserMfa(User user) {
        user.setMfaSecretEnc(null);
        user.setMfaEnabledAt(null);
        userRepo.save(user);
        purgeMfaArtifacts(user.getId());
    }

    private void requireEnabled(User user) {
        if (!user.isMfaEnabled()) {
            throw new ConflictException(NOT_ENABLED);
        }
    }

    private User currentUser() {
        Long userId = SessionUtils.requirePrincipal().getId();
        return userRepo.findOneById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with given id: " + userId));
    }
}

package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.ClinicDeviceDTO;
import com.clinic.clinicmanager.DTO.ClinicDeviceLoginResult;
import com.clinic.clinicmanager.DTO.ClinicDeviceRegistration;
import com.clinic.clinicmanager.DTO.LoginDeviceCheck;
import com.clinic.clinicmanager.DTO.RegistrationCodeDTO;
import com.clinic.clinicmanager.DTO.request.device.CreateRegistrationCodeRequest;
import com.clinic.clinicmanager.config.security.ClinicDeviceSettings;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.TooManyAttemptsException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.DeviceRegistrationCode;
import com.clinic.clinicmanager.model.TrustedDevice;
import com.clinic.clinicmanager.model.constants.TrustedDeviceKind;
import com.clinic.clinicmanager.repo.DeviceRegistrationCodeRepo;
import com.clinic.clinicmanager.repo.TrustedDeviceRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.ClinicDeviceService;
import com.clinic.clinicmanager.service.DeviceCodeAttemptService;
import com.clinic.clinicmanager.service.SubjectAttemptLimiter;
import com.clinic.clinicmanager.utils.DeviceLabelParser;
import com.clinic.clinicmanager.utils.SecureTokens;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClinicDeviceServiceImpl implements ClinicDeviceService {

    public static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    public static final int CODE_LENGTH = 6;
    public static final long CODE_VALIDITY_MINUTES = 15L;
    public static final int NAME_MAX_LENGTH = 100;

    private static final int MAX_CODE_GENERATION_ATTEMPTS = 20;
    private static final int TOKEN_BYTES = 32;
    private static final int USER_AGENT_MAX_LENGTH = 512;
    private static final int IP_MAX_LENGTH = 64;
    private static final TrustedDeviceKind KIND = TrustedDeviceKind.CLINIC_DEVICE;
    private static final long CODE_CREATION_LOCK_KEY = 0x50430044455643L;

    private static final String TARGET_REQUIRED = "Podaj nazwę urządzenia albo wybierz urządzenie do zastąpienia.";
    private static final String NAME_REQUIRED = "Podaj nazwę urządzenia.";
    private static final String NAME_TOO_LONG = "Nazwa urządzenia może mieć maksymalnie " + NAME_MAX_LENGTH + " znaków.";
    private static final String NAME_TAKEN = "Urządzenie o tej nazwie już istnieje.";
    private static final String DEVICE_NOT_FOUND = "Nie znaleziono urządzenia.";
    private static final String CODE_NOT_FOUND = "Nie znaleziono kodu.";
    private static final String REVOKE_FIRST = "Najpierw unieważnij urządzenie.";
    private static final String ACTIVE_CODE_EXISTS = "Istnieje już aktywny kod rejestracji. Użyj go albo anuluj, zanim wygenerujesz nowy.";
    private static final String INVALID_CODE = "Kod jest nieprawidłowy albo wygasł.";
    private static final String ALREADY_REGISTERED = "To urządzenie jest już zarejestrowane.";
    private static final String BLOCKED = "Zbyt wiele nieudanych prób. Spróbuj ponownie za %d min.";

    private final TrustedDeviceRepo trustedDeviceRepo;
    private final DeviceRegistrationCodeRepo registrationCodeRepo;
    private final DeviceCodeAttemptService deviceCodeAttemptService;
    private final ClinicDeviceResolver clinicDeviceResolver;
    private final AuditLogService auditLogService;
    private final ClinicDeviceSettings clinicDeviceSettings;
    private final Clock businessClock;

    @Override
    @Transactional(readOnly = true)
    public List<ClinicDeviceDTO> getDevices(boolean includeRevoked) {
        List<TrustedDevice> devices = trustedDeviceRepo.findAllByKindOrdered(KIND, includeRevoked);
        Map<Long, String> replacementNames = deviceNames(devices.stream()
                .map(TrustedDevice::getReplacedById)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return devices.stream()
                .map(device -> new ClinicDeviceDTO(device, nameOf(replacementNames, device.getReplacedById())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegistrationCodeDTO> getActiveCodes() {
        List<DeviceRegistrationCode> codes = registrationCodeRepo.findActive(now());
        Map<Long, String> replaceNames = deviceNames(codes.stream()
                .map(DeviceRegistrationCode::getReplaceDeviceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return codes.stream()
                .map(code -> new RegistrationCodeDTO(code, nameOf(replaceNames, code.getReplaceDeviceId())))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RegistrationCodeDTO createCode(CreateRegistrationCodeRequest request) {
        String name = normalizeName(request.getName());
        Long replaceDeviceId = request.getReplaceDeviceId();
        if ((name == null) == (replaceDeviceId == null)) {
            throw new ValidationException(TARGET_REQUIRED);
        }

        registrationCodeRepo.acquireTransactionLock(CODE_CREATION_LOCK_KEY);
        LocalDateTime now = now();
        if (registrationCodeRepo.existsActive(now)) {
            throw new ConflictException(ACTIVE_CODE_EXISTS);
        }
        String replaceDeviceName = null;
        if (name != null) {
            validateName(name, null, now);
        } else {
            replaceDeviceName = findActiveDevice(replaceDeviceId, now).getName();
        }

        String createdBy = SessionUtils.requirePrincipal().getUsername();
        DeviceRegistrationCode code = registrationCodeRepo.save(DeviceRegistrationCode.builder()
                .code(generateUniqueCode(now))
                .name(name)
                .replaceDeviceId(replaceDeviceId)
                .createdBy(createdBy)
                .createdAt(now)
                .expiresAt(now.plusMinutes(CODE_VALIDITY_MINUTES))
                .build());

        auditLogService.logSecurity("Device-Code-Created", code.getId(), name != null ? name : replaceDeviceName,
                codeAuditDetails(name, replaceDeviceName, createdBy));
        return new RegistrationCodeDTO(code, replaceDeviceName);
    }

    @Override
    @Transactional
    public void cancelCode(Long codeId) {
        LocalDateTime now = now();
        DeviceRegistrationCode code = registrationCodeRepo.findById(codeId)
                .filter(found -> found.isActive(now))
                .orElseThrow(() -> new ResourceNotFoundException(CODE_NOT_FOUND));
        code.setCancelledAt(now);
        registrationCodeRepo.save(code);

        String replaceDeviceName = code.getReplaceDeviceId() != null
                ? nameOf(deviceNames(Set.of(code.getReplaceDeviceId())), code.getReplaceDeviceId())
                : null;
        String by = SessionUtils.requirePrincipal().getUsername();
        auditLogService.logSecurity("Device-Code-Cancelled", code.getId(),
                code.getName() != null ? code.getName() : replaceDeviceName,
                codeAuditDetails(code.getName(), replaceDeviceName, by));
    }

    @Override
    @Transactional
    public ClinicDeviceDTO renameDevice(Long deviceId, String name) {
        LocalDateTime now = now();
        TrustedDevice device = findActiveDevice(deviceId, now);
        String newName = normalizeName(name);
        if (newName == null) {
            throw new ValidationException(NAME_REQUIRED);
        }
        validateName(newName, device.getId(), now);

        String oldName = device.getName();
        device.setName(newName);
        TrustedDevice saved = trustedDeviceRepo.save(device);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("oldName", oldName);
        details.put("newName", newName);
        auditLogService.logSecurity("Device-Renamed", saved.getId(), newName, details);
        return new ClinicDeviceDTO(saved, null);
    }

    @Override
    @Transactional
    public void revokeDevice(Long deviceId) {
        LocalDateTime now = now();
        TrustedDevice device = findActiveDevice(deviceId, now);
        device.setRevokedAt(now);
        trustedDeviceRepo.save(device);
        clinicDeviceResolver.invalidateAllowanceCache();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", device.getName());
        auditLogService.logSecurity("Device-Revoked", device.getId(), device.getName(), details);
    }

    @Override
    @Transactional
    public void deleteDevice(Long deviceId) {
        TrustedDevice device = trustedDeviceRepo.findByIdAndKind(deviceId, KIND)
                .orElseThrow(() -> new ResourceNotFoundException(DEVICE_NOT_FOUND));
        if (device.getRevokedAt() == null) {
            throw new ConflictException(REVOKE_FIRST);
        }

        trustedDeviceRepo.clearReplacedBy(device.getId());
        registrationCodeRepo.cancelPendingForReplacedDevice(device.getId(), now());
        registrationCodeRepo.clearReplaceDevice(device.getId());
        trustedDeviceRepo.delete(device);
        clinicDeviceResolver.invalidateAllowanceCache();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", device.getName());
        auditLogService.logSecurity("Device-Deleted", device.getId(), device.getName(), details);
    }

    @Override
    @Transactional(noRollbackFor = {ValidationException.class, TooManyAttemptsException.class})
    public ClinicDeviceRegistration register(String code, String currentRawToken, String userAgent, String ip) {
        String subject = SubjectAttemptLimiter.ipSubject(ip);
        if (deviceCodeAttemptService.isBlocked(subject)) {
            throw blockedException(subject);
        }
        OptionalInt attempt = deviceCodeAttemptService.reserveAttempt(subject);
        if (attempt.isEmpty()) {
            throw blockedException(subject);
        }

        LocalDateTime now = now();
        String normalizedCode = normalizeCode(code);
        Optional<DeviceRegistrationCode> registrationCode = normalizedCode != null
                ? registrationCodeRepo.findActiveByCodeForUpdate(normalizedCode, now).stream().findFirst()
                : Optional.empty();
        if (registrationCode.isEmpty()) {
            int remaining = deviceCodeAttemptService.recordFailure(subject, attempt.getAsInt());
            if (remaining <= 0) {
                throw blockedException(subject);
            }
            throw new ValidationException(INVALID_CODE);
        }
        deviceCodeAttemptService.recordSuccess(subject);

        Optional<TrustedDevice> current = clinicDeviceResolver.resolveToken(currentRawToken);
        if (current.isPresent() && !canRegisterOver(current.get(), registrationCode.get())) {
            throw new ConflictException(ALREADY_REGISTERED);
        }

        return completeRegistration(registrationCode.get(), userAgent, ip, now);
    }

    private static boolean canRegisterOver(TrustedDevice current, DeviceRegistrationCode registrationCode) {
        return current.getId().equals(registrationCode.getReplaceDeviceId()) || current.getSuspectedCopyAt() != null;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginDeviceCheck inspectForLogin(String rawToken, String ip, String userAgent) {
        if (!StringUtils.hasText(rawToken)) {
            return LoginDeviceCheck.unknown();
        }
        String tokenHash = SecureTokens.sha256Hex(rawToken);
        LocalDateTime now = now();

        Optional<TrustedDevice> current = trustedDeviceRepo.findByTokenHashAndKind(tokenHash, KIND);
        if (current.isPresent()) {
            TrustedDevice device = current.get();
            return new LoginDeviceCheck(statusOf(device, now), device.getId(), device.getName(), false);
        }

        Optional<TrustedDevice> previous = trustedDeviceRepo.findFirstByPreviousTokenHashAndKind(tokenHash, KIND);
        if (previous.isPresent()) {
            TrustedDevice device = previous.get();
            if (!device.isActive(now)) {
                return new LoginDeviceCheck(LoginDeviceCheck.Status.REVOKED, device.getId(), device.getName(), false);
            }
            if (device.getSuspectedCopyAt() == null && isRotationRetry(device, ip, userAgent, now)) {
                return new LoginDeviceCheck(LoginDeviceCheck.Status.ALLOWED, device.getId(), device.getName(), false);
            }
            return new LoginDeviceCheck(LoginDeviceCheck.Status.SUSPECTED_COPY, device.getId(), device.getName(), true);
        }

        return LoginDeviceCheck.unknown();
    }

    @Override
    @Transactional
    public ClinicDeviceLoginResult recognizeOnLogin(String rawToken, String username, String ip, String userAgent) {
        if (!StringUtils.hasText(rawToken)) {
            return ClinicDeviceLoginResult.none();
        }
        String tokenHash = SecureTokens.sha256Hex(rawToken);
        LocalDateTime now = now();

        Optional<TrustedDevice> current = trustedDeviceRepo.findByTokenHashAndKind(tokenHash, KIND);
        if (current.isPresent()) {
            TrustedDevice device = current.get();
            if (!device.isActive(now)) {
                return ClinicDeviceLoginResult.invalid();
            }
            device.setPreviousTokenHash(tokenHash);
            device.setRotatedAt(now);
            device.setLastUserAgentHash(userAgentHash(userAgent));
            return ClinicDeviceLoginResult.rotated(issueToken(device, username, ip, now), device.getName());
        }

        Optional<TrustedDevice> previous = trustedDeviceRepo.findFirstByPreviousTokenHashAndKind(tokenHash, KIND)
                .filter(device -> device.isActive(now));
        if (previous.isPresent()) {
            TrustedDevice device = previous.get();
            if (isRotationRetry(device, ip, userAgent, now)) {
                return ClinicDeviceLoginResult.rotated(issueToken(device, username, ip, now), device.getName());
            }
            markSuspectedCopy(device, username, ip, now);
            return ClinicDeviceLoginResult.reused(device.getName());
        }

        return ClinicDeviceLoginResult.invalid();
    }

    @Override
    @Transactional
    public void recordLoginReuse(Long deviceId, String username, String ip) {
        LocalDateTime now = now();
        trustedDeviceRepo.findByIdAndKind(deviceId, KIND)
                .filter(device -> device.isActive(now))
                .ifPresent(device -> markSuspectedCopy(device, username, ip, now));
    }

    private String issueToken(TrustedDevice device, String username, String ip, LocalDateTime now) {
        String newToken = SecureTokens.randomToken(TOKEN_BYTES);
        device.setTokenHash(SecureTokens.sha256Hex(newToken));
        device.setLastUsedAt(now);
        device.setLastSeenUsername(username);
        device.setLastIp(truncate(ip, IP_MAX_LENGTH));
        trustedDeviceRepo.save(device);
        clinicDeviceResolver.invalidateAllowanceCache();
        return newToken;
    }

    private void markSuspectedCopy(TrustedDevice device, String username, String ip, LocalDateTime now) {
        if (device.getSuspectedCopyAt() == null) {
            device.setSuspectedCopyAt(now);
            trustedDeviceRepo.save(device);
            clinicDeviceResolver.invalidateAllowanceCache();
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", device.getName());
        details.put("ip", ip);
        details.put("username", username);
        auditLogService.logSecurity("Security-Device-Reuse", device.getId(), device.getName(), details, device.getName());
    }

    private boolean isRotationRetry(TrustedDevice device, String ip, String userAgent, LocalDateTime now) {
        return device.getRotatedAt() != null
                && !device.getRotatedAt().plus(clinicDeviceSettings.getReuseGrace()).isBefore(now)
                && Objects.equals(device.getLastIp(), truncate(ip, IP_MAX_LENGTH))
                && Objects.equals(device.getLastUserAgentHash(), userAgentHash(userAgent));
    }

    private static LoginDeviceCheck.Status statusOf(TrustedDevice device, LocalDateTime now) {
        if (!device.isActive(now)) {
            return LoginDeviceCheck.Status.REVOKED;
        }
        if (device.getSuspectedCopyAt() != null) {
            return LoginDeviceCheck.Status.SUSPECTED_COPY;
        }
        return LoginDeviceCheck.Status.ALLOWED;
    }

    private static String userAgentHash(String userAgent) {
        return SecureTokens.sha256Hex(userAgent != null ? userAgent : "");
    }

    private ClinicDeviceRegistration completeRegistration(DeviceRegistrationCode registrationCode, String userAgent,
                                                          String ip, LocalDateTime now) {
        TrustedDevice replaced = registrationCode.getReplaceDeviceId() != null
                ? trustedDeviceRepo.findByIdAndKind(registrationCode.getReplaceDeviceId(), KIND)
                        .orElseThrow(() -> new ResourceNotFoundException(DEVICE_NOT_FOUND))
                : null;
        String name = replaced != null ? replaced.getName() : registrationCode.getName();
        if (replaced == null) {
            ensureNoActiveDeviceNamed(name, null);
        }

        String token = SecureTokens.randomToken(TOKEN_BYTES);
        TrustedDevice device = trustedDeviceRepo.save(TrustedDevice.builder()
                .kind(KIND)
                .tokenHash(SecureTokens.sha256Hex(token))
                .label(DeviceLabelParser.label(userAgent))
                .userAgent(truncate(userAgent, USER_AGENT_MAX_LENGTH))
                .lastIp(truncate(ip, IP_MAX_LENGTH))
                .createdAt(now)
                .lastUsedAt(now)
                .name(name)
                .registeredBy(registrationCode.getCreatedBy())
                .build());

        if (replaced != null) {
            if (replaced.getRevokedAt() == null) {
                replaced.setRevokedAt(now);
            }
            if (replaced.getReplacedById() == null) {
                replaced.setReplacedById(device.getId());
            }
            trustedDeviceRepo.save(replaced);
            clinicDeviceResolver.invalidateAllowanceCache();
        }

        registrationCode.setUsedAt(now);
        registrationCodeRepo.save(registrationCode);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("name", name);
        details.put("replaced", replaced != null);
        details.put("ip", ip);
        auditLogService.logSecurity("Device-Registered", device.getId(), name, details, name);
        return new ClinicDeviceRegistration(token, name);
    }

    private String generateUniqueCode(LocalDateTime now) {
        for (int i = 0; i < MAX_CODE_GENERATION_ATTEMPTS; i++) {
            String candidate = SecureTokens.randomFromAlphabet(CODE_ALPHABET, CODE_LENGTH);
            if (!registrationCodeRepo.existsActiveByCode(candidate, now)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique device registration code");
    }

    private void validateName(String name, Long excludedDeviceId, LocalDateTime now) {
        if (name.length() > NAME_MAX_LENGTH) {
            throw new ValidationException(NAME_TOO_LONG);
        }
        ensureNoActiveDeviceNamed(name, excludedDeviceId);
        if (registrationCodeRepo.existsActiveByName(name, now)) {
            throw new ConflictException(NAME_TAKEN);
        }
    }

    private void ensureNoActiveDeviceNamed(String name, Long excludedDeviceId) {
        boolean taken = trustedDeviceRepo.findActiveByName(KIND, name).stream()
                .anyMatch(device -> !device.getId().equals(excludedDeviceId));
        if (taken) {
            throw new ConflictException(NAME_TAKEN);
        }
    }

    private TrustedDevice findActiveDevice(Long deviceId, LocalDateTime now) {
        return trustedDeviceRepo.findByIdAndKind(deviceId, KIND)
                .filter(device -> device.isActive(now))
                .orElseThrow(() -> new ResourceNotFoundException(DEVICE_NOT_FOUND));
    }

    private Map<Long, String> deviceNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return trustedDeviceRepo.findAllById(ids).stream()
                .filter(device -> device.getName() != null)
                .collect(Collectors.toMap(TrustedDevice::getId, TrustedDevice::getName));
    }

    private static String nameOf(Map<Long, String> names, Long id) {
        return id != null ? names.get(id) : null;
    }

    private TooManyAttemptsException blockedException(String subject) {
        return new TooManyAttemptsException(String.format(BLOCKED, deviceCodeAttemptService.getBlockRemainingMinutes(subject)));
    }

    private static Map<String, Object> codeAuditDetails(String name, String replaceDeviceName, String by) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (name != null) {
            details.put("name", name);
        } else {
            details.put("replace", replaceDeviceName);
        }
        details.put("by", by);
        return details;
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]", "");
        return normalized.length() == CODE_LENGTH ? normalized : null;
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

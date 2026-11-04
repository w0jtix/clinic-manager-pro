package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.config.security.ClinicDeviceCookie;
import com.clinic.clinicmanager.model.TrustedDevice;
import com.clinic.clinicmanager.model.constants.TrustedDeviceKind;
import com.clinic.clinicmanager.repo.TrustedDeviceRepo;
import com.clinic.clinicmanager.utils.RequestContextUtils;
import com.clinic.clinicmanager.utils.SecureTokens;
import com.clinic.clinicmanager.utils.TransactionCallbacks;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ClinicDeviceResolver {

    private static final long ALLOWANCE_TTL_NANOS = 30_000_000_000L;
    private static final int ALLOWANCE_CACHE_MAX_ENTRIES = 1_000;
    private static final String RESOLVED_NAME_ATTRIBUTE = ClinicDeviceResolver.class.getName() + ".resolvedName";

    private final TrustedDeviceRepo trustedDeviceRepo;
    private final ClinicDeviceCookie clinicDeviceCookie;
    private final Clock businessClock;

    private final Map<String, CachedAllowance> allowanceCache = new ConcurrentHashMap<>();

    public Optional<TrustedDevice> resolve(HttpServletRequest request) {
        return resolveToken(clinicDeviceCookie.read(request));
    }

    public Optional<TrustedDevice> resolveToken(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(businessClock);
        return trustedDeviceRepo.findByTokenHashAndKind(SecureTokens.sha256Hex(rawToken), TrustedDeviceKind.CLINIC_DEVICE)
                .filter(device -> device.isActive(now));
    }

    public Optional<String> resolveName(HttpServletRequest request) {
        return resolve(request).map(TrustedDevice::getName);
    }

    public Optional<String> resolveCurrentName() {
        return RequestContextUtils.getCurrentRequest().flatMap(this::resolveNameOncePerRequest);
    }

    private Optional<String> resolveNameOncePerRequest(HttpServletRequest request) {
        Object cached = request.getAttribute(RESOLVED_NAME_ATTRIBUTE);
        if (cached instanceof String name) {
            return name.isEmpty() ? Optional.empty() : Optional.of(name);
        }
        Optional<String> name = resolveName(request);
        request.setAttribute(RESOLVED_NAME_ATTRIBUTE, name.orElse(""));
        return name;
    }

    public boolean isAllowed(HttpServletRequest request) {
        return isAllowedToken(clinicDeviceCookie.read(request));
    }

    public boolean isAllowedToken(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return false;
        }
        String tokenHash = SecureTokens.sha256Hex(rawToken);
        long nowNanos = System.nanoTime();
        CachedAllowance cached = allowanceCache.get(tokenHash);
        if (cached != null && nowNanos - cached.cachedAtNanos() <= ALLOWANCE_TTL_NANOS) {
            return cached.allowed();
        }
        LocalDateTime now = LocalDateTime.now(businessClock);
        boolean allowed = trustedDeviceRepo.findByTokenHashAndKind(tokenHash, TrustedDeviceKind.CLINIC_DEVICE)
                .filter(device -> device.isActive(now))
                .filter(device -> device.getSuspectedCopyAt() == null)
                .isPresent();
        if (allowanceCache.size() >= ALLOWANCE_CACHE_MAX_ENTRIES) {
            allowanceCache.clear();
        }
        allowanceCache.put(tokenHash, new CachedAllowance(allowed, nowNanos));
        return allowed;
    }

    public void invalidateAllowanceCache() {
        TransactionCallbacks.runNowAndAfterCommit(allowanceCache::clear);
    }

    private record CachedAllowance(boolean allowed, long cachedAtNanos) {
    }
}

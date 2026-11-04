package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.config.security.ClinicDeviceCookie;
import com.clinic.clinicmanager.model.TrustedDevice;
import com.clinic.clinicmanager.model.constants.TrustedDeviceKind;
import com.clinic.clinicmanager.repo.TrustedDeviceRepo;
import com.clinic.clinicmanager.service.impl.ClinicDeviceResolver;
import com.clinic.clinicmanager.utils.SecureTokens;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClinicDeviceResolverTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T10:00:00Z"), ZoneId.of("Europe/Warsaw"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock TrustedDeviceRepo trustedDeviceRepo;

    private ClinicDeviceResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ClinicDeviceResolver(trustedDeviceRepo, new ClinicDeviceCookie(true), CLOCK);
    }

    private static MockHttpServletRequest requestWithCookie(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("pc_dev", value));
        return request;
    }

    @Test
    void resolveName_shouldReturnNameWithoutRotating_whenActiveDeviceMatches() {
        TrustedDevice device = TrustedDevice.builder()
                .id(5L)
                .kind(TrustedDeviceKind.CLINIC_DEVICE)
                .tokenHash(SecureTokens.sha256Hex("clinic-token"))
                .name("Recepcja")
                .createdAt(NOW.minusDays(1))
                .build();
        when(trustedDeviceRepo.findByTokenHashAndKind(SecureTokens.sha256Hex("clinic-token"), TrustedDeviceKind.CLINIC_DEVICE))
                .thenReturn(Optional.of(device));

        assertEquals(Optional.of("Recepcja"), resolver.resolveName(requestWithCookie("clinic-token")));
        verify(trustedDeviceRepo, never()).save(any());
    }

    @Test
    void resolveName_shouldReturnEmpty_whenDeviceRevoked() {
        TrustedDevice device = TrustedDevice.builder()
                .id(5L)
                .kind(TrustedDeviceKind.CLINIC_DEVICE)
                .tokenHash(SecureTokens.sha256Hex("clinic-token"))
                .name("Recepcja")
                .createdAt(NOW.minusDays(1))
                .revokedAt(NOW.minusHours(1))
                .build();
        when(trustedDeviceRepo.findByTokenHashAndKind(SecureTokens.sha256Hex("clinic-token"), TrustedDeviceKind.CLINIC_DEVICE))
                .thenReturn(Optional.of(device));

        assertTrue(resolver.resolveName(requestWithCookie("clinic-token")).isEmpty());
    }

    @Test
    void resolveName_shouldReturnEmptyWithoutQuery_whenNoCookie() {
        assertTrue(resolver.resolveName(new MockHttpServletRequest()).isEmpty());
        verifyNoInteractions(trustedDeviceRepo);
    }

    private TrustedDevice activeDevice() {
        return TrustedDevice.builder()
                .id(5L)
                .kind(TrustedDeviceKind.CLINIC_DEVICE)
                .tokenHash(SecureTokens.sha256Hex("clinic-token"))
                .name("Recepcja")
                .createdAt(NOW.minusDays(1))
                .build();
    }

    @Test
    void isAllowed_shouldCacheResult_untilInvalidated() {
        when(trustedDeviceRepo.findByTokenHashAndKind(SecureTokens.sha256Hex("clinic-token"), TrustedDeviceKind.CLINIC_DEVICE))
                .thenReturn(Optional.of(activeDevice()));

        assertTrue(resolver.isAllowed(requestWithCookie("clinic-token")));
        assertTrue(resolver.isAllowed(requestWithCookie("clinic-token")));
        verify(trustedDeviceRepo, times(1)).findByTokenHashAndKind(any(), any());

        resolver.invalidateAllowanceCache();
        assertTrue(resolver.isAllowed(requestWithCookie("clinic-token")));
        verify(trustedDeviceRepo, times(2)).findByTokenHashAndKind(any(), any());
    }

    @Test
    void isAllowed_shouldRejectSuspectedCopy() {
        TrustedDevice suspected = activeDevice();
        suspected.setSuspectedCopyAt(NOW.minusHours(1));
        when(trustedDeviceRepo.findByTokenHashAndKind(SecureTokens.sha256Hex("clinic-token"), TrustedDeviceKind.CLINIC_DEVICE))
                .thenReturn(Optional.of(suspected));

        assertFalse(resolver.isAllowed(requestWithCookie("clinic-token")));
    }

    @Test
    void isAllowed_shouldRejectWithoutQuery_whenNoCookie() {
        assertFalse(resolver.isAllowed(new MockHttpServletRequest()));
        verifyNoInteractions(trustedDeviceRepo);
    }

    @Test
    void resolveCurrentName_shouldQueryOncePerRequest_includingEmptyResult() {
        when(trustedDeviceRepo.findByTokenHashAndKind(SecureTokens.sha256Hex("unknown-token"), TrustedDeviceKind.CLINIC_DEVICE))
                .thenReturn(Optional.empty());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requestWithCookie("unknown-token")));
        try {
            assertTrue(resolver.resolveCurrentName().isEmpty());
            assertTrue(resolver.resolveCurrentName().isEmpty());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        verify(trustedDeviceRepo, times(1)).findByTokenHashAndKind(any(), any());
    }

    @Test
    void resolveCurrentName_shouldReuseResolvedName_withinRequest() {
        when(trustedDeviceRepo.findByTokenHashAndKind(SecureTokens.sha256Hex("clinic-token"), TrustedDeviceKind.CLINIC_DEVICE))
                .thenReturn(Optional.of(activeDevice()));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requestWithCookie("clinic-token")));
        try {
            assertEquals(Optional.of("Recepcja"), resolver.resolveCurrentName());
            assertEquals(Optional.of("Recepcja"), resolver.resolveCurrentName());
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }

        verify(trustedDeviceRepo, times(1)).findByTokenHashAndKind(any(), any());
    }
}

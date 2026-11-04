package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.RememberedDeviceToken;
import com.clinic.clinicmanager.DTO.TrustedDeviceDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.TrustedDevice;
import com.clinic.clinicmanager.model.constants.TrustedDeviceKind;
import com.clinic.clinicmanager.repo.TrustedDeviceRepo;
import com.clinic.clinicmanager.repo.projection.UserDeviceCountProjection;
import com.clinic.clinicmanager.service.impl.TrustedDeviceServiceImpl;
import com.clinic.clinicmanager.utils.SecureTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrustedDeviceServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final String USERNAME = "owner";
    private static final String CHROME_WINDOWS_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Europe/Warsaw"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);
    private static final TrustedDeviceKind KIND = TrustedDeviceKind.MFA_REMEMBER;

    @Mock TrustedDeviceRepo trustedDeviceRepo;
    @Mock AuditLogService auditLogService;

    private TrustedDeviceServiceImpl trustedDeviceService;

    @BeforeEach
    void setUp() {
        trustedDeviceService = new TrustedDeviceServiceImpl(trustedDeviceRepo, auditLogService, CLOCK);
    }

    private TrustedDevice device(String rawToken, LocalDateTime expiresAt, LocalDateTime revokedAt) {
        return TrustedDevice.builder()
                .id(5L)
                .userId(USER_ID)
                .kind(KIND)
                .tokenHash(SecureTokens.sha256Hex(rawToken))
                .label("Chrome · Windows")
                .createdAt(NOW.minusDays(10))
                .lastUsedAt(NOW.minusDays(1))
                .expiresAt(expiresAt)
                .revokedAt(revokedAt)
                .build();
    }

    @Test
    void rememberDevice_shouldStoreOnlyHashWithLabelAnd180DayExpiry() {
        when(trustedDeviceRepo.save(any(TrustedDevice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RememberedDeviceToken token = trustedDeviceService.rememberDevice(USER_ID, CHROME_WINDOWS_UA, "10.0.0.5");

        ArgumentCaptor<TrustedDevice> saved = ArgumentCaptor.forClass(TrustedDevice.class);
        verify(trustedDeviceRepo).save(saved.capture());
        TrustedDevice device = saved.getValue();
        assertEquals(43, token.token().length());
        assertEquals(SecureTokens.sha256Hex(token.token()), device.getTokenHash());
        assertNotEquals(token.token(), device.getTokenHash());
        assertEquals(KIND, device.getKind());
        assertEquals(USER_ID, device.getUserId());
        assertEquals("Chrome · Windows", device.getLabel());
        assertEquals("10.0.0.5", device.getLastIp());
        assertEquals(NOW, device.getCreatedAt());
        assertEquals(NOW.plusDays(180), device.getExpiresAt());
        assertEquals(NOW.plusDays(180), token.expiresAt());
        assertNull(device.getRevokedAt());
    }

    @Test
    void rotateRemembered_shouldIssueNewTokenAndKeepExpiry_whenDeviceActive() {
        LocalDateTime expiresAt = NOW.plusDays(90);
        TrustedDevice device = device("old-token", expiresAt, null);
        when(trustedDeviceRepo.findByTokenHashAndUserIdAndKind(SecureTokens.sha256Hex("old-token"), USER_ID, KIND))
                .thenReturn(Optional.of(device));

        Optional<RememberedDeviceToken> rotated = trustedDeviceService.rotateRemembered(USER_ID, USERNAME, "old-token", CHROME_WINDOWS_UA, "10.0.0.9");

        assertTrue(rotated.isPresent());
        assertNotEquals("old-token", rotated.get().token());
        assertEquals(expiresAt, rotated.get().expiresAt());
        assertEquals(SecureTokens.sha256Hex(rotated.get().token()), device.getTokenHash());
        assertEquals(SecureTokens.sha256Hex("old-token"), device.getPreviousTokenHash());
        assertEquals(NOW, device.getLastUsedAt());
        assertEquals("10.0.0.9", device.getLastIp());
        assertEquals(expiresAt, device.getExpiresAt());
        verify(trustedDeviceRepo).save(device);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void rotateRemembered_shouldReturnEmpty_whenDeviceExpired() {
        TrustedDevice device = device("old-token", NOW.minusSeconds(1), null);
        when(trustedDeviceRepo.findByTokenHashAndUserIdAndKind(SecureTokens.sha256Hex("old-token"), USER_ID, KIND))
                .thenReturn(Optional.of(device));

        Optional<RememberedDeviceToken> rotated = trustedDeviceService.rotateRemembered(USER_ID, USERNAME, "old-token", CHROME_WINDOWS_UA, "10.0.0.9");

        assertTrue(rotated.isEmpty());
        verify(trustedDeviceRepo, never()).save(any());
    }

    @Test
    void rotateRemembered_shouldReturnEmpty_whenDeviceRevoked() {
        TrustedDevice device = device("old-token", NOW.plusDays(30), NOW.minusDays(1));
        when(trustedDeviceRepo.findByTokenHashAndUserIdAndKind(SecureTokens.sha256Hex("old-token"), USER_ID, KIND))
                .thenReturn(Optional.of(device));

        assertTrue(trustedDeviceService.rotateRemembered(USER_ID, USERNAME, "old-token", CHROME_WINDOWS_UA, "10.0.0.9").isEmpty());
        verify(trustedDeviceRepo, never()).save(any());
    }

    @Test
    void rotateRemembered_shouldRevokeAndAudit_whenPreviousTokenReused() {
        TrustedDevice device = device("current-token", NOW.plusDays(30), null);
        device.setPreviousTokenHash(SecureTokens.sha256Hex("stolen-token"));
        when(trustedDeviceRepo.findByTokenHashAndUserIdAndKind(SecureTokens.sha256Hex("stolen-token"), USER_ID, KIND))
                .thenReturn(Optional.empty());
        when(trustedDeviceRepo.findFirstByPreviousTokenHashAndUserIdAndKind(SecureTokens.sha256Hex("stolen-token"), USER_ID, KIND))
                .thenReturn(Optional.of(device));

        Optional<RememberedDeviceToken> rotated = trustedDeviceService.rotateRemembered(USER_ID, USERNAME, "stolen-token", CHROME_WINDOWS_UA, "10.0.0.66");

        assertTrue(rotated.isEmpty());
        assertEquals(NOW, device.getRevokedAt());
        verify(trustedDeviceRepo).save(device);
        verify(auditLogService).logSecurity(eq("Security-2FA-TrustedDevice-Reuse"), eq(USER_ID), eq(USERNAME), any());
    }

    @Test
    void rotateRemembered_shouldReturnEmptyWithoutLookup_whenNoCookie() {
        assertTrue(trustedDeviceService.rotateRemembered(USER_ID, USERNAME, null, CHROME_WINDOWS_UA, "10.0.0.9").isEmpty());
        assertTrue(trustedDeviceService.rotateRemembered(USER_ID, USERNAME, "", CHROME_WINDOWS_UA, "10.0.0.9").isEmpty());
        verifyNoInteractions(trustedDeviceRepo, auditLogService);
    }

    @Test
    void rotateRemembered_shouldReturnEmpty_whenTokenUnknown() {
        String hash = SecureTokens.sha256Hex("unknown");
        when(trustedDeviceRepo.findByTokenHashAndUserIdAndKind(hash, USER_ID, KIND)).thenReturn(Optional.empty());
        when(trustedDeviceRepo.findFirstByPreviousTokenHashAndUserIdAndKind(hash, USER_ID, KIND)).thenReturn(Optional.empty());

        assertTrue(trustedDeviceService.rotateRemembered(USER_ID, USERNAME, "unknown", CHROME_WINDOWS_UA, "10.0.0.9").isEmpty());
        verify(trustedDeviceRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void revokeAllRemembered_shouldRevokeOnlyRememberKindForUser() {
        when(trustedDeviceRepo.revokeAll(USER_ID, KIND, NOW)).thenReturn(3);

        assertEquals(3, trustedDeviceService.revokeAllRemembered(USER_ID));
        verify(trustedDeviceRepo).revokeAll(USER_ID, KIND, NOW);
    }

    @Test
    void revokeRemembered_shouldReturn404_whenDeviceNotOwned() {
        when(trustedDeviceRepo.findByIdAndUserIdAndKind(99L, USER_ID, KIND)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> trustedDeviceService.revokeRemembered(USER_ID, 99L));
        verify(trustedDeviceRepo, never()).save(any());
    }

    @Test
    void countActiveRemembered_shouldCountActiveRememberKindAtCurrentTime() {
        when(trustedDeviceRepo.countActive(USER_ID, KIND, NOW)).thenReturn(4L);

        assertEquals(4, trustedDeviceService.countActiveRemembered(USER_ID));
    }

    @Test
    void countActiveRememberedByUser_shouldMapGroupedRows() {
        when(trustedDeviceRepo.countActiveGroupedByUser(KIND, NOW)).thenReturn(List.of(countRow(7L, 2L), countRow(9L, 1L)));

        assertEquals(Map.of(7L, 2, 9L, 1), trustedDeviceService.countActiveRememberedByUser());
    }

    private static UserDeviceCountProjection countRow(Long userId, Long count) {
        return new UserDeviceCountProjection() {
            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public Long getDeviceCount() {
                return count;
            }
        };
    }

    @Test
    void revokeRemembered_shouldReturn404_whenDeviceAlreadyRevoked() {
        when(trustedDeviceRepo.findByIdAndUserIdAndKind(5L, USER_ID, KIND))
                .thenReturn(Optional.of(device("token", NOW.plusDays(30), NOW.minusDays(2))));

        assertThrows(ResourceNotFoundException.class, () -> trustedDeviceService.revokeRemembered(USER_ID, 5L));
    }

    @Test
    void revokeRemembered_shouldMarkRevokedAndReturnLabel() {
        TrustedDevice device = device("token", NOW.plusDays(30), null);
        when(trustedDeviceRepo.findByIdAndUserIdAndKind(5L, USER_ID, KIND)).thenReturn(Optional.of(device));

        assertEquals("Chrome · Windows", trustedDeviceService.revokeRemembered(USER_ID, 5L));
        assertEquals(NOW, device.getRevokedAt());
        verify(trustedDeviceRepo).save(device);
    }

    @Test
    void getActiveRemembered_shouldKeepLastUsedOrderAndMarkCurrentDevice() {
        TrustedDevice other = device("other", NOW.plusDays(30), null);
        TrustedDevice current = device("mine", NOW.plusDays(30), null);
        current.setId(6L);
        when(trustedDeviceRepo.findActiveOrderByLastUsed(USER_ID, KIND, NOW)).thenReturn(List.of(other, current));

        List<TrustedDeviceDTO> devices = trustedDeviceService.getActiveRemembered(USER_ID, "mine");

        assertEquals(List.of(5L, 6L), devices.stream().map(TrustedDeviceDTO::getId).toList());
        assertFalse(devices.get(0).isCurrent());
        assertTrue(devices.get(1).isCurrent());
        assertEquals("Chrome · Windows", devices.get(0).getLabel());
    }

    @Test
    void getActiveRemembered_shouldNeverMarkCurrent_whenNoCookie() {
        when(trustedDeviceRepo.findActiveOrderByLastUsed(USER_ID, KIND, NOW))
                .thenReturn(List.of(device("a", NOW.plusDays(30), null)));

        List<TrustedDeviceDTO> devices = trustedDeviceService.getActiveRemembered(USER_ID, null);

        assertTrue(devices.stream().noneMatch(TrustedDeviceDTO::isCurrent));
    }
}

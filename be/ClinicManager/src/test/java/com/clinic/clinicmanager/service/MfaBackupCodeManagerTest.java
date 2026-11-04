package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.UserBackupCode;
import com.clinic.clinicmanager.repo.UserBackupCodeRepo;
import com.clinic.clinicmanager.service.impl.MfaBackupCodeManager;
import com.clinic.clinicmanager.service.impl.MfaCodeVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MfaBackupCodeManagerTest {

    private static final Long USER_ID = 7L;
    private static final String PENDING_CODES_KEY = "mfa:pending-codes:user:7";
    private static final String CODE_PATTERN = "[a-hj-km-np-z2-9]{4}-[a-hj-km-np-z2-9]{4}";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Europe/Warsaw"));

    @Mock UserBackupCodeRepo userBackupCodeRepo;
    @Mock MfaCodeVerifier mfaCodeVerifier;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private MfaBackupCodeManager manager;
    private User user;

    @BeforeEach
    void setUp() {
        manager = new MfaBackupCodeManager(userBackupCodeRepo, passwordEncoder, mfaCodeVerifier, redisTemplate, CLOCK);
        user = User.builder().id(USER_ID).username("owner").password("hash").build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void replace_shouldStoreTenHashedCodesReplacingOldOnes() {
        List<String> codes = manager.replace(USER_ID);

        assertEquals(10, codes.size());
        assertTrue(codes.stream().allMatch(code -> code.matches(CODE_PATTERN)));
        verify(userBackupCodeRepo).deleteAllByUserId(USER_ID);
        ArgumentCaptor<List<UserBackupCode>> saved = ArgumentCaptor.forClass(List.class);
        verify(userBackupCodeRepo).saveAll(saved.capture());
        assertEquals(10, saved.getValue().size());
        for (int i = 0; i < 10; i++) {
            String plain = codes.get(i).replace("-", "");
            UserBackupCode entity = saved.getValue().get(i);
            assertEquals(USER_ID, entity.getUserId());
            assertEquals(LocalDateTime.now(CLOCK), entity.getCreatedAt());
            assertNotEquals(plain, entity.getCodeHash());
            assertTrue(passwordEncoder.matches(plain, entity.getCodeHash()));
        }
    }

    @Test
    void preparePending_shouldStoreHashesInRedisWithoutTouchingActiveCodes() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        List<String> codes = manager.preparePending(USER_ID);

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(eq(PENDING_CODES_KEY), stored.capture(), eq(15L), eq(TimeUnit.MINUTES));
        String[] hashes = stored.getValue().split(",");
        assertEquals(10, hashes.length);
        for (int i = 0; i < 10; i++) {
            String plain = codes.get(i).replace("-", "");
            assertFalse(stored.getValue().contains(plain));
            assertTrue(passwordEncoder.matches(plain, hashes[i]));
        }
        verifyNoInteractions(userBackupCodeRepo);
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmPending_shouldReplaceActiveCodesWithPendingHashes() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete(PENDING_CODES_KEY)).thenReturn("hash-1,hash-2,hash-3");

        assertEquals(3, manager.confirmPending(USER_ID));

        verify(userBackupCodeRepo).deleteAllByUserId(USER_ID);
        ArgumentCaptor<List<UserBackupCode>> saved = ArgumentCaptor.forClass(List.class);
        verify(userBackupCodeRepo).saveAll(saved.capture());
        assertEquals(List.of("hash-1", "hash-2", "hash-3"), saved.getValue().stream().map(UserBackupCode::getCodeHash).toList());
        assertTrue(saved.getValue().stream().allMatch(c -> USER_ID.equals(c.getUserId()) && c.getUsedAt() == null));
    }

    @Test
    void confirmPending_shouldReturn409_whenPendingCodesExpired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete(PENDING_CODES_KEY)).thenReturn(null);

        ConflictException ex = assertThrows(ConflictException.class, () -> manager.confirmPending(USER_ID));

        assertEquals("Nowe kody wygasły. Wygeneruj je ponownie.", ex.getMessage());
        verifyNoInteractions(userBackupCodeRepo);
    }

    @Test
    void consume_shouldAcceptNormalizedCodeOnlyOnce() {
        UserBackupCode backupCode = UserBackupCode.builder().id(11L).userId(USER_ID)
                .codeHash(passwordEncoder.encode("abcd2345")).build();
        when(mfaCodeVerifier.reserveAttempt(user)).thenReturn(1, 2);
        when(userBackupCodeRepo.findAllByUserIdAndUsedAtIsNull(USER_ID)).thenReturn(List.of(backupCode), new ArrayList<>());
        when(userBackupCodeRepo.markUsed(11L, LocalDateTime.now(CLOCK))).thenReturn(1);
        when(userBackupCodeRepo.countByUserIdAndUsedAtIsNull(USER_ID)).thenReturn(9);
        doThrow(new ValidationException("Kod zapasowy jest nieprawidłowy albo został już użyty."))
                .when(mfaCodeVerifier).registerFailure(eq(user), eq(2), any());

        assertEquals(9, manager.consume(user, " ABCD-2345 "));
        verify(mfaCodeVerifier).recordSuccess(user);

        assertThrows(ValidationException.class, () -> manager.consume(user, "abcd 2345"));
        verify(mfaCodeVerifier).registerFailure(eq(user), eq(2), argThat(message ->
                "Kod zapasowy jest nieprawidłowy albo został już użyty.".equals(message.apply(4))));
    }

    @Test
    void consume_shouldReject_whenConcurrentRequestUsedCodeFirst() {
        UserBackupCode backupCode = UserBackupCode.builder().id(11L).userId(USER_ID)
                .codeHash(passwordEncoder.encode("abcd2345")).build();
        when(mfaCodeVerifier.reserveAttempt(user)).thenReturn(2);
        when(userBackupCodeRepo.findAllByUserIdAndUsedAtIsNull(USER_ID)).thenReturn(List.of(backupCode));
        when(userBackupCodeRepo.markUsed(eq(11L), any())).thenReturn(0);
        doThrow(new ValidationException("Kod zapasowy jest nieprawidłowy albo został już użyty."))
                .when(mfaCodeVerifier).registerFailure(eq(user), eq(2), any());

        assertThrows(ValidationException.class, () -> manager.consume(user, "abcd-2345"));

        verify(mfaCodeVerifier, never()).recordSuccess(any());
    }

    @Test
    void consume_shouldCountMalformedCodeAsFailureWithoutHashing() {
        when(mfaCodeVerifier.reserveAttempt(user)).thenReturn(1);
        doThrow(new ValidationException("Kod zapasowy jest nieprawidłowy albo został już użyty."))
                .when(mfaCodeVerifier).registerFailure(eq(user), eq(1), any());

        assertThrows(ValidationException.class, () -> manager.consume(user, "abc-234!"));

        verify(userBackupCodeRepo, never()).findAllByUserIdAndUsedAtIsNull(any());
    }

    @Test
    void purge_shouldDeleteActiveAndPendingCodes() {
        manager.purge(USER_ID);

        verify(userBackupCodeRepo).deleteAllByUserId(USER_ID);
        verify(redisTemplate).delete(PENDING_CODES_KEY);
    }
}

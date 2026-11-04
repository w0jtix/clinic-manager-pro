package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.exceptions.StepUpException;
import com.clinic.clinicmanager.exceptions.TooManyAttemptsException;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.service.impl.MfaCodeVerifier;
import com.clinic.clinicmanager.service.impl.StepUpVerificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StepUpVerificationServiceImplTest {

    private static final Long USER_ID = 1L;

    @Mock UserRepo userRepo;
    @Mock StepUpAttemptService stepUpAttemptService;
    @Mock MfaCodeVerifier mfaCodeVerifier;
    @Mock PasswordEncoder passwordEncoder;

    private StepUpVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StepUpVerificationServiceImpl(userRepo, stepUpAttemptService, mfaCodeVerifier, passwordEncoder);
    }

    private User userWithMfa(boolean mfa) {
        User user = User.builder().id(USER_ID).username("admin").password("bcrypt-hash").build();
        if (mfa) {
            user.setMfaSecretEnc("encrypted-secret");
            user.setMfaEnabledAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        }
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void verify_shouldAcceptValidTotpAndResetCounter_whenMfaEnabled() {
        User user = userWithMfa(true);
        when(stepUpAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.of(1));
        when(mfaCodeVerifier.checkCode(user, "123456")).thenReturn(true);

        service.verify(USER_ID, "123456", null);

        verify(stepUpAttemptService).recordSuccess(USER_ID);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void verify_shouldRequireCodeNotPassword_whenMfaEnabled() {
        userWithMfa(true);

        StepUpException ex = assertThrows(StepUpException.class, () -> service.verify(USER_ID, null, "password"));

        assertEquals(StepUpException.REQUIRED, ex.getCode());
        assertEquals("Podaj kod z aplikacji.", ex.getMessage());
        verify(stepUpAttemptService, never()).reserveAttempt(anyLong());
    }

    @Test
    void verify_shouldRejectInvalidCodeAndCountFailure() {
        User user = userWithMfa(true);
        when(stepUpAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.of(2));
        when(mfaCodeVerifier.checkCode(user, "000000")).thenReturn(false);
        when(stepUpAttemptService.recordFailure(USER_ID, "admin", 2)).thenReturn(3);

        StepUpException ex = assertThrows(StepUpException.class, () -> service.verify(USER_ID, "000000", null));

        assertEquals(StepUpException.INVALID, ex.getCode());
        assertEquals("Nieprawidłowy kod.", ex.getMessage());
        verify(stepUpAttemptService, never()).recordSuccess(anyLong());
    }

    @Test
    void verify_shouldAcceptMatchingPassword_whenMfaDisabled() {
        userWithMfa(false);
        when(stepUpAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.of(1));
        when(passwordEncoder.matches("secret", "bcrypt-hash")).thenReturn(true);

        service.verify(USER_ID, "123456", "secret");

        verify(stepUpAttemptService).recordSuccess(USER_ID);
        verifyNoInteractions(mfaCodeVerifier);
    }

    @Test
    void verify_shouldRequirePassword_whenMfaDisabled() {
        userWithMfa(false);

        StepUpException ex = assertThrows(StepUpException.class, () -> service.verify(USER_ID, "123456", " "));

        assertEquals(StepUpException.REQUIRED, ex.getCode());
        assertEquals("Podaj hasło do konta.", ex.getMessage());
    }

    @Test
    void verify_shouldRejectWrongPassword() {
        userWithMfa(false);
        when(stepUpAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.of(1));
        when(passwordEncoder.matches("wrong", "bcrypt-hash")).thenReturn(false);
        when(stepUpAttemptService.recordFailure(USER_ID, "admin", 1)).thenReturn(4);

        StepUpException ex = assertThrows(StepUpException.class, () -> service.verify(USER_ID, null, "wrong"));

        assertEquals(StepUpException.INVALID, ex.getCode());
        assertEquals("Nieprawidłowe hasło.", ex.getMessage());
    }

    @Test
    void verify_shouldThrowTooManyAttempts_onFifthFailure() {
        userWithMfa(false);
        when(stepUpAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.of(5));
        when(passwordEncoder.matches("wrong", "bcrypt-hash")).thenReturn(false);
        when(stepUpAttemptService.recordFailure(USER_ID, "admin", 5)).thenReturn(0);
        when(stepUpAttemptService.getBlockRemainingMinutes(USER_ID)).thenReturn(15L);

        TooManyAttemptsException ex = assertThrows(TooManyAttemptsException.class, () -> service.verify(USER_ID, null, "wrong"));

        assertEquals("Zbyt wiele nieudanych prób. Spróbuj ponownie za 15 min.", ex.getMessage());
    }

    @Test
    void verify_shouldThrowTooManyAttemptsWithoutChecking_whenBlocked() {
        userWithMfa(false);
        when(stepUpAttemptService.isBlocked(USER_ID)).thenReturn(true);
        when(stepUpAttemptService.getBlockRemainingMinutes(USER_ID)).thenReturn(9L);

        TooManyAttemptsException ex = assertThrows(TooManyAttemptsException.class, () -> service.verify(USER_ID, null, "secret"));

        assertEquals("Zbyt wiele nieudanych prób. Spróbuj ponownie za 9 min.", ex.getMessage());
        verifyNoInteractions(passwordEncoder, mfaCodeVerifier);
        verify(stepUpAttemptService, never()).reserveAttempt(anyLong());
    }
}

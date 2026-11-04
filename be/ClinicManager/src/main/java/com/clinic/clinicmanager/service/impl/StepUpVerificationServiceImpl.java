package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.StepUpException;
import com.clinic.clinicmanager.exceptions.TooManyAttemptsException;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.service.StepUpAttemptService;
import com.clinic.clinicmanager.service.StepUpVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.OptionalInt;

@Service
@RequiredArgsConstructor
public class StepUpVerificationServiceImpl implements StepUpVerificationService {

    private static final String CODE_REQUIRED = "Podaj kod z aplikacji.";
    private static final String PASSWORD_REQUIRED = "Podaj hasło do konta.";
    private static final String CODE_INVALID = "Nieprawidłowy kod.";
    private static final String PASSWORD_INVALID = "Nieprawidłowe hasło.";
    private static final String BLOCKED = "Zbyt wiele nieudanych prób. Spróbuj ponownie za %d min.";

    private final UserRepo userRepo;
    private final StepUpAttemptService stepUpAttemptService;
    private final MfaCodeVerifier mfaCodeVerifier;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void verify(Long userId, String code, String password) {
        User user = userRepo.findOneById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono użytkownika."));
        if (stepUpAttemptService.isBlocked(userId)) {
            throw blockedException(userId);
        }

        boolean useCode = user.isMfaEnabled();
        if (useCode && !StringUtils.hasText(code)) {
            throw new StepUpException(StepUpException.REQUIRED, CODE_REQUIRED);
        }
        if (!useCode && !StringUtils.hasText(password)) {
            throw new StepUpException(StepUpException.REQUIRED, PASSWORD_REQUIRED);
        }

        OptionalInt attempt = stepUpAttemptService.reserveAttempt(userId);
        if (attempt.isEmpty()) {
            throw blockedException(userId);
        }

        boolean confirmed = useCode
                ? mfaCodeVerifier.checkCode(user, code)
                : passwordEncoder.matches(password, user.getPassword());
        if (!confirmed) {
            int remaining = stepUpAttemptService.recordFailure(userId, user.getUsername(), attempt.getAsInt());
            if (remaining <= 0) {
                throw blockedException(userId);
            }
            throw new StepUpException(StepUpException.INVALID, useCode ? CODE_INVALID : PASSWORD_INVALID);
        }
        stepUpAttemptService.recordSuccess(userId);
    }

    private TooManyAttemptsException blockedException(Long userId) {
        return new TooManyAttemptsException(String.format(BLOCKED, stepUpAttemptService.getBlockRemainingMinutes(userId)));
    }
}

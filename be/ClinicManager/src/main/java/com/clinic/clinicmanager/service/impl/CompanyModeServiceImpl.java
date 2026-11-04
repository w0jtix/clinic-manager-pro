package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.response.JwtResponse;
import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.TooManyAttemptsException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.CompanyModeService;
import com.clinic.clinicmanager.service.CompanyPinAttemptService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import com.clinic.clinicmanager.utils.CompanyPinValidator;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

@Service
@RequiredArgsConstructor
public class CompanyModeServiceImpl implements CompanyModeService {

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final TokenBlacklistService tokenBlacklistService;
    private final CompanyPinAttemptService companyPinAttemptService;
    private final AuditLogService auditLogService;

    @Override
    public JwtResponse elevate(String pin, String currentToken) {
        UserDetailsImpl principal = requireMasterPrincipal();
        if (currentToken != null && jwtUtils.isCompanyModeToken(currentToken)) {
            throw new ConflictException("Tryb firmowy jest już aktywny.");
        }
        User user = findUser(principal.getId());
        verifyPin(user, pin);

        String token = jwtUtils.generateJwtToken(user.getUsername(), true);
        blacklist(currentToken);
        auditLogService.logSecurity("Master-Elevation", user.getId(), user.getUsername(),
                Map.of("username", user.getUsername(), "action", "Włączono tryb firmowy"));

        List<GrantedAuthority> authorities = new ArrayList<>(principal.getAuthorities());
        authorities.add(new SimpleGrantedAuthority(AppRoleHierarchy.COMPANY));
        return JwtResponse.of(token, principal, AppRoleHierarchy.effectiveRoles(authorities), true);
    }

    @Override
    public JwtResponse dropElevation(String currentToken) {
        UserDetailsImpl principal = SessionUtils.requirePrincipal();
        if (currentToken == null || !jwtUtils.isCompanyModeToken(currentToken)) {
            throw new ConflictException("Tryb firmowy nie jest aktywny.");
        }

        String token = jwtUtils.generateJwtToken(principal.getUsername(), false);
        blacklist(currentToken);
        auditLogService.logSecurity("Master-Demotion", principal.getId(), principal.getUsername(),
                Map.of("username", principal.getUsername(), "action", "Wyłączono tryb firmowy"));

        return JwtResponse.of(token, principal, AppRoleHierarchy.effectiveRoles(principal.getAuthorities()), false);
    }

    @Override
    public String encodeCompanyPin(String pin) {
        CompanyPinValidator.validate(pin);
        return passwordEncoder.encode(pin);
    }

    @Override
    public void changeOwnPin(String currentPin, String newPin) {
        UserDetailsImpl principal = requireMasterPrincipal();
        CompanyPinValidator.validate(newPin);
        User user = findUser(principal.getId());
        verifyPin(user, currentPin);
        if (newPin.equals(currentPin)) {
            throw new ValidationException("Nowy PIN musi różnić się od obecnego.");
        }

        user.setCompanyPinHash(passwordEncoder.encode(newPin));
        userRepo.save(user);
        auditLogService.logSecurity("User-CompanyPin", user.getId(), user.getUsername(),
                Map.of("username", user.getUsername(), "action", "Zmieniono własny PIN trybu firmowego"));
    }

    @Override
    @Transactional
    public void setPinByAdmin(Long userId, String newPin) {
        User user = findUser(userId);
        boolean master = user.getRoles() != null && user.getRoles().stream()
                .anyMatch(role -> role.getName() == RoleType.ROLE_MASTER);
        if (!master) {
            throw new ConflictException("Użytkownik " + user.getUsername() + " nie posiada roli MASTER.");
        }

        user.setCompanyPinHash(encodeCompanyPin(newPin));
        userRepo.save(user);
        companyPinAttemptService.reset(userId);
        auditLogService.logSecurity("User-CompanyPin", user.getId(), user.getUsername(),
                Map.of("username", user.getUsername(), "action", "Ustawiono PIN trybu firmowego przez administratora"));
    }

    private void verifyPin(User user, String pin) {
        if (companyPinAttemptService.isBlocked(user.getId())) {
            throw blockedException(user.getId());
        }
        if (user.getCompanyPinHash() == null) {
            throw new ConflictException("PIN trybu firmowego nie został ustawiony. Skontaktuj się z administratorem.");
        }
        OptionalInt attempt = companyPinAttemptService.reserveAttempt(user.getId());
        if (attempt.isEmpty()) {
            throw blockedException(user.getId());
        }
        if (pin == null || !passwordEncoder.matches(pin, user.getCompanyPinHash())) {
            int remaining = companyPinAttemptService.recordFailure(user.getId(), user.getUsername(), attempt.getAsInt());
            if (remaining <= 0) {
                throw blockedException(user.getId());
            }
            throw new ValidationException("Nieprawidłowy PIN. Pozostało prób: " + remaining + ".");
        }
        companyPinAttemptService.recordSuccess(user.getId());
    }

    private TooManyAttemptsException blockedException(Long userId) {
        long minutes = companyPinAttemptService.getBlockRemainingMinutes(userId);
        return new TooManyAttemptsException("Zbyt wiele nieudanych prób podania PIN-u. Tryb firmowy jest zablokowany, spróbuj ponownie za "
                + minutes + " min.");
    }

    private UserDetailsImpl requireMasterPrincipal() {
        UserDetailsImpl principal = SessionUtils.requirePrincipal();
        boolean storedMaster = principal.getAuthorities().stream()
                .anyMatch(authority -> AppRoleHierarchy.MASTER.equals(authority.getAuthority()));
        if (!storedMaster) {
            throw new AccessDeniedException("Tryb firmowy jest dostępny tylko dla roli MASTER.");
        }
        return principal;
    }

    private User findUser(Long id) {
        return userRepo.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with given id: " + id));
    }

    private void blacklist(String token) {
        if (token == null) {
            return;
        }
        long remainingMs = jwtUtils.getRemainingExpirationMs(token);
        if (remainingMs > 0) {
            tokenBlacklistService.blacklist(token, remainingMs);
        }
    }
}

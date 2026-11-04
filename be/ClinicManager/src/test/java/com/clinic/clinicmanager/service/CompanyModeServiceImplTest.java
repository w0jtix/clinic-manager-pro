package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.response.JwtResponse;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.TooManyAttemptsException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.Role;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.service.impl.CompanyModeServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyModeServiceImplTest {

    @Mock UserRepo userRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtils jwtUtils;
    @Mock TokenBlacklistService tokenBlacklistService;
    @Mock CompanyPinAttemptService companyPinAttemptService;
    @Mock AuditLogService auditLogService;

    @InjectMocks
    CompanyModeServiceImpl service;

    private static final Long USER_ID = 7L;
    private static final String NORMAL_TOKEN = "normal-jwt";
    private static final String ELEVATED_TOKEN = "elevated-jwt";

    private User owner;

    @BeforeEach
    void setUp() {
        owner = User.builder()
                .id(USER_ID)
                .username("owner")
                .password("hash")
                .avatar("avatar1.png")
                .roles(Set.of(Role.builder().id(3L).name(RoleType.ROLE_MASTER).build()))
                .companyPinHash("pinHash")
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String... roles) {
        UserDetailsImpl principal = new UserDetailsImpl(USER_ID, "owner", "hash", "avatar1.png", null,
                AuthorityUtils.createAuthorityList(roles));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void elevate_shouldReturnElevatedToken_andBlacklistNormalToken_whenPinValid() {
        loginAs("ROLE_MASTER");
        when(jwtUtils.isCompanyModeToken(NORMAL_TOKEN)).thenReturn(false);
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(owner));
        when(companyPinAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.of(1));
        when(passwordEncoder.matches("482915", "pinHash")).thenReturn(true);
        when(jwtUtils.generateJwtToken("owner", true)).thenReturn(ELEVATED_TOKEN);
        when(jwtUtils.getRemainingExpirationMs(NORMAL_TOKEN)).thenReturn(60000L);

        JwtResponse response = service.elevate("482915", NORMAL_TOKEN);

        assertThat(response.getToken()).isEqualTo(ELEVATED_TOKEN);
        assertThat(response.isCompanyMode()).isTrue();
        assertThat(response.getRoles()).containsExactly("ROLE_COMPANY", "ROLE_MASTER", "ROLE_USER");
        assertThat(response.getId()).isEqualTo(USER_ID);
        verify(tokenBlacklistService).blacklist(NORMAL_TOKEN, 60000L);
        verify(companyPinAttemptService).recordSuccess(USER_ID);
        verify(auditLogService).logSecurity(eq("Master-Elevation"), eq(USER_ID), eq("owner"), any());
    }

    @Test
    void elevate_shouldThrowValidation_andCountFailure_whenPinWrong() {
        loginAs("ROLE_MASTER");
        when(jwtUtils.isCompanyModeToken(NORMAL_TOKEN)).thenReturn(false);
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(owner));
        when(companyPinAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.of(1));
        when(passwordEncoder.matches("111222", "pinHash")).thenReturn(false);
        when(companyPinAttemptService.recordFailure(USER_ID, "owner", 1)).thenReturn(4);

        assertThatThrownBy(() -> service.elevate("111222", NORMAL_TOKEN))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nieprawidłowy PIN. Pozostało prób: 4.");
        verify(jwtUtils, never()).generateJwtToken(anyString(), anyBoolean());
        verifyNoInteractions(tokenBlacklistService);
    }

    @Test
    void elevate_shouldThrowTooManyAttempts_whenFifthWrongPinBlocks() {
        loginAs("ROLE_MASTER");
        when(jwtUtils.isCompanyModeToken(NORMAL_TOKEN)).thenReturn(false);
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(owner));
        when(companyPinAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.of(5));
        when(passwordEncoder.matches("111222", "pinHash")).thenReturn(false);
        when(companyPinAttemptService.recordFailure(USER_ID, "owner", 5)).thenReturn(0);
        when(companyPinAttemptService.getBlockRemainingMinutes(USER_ID)).thenReturn(15L);

        assertThatThrownBy(() -> service.elevate("111222", NORMAL_TOKEN))
                .isInstanceOf(TooManyAttemptsException.class)
                .hasMessage("Zbyt wiele nieudanych prób podania PIN-u. Tryb firmowy jest zablokowany, spróbuj ponownie za 15 min.");
    }

    @Test
    void elevate_shouldThrowTooManyAttempts_withoutCheckingPin_whenBlocked() {
        loginAs("ROLE_MASTER");
        when(jwtUtils.isCompanyModeToken(NORMAL_TOKEN)).thenReturn(false);
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(owner));
        when(companyPinAttemptService.isBlocked(USER_ID)).thenReturn(true);
        when(companyPinAttemptService.getBlockRemainingMinutes(USER_ID)).thenReturn(9L);

        assertThatThrownBy(() -> service.elevate("482915", NORMAL_TOKEN))
                .isInstanceOf(TooManyAttemptsException.class)
                .hasMessageContaining("spróbuj ponownie za 9 min");
        verifyNoInteractions(passwordEncoder);
        verify(companyPinAttemptService, never()).reserveAttempt(any());
        verify(companyPinAttemptService, never()).recordFailure(any(), any(), anyInt());
    }

    @Test
    void elevate_shouldThrowTooManyAttempts_withoutComparingPin_whenAttemptLimitExceeded() {
        loginAs("ROLE_MASTER");
        when(jwtUtils.isCompanyModeToken(NORMAL_TOKEN)).thenReturn(false);
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(owner));
        when(companyPinAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.empty());
        when(companyPinAttemptService.getBlockRemainingMinutes(USER_ID)).thenReturn(15L);

        assertThatThrownBy(() -> service.elevate("482915", NORMAL_TOKEN))
                .isInstanceOf(TooManyAttemptsException.class)
                .hasMessage("Zbyt wiele nieudanych prób podania PIN-u. Tryb firmowy jest zablokowany, spróbuj ponownie za 15 min.");
        verify(passwordEncoder, never()).matches(any(), any());
        verify(companyPinAttemptService, never()).recordFailure(any(), any(), anyInt());
        verify(companyPinAttemptService, never()).recordSuccess(any());
        verify(jwtUtils, never()).generateJwtToken(anyString(), anyBoolean());
        verifyNoInteractions(tokenBlacklistService);
    }

    @Test
    void changeOwnPin_shouldThrowTooManyAttempts_withoutComparingPin_whenAttemptLimitExceeded() {
        loginAs("ROLE_MASTER", "ROLE_COMPANY");
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(owner));
        when(companyPinAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.empty());
        when(companyPinAttemptService.getBlockRemainingMinutes(USER_ID)).thenReturn(15L);

        assertThatThrownBy(() -> service.changeOwnPin("482915", "739164"))
                .isInstanceOf(TooManyAttemptsException.class);
        verify(passwordEncoder, never()).matches(any(), any());
        verify(userRepo, never()).save(any());
    }

    @Test
    void elevate_shouldThrowAccessDenied_whenUserHasNoStoredMasterRole() {
        loginAs("ROLE_ADMIN", "ROLE_USER");

        assertThatThrownBy(() -> service.elevate("482915", NORMAL_TOKEN))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(userRepo, passwordEncoder, companyPinAttemptService);
    }

    @Test
    void elevate_shouldThrowConflict_whenAlreadyElevated() {
        loginAs("ROLE_MASTER");
        when(jwtUtils.isCompanyModeToken(ELEVATED_TOKEN)).thenReturn(true);

        assertThatThrownBy(() -> service.elevate("482915", ELEVATED_TOKEN))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(passwordEncoder, companyPinAttemptService);
    }

    @Test
    void elevate_shouldThrowConflict_withoutCountingAttempt_whenPinNotSet() {
        loginAs("ROLE_MASTER");
        owner.setCompanyPinHash(null);
        when(jwtUtils.isCompanyModeToken(NORMAL_TOKEN)).thenReturn(false);
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.elevate("482915", NORMAL_TOKEN))
                .isInstanceOf(ConflictException.class);
        verify(companyPinAttemptService, never()).reserveAttempt(any());
        verify(companyPinAttemptService, never()).recordFailure(any(), any(), anyInt());
    }

    @Test
    void dropElevation_shouldReturnNormalToken_andBlacklistElevatedToken() {
        loginAs("ROLE_MASTER");
        when(jwtUtils.isCompanyModeToken(ELEVATED_TOKEN)).thenReturn(true);
        when(jwtUtils.generateJwtToken("owner", false)).thenReturn(NORMAL_TOKEN);
        when(jwtUtils.getRemainingExpirationMs(ELEVATED_TOKEN)).thenReturn(30000L);

        JwtResponse response = service.dropElevation(ELEVATED_TOKEN);

        assertThat(response.getToken()).isEqualTo(NORMAL_TOKEN);
        assertThat(response.isCompanyMode()).isFalse();
        assertThat(response.getRoles()).containsExactly("ROLE_MASTER", "ROLE_USER");
        verify(tokenBlacklistService).blacklist(ELEVATED_TOKEN, 30000L);
        verify(auditLogService).logSecurity(eq("Master-Demotion"), eq(USER_ID), eq("owner"), any());
        verify(auditLogService, never()).logSecurity(eq("Master-Elevation"), any(), any(), any());
    }

    @Test
    void dropElevation_shouldThrowConflict_whenTokenNotElevated() {
        loginAs("ROLE_MASTER");
        when(jwtUtils.isCompanyModeToken(NORMAL_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> service.dropElevation(NORMAL_TOKEN))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(tokenBlacklistService);
    }

    @Test
    void encodeCompanyPin_shouldRejectWeakPin_beforeEncoding() {
        assertThatThrownBy(() -> service.encodeCompanyPin("654321"))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void changeOwnPin_shouldSaveNewHash_whenCurrentPinValid() {
        loginAs("ROLE_MASTER", "ROLE_COMPANY");
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(owner));
        when(companyPinAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.of(1));
        when(passwordEncoder.matches("482915", "pinHash")).thenReturn(true);
        when(passwordEncoder.encode("739164")).thenReturn("newHash");

        service.changeOwnPin("482915", "739164");

        verify(userRepo).save(argThat(u -> "newHash".equals(u.getCompanyPinHash())));
        verify(auditLogService).logSecurity("User-CompanyPin", USER_ID, "owner",
                Map.of("username", "owner", "action", "Zmieniono własny PIN trybu firmowego"));
        verify(auditLogService, never()).logUpdate(any(), any(), any(), any(), any());
    }

    @Test
    void changeOwnPin_shouldCountFailure_whenCurrentPinWrong() {
        loginAs("ROLE_MASTER", "ROLE_COMPANY");
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(owner));
        when(companyPinAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.of(2));
        when(passwordEncoder.matches("111222", "pinHash")).thenReturn(false);
        when(companyPinAttemptService.recordFailure(USER_ID, "owner", 2)).thenReturn(3);

        assertThatThrownBy(() -> service.changeOwnPin("111222", "739164"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nieprawidłowy PIN. Pozostało prób: 3.");
        verify(userRepo, never()).save(any());
    }

    @Test
    void changeOwnPin_shouldRejectWeakNewPin_withoutCountingAttempt() {
        loginAs("ROLE_MASTER", "ROLE_COMPANY");

        assertThatThrownBy(() -> service.changeOwnPin("482915", "111111"))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(companyPinAttemptService, userRepo);
    }

    @Test
    void changeOwnPin_shouldRejectSamePin() {
        loginAs("ROLE_MASTER", "ROLE_COMPANY");
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(owner));
        when(companyPinAttemptService.reserveAttempt(USER_ID)).thenReturn(OptionalInt.of(1));
        when(passwordEncoder.matches("482915", "pinHash")).thenReturn(true);

        assertThatThrownBy(() -> service.changeOwnPin("482915", "482915"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nowy PIN musi różnić się od obecnego.");
        verify(userRepo, never()).save(any());
    }

    @Test
    void changeOwnPin_shouldThrowAccessDenied_forAdminWithoutStoredMaster() {
        loginAs("ROLE_ADMIN", "ROLE_USER");

        assertThatThrownBy(() -> service.changeOwnPin("482915", "739164"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void setPinByAdmin_shouldSaveHash_andResetLockout_whenTargetIsMaster() {
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(owner));
        when(passwordEncoder.encode("739164")).thenReturn("adminHash");

        service.setPinByAdmin(USER_ID, "739164");

        verify(userRepo).save(argThat(u -> "adminHash".equals(u.getCompanyPinHash())));
        verify(companyPinAttemptService).reset(USER_ID);
        verify(auditLogService).logSecurity("User-CompanyPin", USER_ID, "owner",
                Map.of("username", "owner", "action", "Ustawiono PIN trybu firmowego przez administratora"));
    }

    @Test
    void setPinByAdmin_shouldThrowConflict_whenTargetIsNotMaster() {
        owner.setRoles(Set.of(Role.builder().id(1L).name(RoleType.ROLE_USER).build()));
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.setPinByAdmin(USER_ID, "739164"))
                .isInstanceOf(ConflictException.class);
        verify(userRepo, never()).save(any());
    }

    @Test
    void setPinByAdmin_shouldRejectWeakPin() {
        when(userRepo.findOneById(USER_ID)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.setPinByAdmin(USER_ID, "012345"))
                .isInstanceOf(ValidationException.class);
        verify(userRepo, never()).save(any());
    }
}

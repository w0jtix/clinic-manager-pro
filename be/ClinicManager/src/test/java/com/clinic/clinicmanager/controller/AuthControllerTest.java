package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.ClinicDeviceLoginResult;
import com.clinic.clinicmanager.DTO.ClinicDeviceRegistration;
import com.clinic.clinicmanager.DTO.LoginDeviceCheck;
import com.clinic.clinicmanager.DTO.EmployeeSummaryDTO;
import com.clinic.clinicmanager.DTO.MfaLoginResult;
import com.clinic.clinicmanager.DTO.RememberedDeviceToken;
import com.clinic.clinicmanager.DTO.request.auth.ChangePasswordRequest;
import com.clinic.clinicmanager.DTO.request.auth.ForceChangePasswordRequest;
import com.clinic.clinicmanager.DTO.request.auth.LoginRequest;
import com.clinic.clinicmanager.DTO.request.auth.SignupRequest;
import com.clinic.clinicmanager.DTO.response.JwtResponse;
import com.clinic.clinicmanager.config.security.ClinicDeviceCookie;
import com.clinic.clinicmanager.config.security.MfaDeviceCookie;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.MfaSessionExpiredException;
import com.clinic.clinicmanager.exceptions.TooManyAttemptsException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.Role;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.LoginAttemptScope;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.repo.RoleRepo;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.service.AccessControlService;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.AvatarService;
import com.clinic.clinicmanager.service.ClinicDeviceService;
import com.clinic.clinicmanager.service.CompanyModeService;
import com.clinic.clinicmanager.service.EmployeeService;
import com.clinic.clinicmanager.service.LoginAttemptService;
import com.clinic.clinicmanager.service.MfaLoginService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import com.clinic.clinicmanager.service.TrustedDeviceService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({WebSecurityConfig.class, MfaDeviceCookie.class, ClinicDeviceCookie.class})
class AuthControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock businessClock() {
            return Clock.fixed(NOW, ZoneId.of("Europe/Warsaw"));
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserRepo userRepo;
    @MockBean RoleRepo roleRepo;
    @MockBean PasswordEncoder passwordEncoder;
    @MockBean AuthenticationManager authenticationManager;
    @MockBean JwtUtils jwtUtils;
    @MockBean EmployeeService employeeService;
    @MockBean AuditLogService auditLogService;
    @MockBean TokenBlacklistService tokenBlacklistService;
    @MockBean LoginAttemptService loginAttemptService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean CompanyModeService companyModeService;
    @MockBean MfaLoginService mfaLoginService;
    @MockBean TrustedDeviceService trustedDeviceService;
    @MockBean ClinicDeviceService clinicDeviceService;
    @MockBean AccessControlService accessControlService;
    @MockBean AvatarService avatarService;

    @BeforeEach
    void stubClinicDeviceRecognition() {
        when(clinicDeviceService.recognizeOnLogin(any(), any(), any(), any())).thenReturn(ClinicDeviceLoginResult.none());
        when(clinicDeviceService.inspectForLogin(any(), any(), any())).thenReturn(LoginDeviceCheck.unknown());
    }

    private static UserDetailsImpl userWith(Long id, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());
        return new UserDetailsImpl(id, "testuser", "pass", "avatar5.png", null, authorities);
    }

    @Test
    void login_shouldReturn200WithToken_whenCredentialsValid() throws Exception {
        UserDetailsImpl principal = userWith(1L, "USER");
        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtUtils.generateJwtToken(auth)).thenReturn("fake-jwt");

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("fake-jwt"))
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    void login_shouldReturn401_whenAuthenticationFails() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new RuntimeException("Bad credentials"));

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("wrong");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_shouldReturn429_whenUserIsBlocked() throws Exception {
        when(loginAttemptService.isBlocked(any(), any(), any())).thenReturn(true);

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void login_shouldReturn401_whenUserIsShadowBanned() throws Exception {
        when(loginAttemptService.isShadowBanned(any(), any(), any())).thenReturn(true);

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_shouldReturn200_whenAdmin() throws Exception {
        Role userRole = Role.builder().id(1L).name(RoleType.ROLE_USER).build();
        User savedUser = User.builder().id(1L).username("newuser").password("hash").roles(Set.of(userRole)).build();

        when(userRepo.existsByUsername("newuser")).thenReturn(false);
        when(roleRepo.findByName(RoleType.ROLE_USER)).thenReturn(Optional.of(userRole));
        when(userRepo.save(any(User.class))).thenReturn(savedUser);

        SignupRequest request = new SignupRequest();
        request.setUsername("newuser");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully!"));
    }

    @Test
    void register_shouldReturn403_whenUserRole() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setUsername("newuser");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .with(user(userWith(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_shouldReturn400_whenUsernameAlreadyExists() throws Exception {
        when(userRepo.existsByUsername("existinguser")).thenReturn(true);

        SignupRequest request = new SignupRequest();
        request.setUsername("existinguser");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldReturn400_whenAdminRoleRequested() throws Exception {
        when(userRepo.existsByUsername("newuser")).thenReturn(false);

        SignupRequest request = new SignupRequest();
        request.setUsername("newuser");
        request.setPassword("password123");
        request.setRole(Set.of(RoleType.ROLE_ADMIN, RoleType.ROLE_USER));

        mockMvc.perform(post("/api/auth/register")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Nie można nadać roli ADMIN."));

        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void register_shouldKeepOnlyMaster_whenUserAndMasterRequested() throws Exception {
        Role userRole = Role.builder().id(2L).name(RoleType.ROLE_USER).build();
        Role masterRole = Role.builder().id(3L).name(RoleType.ROLE_MASTER).build();
        User savedUser = User.builder().id(5L).username("newuser").password("hash").roles(Set.of(masterRole)).build();

        when(userRepo.existsByUsername("newuser")).thenReturn(false);
        when(roleRepo.findByName(RoleType.ROLE_USER)).thenReturn(Optional.of(userRole));
        when(roleRepo.findByName(RoleType.ROLE_MASTER)).thenReturn(Optional.of(masterRole));
        when(companyModeService.encodeCompanyPin("482915")).thenReturn("pinHash");
        when(userRepo.save(any(User.class))).thenReturn(savedUser);

        SignupRequest request = new SignupRequest();
        request.setUsername("newuser");
        request.setPassword("password123");
        request.setRole(Set.of(RoleType.ROLE_USER, RoleType.ROLE_MASTER));
        request.setCompanyPin("482915");

        mockMvc.perform(post("/api/auth/register")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(userRepo).save(argThat(u ->
                u.getRoles().equals(Set.of(masterRole)) && "pinHash".equals(u.getCompanyPinHash())));
    }

    @Test
    void logout_shouldReturn200_whenAuthenticated() throws Exception {
        when(jwtUtils.getRemainingExpirationMs("some-token")).thenReturn(60000L);

        mockMvc.perform(post("/api/auth/logout")
                        .with(user(userWith(1L, "USER")))
                        .header("Authorization", "Bearer some-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));
    }

    @Test
    void changePassword_shouldReturn200_whenOldPasswordMatches() throws Exception {
        User loggedUser = User.builder().id(1L).username("testuser").password("hashedOld").roles(Set.of()).build();
        when(userRepo.findOneById(1L)).thenReturn(Optional.of(loggedUser));
        when(passwordEncoder.matches("oldPass", "hashedOld")).thenReturn(true);
        when(passwordEncoder.encode("newPass123")).thenReturn("hashedNew");
        when(userRepo.save(any(User.class))).thenReturn(loggedUser);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("oldPass");
        request.setNewPassword("newPass123");

        mockMvc.perform(post("/api/auth/change-password")
                        .with(user(userWith(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
        verify(trustedDeviceService).revokeAllRemembered(1L);
        verify(auditLogService).logSecurity("User-Password", 1L, "testuser",
                Map.of("username", "testuser", "action", "Zmieniono własne hasło"));
    }

    @Test
    void changePassword_shouldReturn400_whenOldPasswordWrong() throws Exception {
        User loggedUser = User.builder().id(1L).username("testuser").password("hashedOld").roles(Set.of()).build();
        when(userRepo.findOneById(1L)).thenReturn(Optional.of(loggedUser));
        when(passwordEncoder.matches("wrongOld", "hashedOld")).thenReturn(false);

        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("wrongOld");
        request.setNewPassword("newPass123");

        mockMvc.perform(post("/api/auth/change-password")
                        .with(user(userWith(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forceChangePassword_shouldReturn200_whenAdmin() throws Exception {
        User targetUser = User.builder().id(2L).username("other").password("oldHash").roles(Set.of()).build();
        when(userRepo.findOneById(2L)).thenReturn(Optional.of(targetUser));
        when(passwordEncoder.encode("newPass123")).thenReturn("newHash");
        when(userRepo.save(any(User.class))).thenReturn(targetUser);

        ForceChangePasswordRequest request = new ForceChangePasswordRequest();
        request.setUserId(2L);
        request.setNewPassword("newPass123");

        mockMvc.perform(post("/api/auth/force-change-password")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));
        verify(trustedDeviceService).revokeAllRemembered(2L);
        verify(auditLogService).logSecurity("User-Password", 2L, "other",
                Map.of("username", "other", "action", "Wymuszono zmianę hasła przez administratora"));
    }

    @Test
    void forceChangePassword_shouldReturn403_whenUserRole() throws Exception {
        ForceChangePasswordRequest request = new ForceChangePasswordRequest();
        request.setUserId(2L);
        request.setNewPassword("newPass123");

        mockMvc.perform(post("/api/auth/force-change-password")
                        .with(user(userWith(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_shouldReturn400_whenPasswordHas9Chars() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setUsername("newuser");
        request.setPassword("pass12345");

        mockMvc.perform(post("/api/auth/register")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("password: Hasło musi mieć od 10 do 40 znaków."));

        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void register_shouldReturn200_whenPasswordHas10Chars() throws Exception {
        Role userRole = Role.builder().id(2L).name(RoleType.ROLE_USER).build();
        User savedUser = User.builder().id(1L).username("newuser").password("hash").roles(Set.of(userRole)).build();

        when(userRepo.existsByUsername("newuser")).thenReturn(false);
        when(roleRepo.findByName(RoleType.ROLE_USER)).thenReturn(Optional.of(userRole));
        when(passwordEncoder.encode("pass123456")).thenReturn("hash");
        when(userRepo.save(any(User.class))).thenReturn(savedUser);

        SignupRequest request = new SignupRequest();
        request.setUsername("newuser");
        request.setPassword("pass123456");

        mockMvc.perform(post("/api/auth/register")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully!"));

        verify(userRepo).save(argThat(u -> "hash".equals(u.getPassword())));
    }

    @Test
    void changePassword_shouldReturn400_whenNewPasswordHas9Chars() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setOldPassword("oldPass");
        request.setNewPassword("newPas123");

        mockMvc.perform(post("/api/auth/change-password")
                        .with(user(userWith(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("newPassword: Hasło musi mieć od 10 do 40 znaków."));

        verify(userRepo, never()).save(any(User.class));
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void forceChangePassword_shouldReturn400_whenNewPasswordHas9Chars() throws Exception {
        ForceChangePasswordRequest request = new ForceChangePasswordRequest();
        request.setUserId(2L);
        request.setNewPassword("newPas123");

        mockMvc.perform(post("/api/auth/force-change-password")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("newPassword: Hasło musi mieć od 10 do 40 znaków."));

        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void login_shouldReturnEffectiveRolesAndNormalMode_whenAdmin() throws Exception {
        UserDetailsImpl principal = userWith(1L, "ADMIN", "USER");
        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtUtils.generateJwtToken(auth)).thenReturn("fake-jwt");

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyMode").value(false))
                .andExpect(jsonPath("$.roles").value(contains("ROLE_ADMIN", "ROLE_COMPANY", "ROLE_MASTER", "ROLE_USER")));
    }

    @Test
    void register_shouldReturn400_whenMasterWithoutPin() throws Exception {
        Role masterRole = Role.builder().id(3L).name(RoleType.ROLE_MASTER).build();
        when(userRepo.existsByUsername("owner")).thenReturn(false);
        when(roleRepo.findByName(RoleType.ROLE_MASTER)).thenReturn(Optional.of(masterRole));

        SignupRequest request = new SignupRequest();
        request.setUsername("owner");
        request.setPassword("password123");
        request.setRole(Set.of(RoleType.ROLE_MASTER));

        mockMvc.perform(post("/api/auth/register")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("PIN trybu firmowego jest wymagany dla roli MASTER."));
        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void register_shouldReturn400_whenMasterPinIsWeak() throws Exception {
        Role masterRole = Role.builder().id(3L).name(RoleType.ROLE_MASTER).build();
        when(userRepo.existsByUsername("owner")).thenReturn(false);
        when(roleRepo.findByName(RoleType.ROLE_MASTER)).thenReturn(Optional.of(masterRole));
        when(companyModeService.encodeCompanyPin("123456"))
                .thenThrow(new ValidationException("PIN nie może być ciągiem kolejnych cyfr (np. 123456 lub 654321)."));

        SignupRequest request = new SignupRequest();
        request.setUsername("owner");
        request.setPassword("password123");
        request.setRole(Set.of(RoleType.ROLE_MASTER));
        request.setCompanyPin("123456");

        mockMvc.perform(post("/api/auth/register")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        verify(userRepo, never()).save(any(User.class));
    }

    @Test
    void register_shouldStorePinHash_whenMasterWithValidPin() throws Exception {
        Role masterRole = Role.builder().id(3L).name(RoleType.ROLE_MASTER).build();
        User savedUser = User.builder().id(5L).username("owner").password("hash").roles(Set.of(masterRole)).build();
        when(userRepo.existsByUsername("owner")).thenReturn(false);
        when(roleRepo.findByName(RoleType.ROLE_MASTER)).thenReturn(Optional.of(masterRole));
        when(companyModeService.encodeCompanyPin("482915")).thenReturn("pinHash");
        when(userRepo.save(any(User.class))).thenReturn(savedUser);

        SignupRequest request = new SignupRequest();
        request.setUsername("owner");
        request.setPassword("password123");
        request.setRole(Set.of(RoleType.ROLE_MASTER));
        request.setCompanyPin("482915");

        mockMvc.perform(post("/api/auth/register")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        verify(userRepo).save(argThat(u -> "pinHash".equals(u.getCompanyPinHash())));
    }

    @Test
    void register_shouldReturn403_whenCompanyMode() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setUsername("newuser");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .with(user(userWith(1L, "MASTER", "COMPANY")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void elevate_shouldReturnElevatedToken_whenPinValid() throws Exception {
        JwtResponse response = JwtResponse.builder()
                .token("elevated-jwt").type("Bearer").id(1L).username("testuser")
                .roles(List.of("ROLE_COMPANY", "ROLE_MASTER", "ROLE_USER")).companyMode(true).build();
        when(companyModeService.elevate("482915", "normal-jwt")).thenReturn(response);

        mockMvc.perform(post("/api/auth/elevate")
                        .with(user(userWith(1L, "MASTER")))
                        .header("Authorization", "Bearer normal-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"482915\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("elevated-jwt"))
                .andExpect(jsonPath("$.companyMode").value(true))
                .andExpect(jsonPath("$.roles").value(contains("ROLE_COMPANY", "ROLE_MASTER", "ROLE_USER")));
    }

    @Test
    void elevate_shouldReturn400_whenPinWrong() throws Exception {
        when(companyModeService.elevate("111222", "normal-jwt"))
                .thenThrow(new ValidationException("Nieprawidłowy PIN. Pozostało prób: 4."));

        mockMvc.perform(post("/api/auth/elevate")
                        .with(user(userWith(1L, "MASTER")))
                        .header("Authorization", "Bearer normal-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"111222\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Nieprawidłowy PIN. Pozostało prób: 4."));
    }

    @Test
    void elevate_shouldReturn429_whenBlocked() throws Exception {
        when(companyModeService.elevate("482915", "normal-jwt"))
                .thenThrow(new TooManyAttemptsException("Zbyt wiele nieudanych prób podania PIN-u. Tryb firmowy jest zablokowany, spróbuj ponownie za 15 min."));

        mockMvc.perform(post("/api/auth/elevate")
                        .with(user(userWith(1L, "MASTER")))
                        .header("Authorization", "Bearer normal-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"482915\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(containsString("spróbuj ponownie za 15 min")));
    }

    @Test
    void elevate_shouldReturn400_whenPinMissing() throws Exception {
        mockMvc.perform(post("/api/auth/elevate")
                        .with(user(userWith(1L, "MASTER")))
                        .header("Authorization", "Bearer normal-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(companyModeService);
    }

    @Test
    void elevate_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/api/auth/elevate")
                        .with(user(userWith(1L, "USER")))
                        .header("Authorization", "Bearer normal-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"482915\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(companyModeService);
    }

    @Test
    void dropElevation_shouldReturnNormalToken() throws Exception {
        JwtResponse response = JwtResponse.builder()
                .token("normal-jwt").type("Bearer").id(1L).username("testuser")
                .roles(List.of("ROLE_MASTER", "ROLE_USER")).companyMode(false).build();
        when(companyModeService.dropElevation("elevated-jwt")).thenReturn(response);

        mockMvc.perform(post("/api/auth/drop-elevation")
                        .with(user(userWith(1L, "MASTER", "COMPANY")))
                        .header("Authorization", "Bearer elevated-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("normal-jwt"))
                .andExpect(jsonPath("$.companyMode").value(false));
    }

    @Test
    void dropElevation_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/api/auth/drop-elevation")
                        .with(user(userWith(1L, "USER")))
                        .header("Authorization", "Bearer some-jwt"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(companyModeService);
    }

    private static UserDetailsImpl masterWithEmployee() {
        return new UserDetailsImpl(7L, "owner", "pass", "avatar3.png", new EmployeeSummaryDTO(4L, "Anna", "Nowak"),
                List.of(new SimpleGrantedAuthority("ROLE_MASTER")));
    }

    @Test
    void me_shouldReturnCompanyMode_whenMasterAuthenticatedWithCompanyAuthority() throws Exception {
        UserDetailsImpl principal = masterWithEmployee();
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_MASTER"), new SimpleGrantedAuthority("ROLE_COMPANY"));

        mockMvc.perform(get("/api/auth/me")
                        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, authorities))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.username").value("owner"))
                .andExpect(jsonPath("$.avatar").value("avatar3.png"))
                .andExpect(jsonPath("$.employee.id").value(4))
                .andExpect(jsonPath("$.employee.name").value("Anna"))
                .andExpect(jsonPath("$.roles").value(contains("ROLE_COMPANY", "ROLE_MASTER", "ROLE_USER")))
                .andExpect(jsonPath("$.companyMode").value(true))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void me_shouldReturnNormalMode_whenMasterWithoutCompanyAuthority() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .with(user(masterWithEmployee())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(contains("ROLE_MASTER", "ROLE_USER")))
                .andExpect(jsonPath("$.companyMode").value(false));
    }

    @Test
    void me_shouldReturnEffectiveRolesWithoutCompanyMode_whenAdmin() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .with(user(userWith(1L, "ADMIN", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.roles").value(contains("ROLE_ADMIN", "ROLE_COMPANY", "ROLE_MASTER", "ROLE_USER")))
                .andExpect(jsonPath("$.companyMode").value(false));
    }

    @Test
    void me_shouldReturnUserRoleOnly_whenPlainUser() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .with(user(userWith(2L, "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").value(contains("ROLE_USER")))
                .andExpect(jsonPath("$.companyMode").value(false))
                .andExpect(jsonPath("$.employee").doesNotExist());
    }

    private Authentication mockAuthentication(UserDetailsImpl principal) {
        Authentication auth = Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        return auth;
    }

    private String loginBody() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("password");
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void login_shouldNotAskForCode_whenMfaDisabled() throws Exception {
        Authentication auth = mockAuthentication(userWith(1L, "ADMIN", "USER"));
        when(jwtUtils.generateJwtToken(auth)).thenReturn("fake-jwt");
        when(mfaLoginService.isMfaEnabled(1L)).thenReturn(false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("fake-jwt"))
                .andExpect(jsonPath("$.mfaRequired").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verifyNoInteractions(trustedDeviceService);
        verify(auditLogService).logSecurity(eq("User-Login"), eq(1L), eq("testuser"), any(), isNull());
    }

    @Test
    void login_shouldReturnMfaChallenge_whenMfaEnabledAndNoRememberedDevice() throws Exception {
        mockAuthentication(userWith(1L, "ADMIN", "USER"));
        when(mfaLoginService.isMfaEnabled(1L)).thenReturn(true);
        when(trustedDeviceService.rotateRemembered(eq(1L), eq("testuser"), isNull(), any(), any())).thenReturn(Optional.empty());
        when(mfaLoginService.startLoginChallenge(1L)).thenReturn("challenge-token");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.mfaToken").value("challenge-token"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verify(loginAttemptService).recordSuccess(any(), eq("testuser"), eq(LoginAttemptScope.IP_AND_USER));
        verify(jwtUtils, never()).generateJwtToken(any(Authentication.class));
        verify(auditLogService, never()).logSecurity(eq("User-Login"), any(), any(), any(), any());
    }

    @Test
    void login_shouldSkipCodeAndRotateCookie_whenRememberedDeviceValid() throws Exception {
        Authentication auth = mockAuthentication(userWith(1L, "ADMIN", "USER"));
        when(jwtUtils.generateJwtToken(auth)).thenReturn("fake-jwt");
        when(mfaLoginService.isMfaEnabled(1L)).thenReturn(true);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 10, 17, 12, 0).plusDays(100);
        when(trustedDeviceService.rotateRemembered(eq(1L), eq("testuser"), eq("old-device-token"), any(), any()))
                .thenReturn(Optional.of(new RememberedDeviceToken("new-device-token", expiresAt)));

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("pc_mfa_1", "old-device-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("fake-jwt"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("pc_mfa_1=new-device-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=8640000")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));

        verify(mfaLoginService, never()).startLoginChallenge(any());
        verify(auditLogService).logSecurity(eq("User-Login"), eq(1L), eq("testuser"), any(), isNull());
    }

    @Test
    void login_shouldClearCookieAndAskForCode_whenRememberedDeviceInvalid() throws Exception {
        mockAuthentication(userWith(1L, "ADMIN", "USER"));
        when(mfaLoginService.isMfaEnabled(1L)).thenReturn(true);
        when(trustedDeviceService.rotateRemembered(eq(1L), eq("testuser"), eq("reused-token"), any(), any()))
                .thenReturn(Optional.empty());
        when(mfaLoginService.startLoginChallenge(1L)).thenReturn("challenge-token");

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("pc_mfa_1", "reused-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("pc_mfa_1=;")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    void verifyMfa_shouldReturnJwt_whenCodeValid() throws Exception {
        UserDetailsImpl principal = userWith(1L, "ADMIN", "USER");
        when(mfaLoginService.verifyLoginCode("challenge-token", "123456")).thenReturn(new MfaLoginResult(1L, "testuser", null));
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(principal);
        when(jwtUtils.generateJwtToken(any(Authentication.class))).thenReturn("mfa-jwt");

        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"challenge-token\",\"code\":\"123456\",\"rememberDevice\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mfa-jwt"))
                .andExpect(jsonPath("$.companyMode").value(false))
                .andExpect(jsonPath("$.remainingBackupCodes").value(nullValue()))
                .andExpect(jsonPath("$.roles").value(contains("ROLE_ADMIN", "ROLE_COMPANY", "ROLE_MASTER", "ROLE_USER")))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verify(trustedDeviceService, never()).rememberDevice(any(), any(), any());
        verify(auditLogService).logSecurity(eq("User-Login"), eq(1L), eq("testuser"), any(), isNull());
    }

    @Test
    void verifyMfa_shouldSetRememberCookie_whenRememberDeviceRequested() throws Exception {
        UserDetailsImpl principal = userWith(1L, "ADMIN", "USER");
        when(mfaLoginService.verifyLoginCode("challenge-token", "123456")).thenReturn(new MfaLoginResult(1L, "testuser", null));
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(principal);
        when(jwtUtils.generateJwtToken(any(Authentication.class))).thenReturn("mfa-jwt");
        when(trustedDeviceService.rememberDevice(eq(1L), anyString(), any()))
                .thenReturn(new RememberedDeviceToken("fresh-token", LocalDateTime.of(2026, 10, 17, 12, 0).plusDays(180)));

        mockMvc.perform(post("/api/auth/mfa/verify")
                        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/130.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"challenge-token\",\"code\":\"123456\",\"rememberDevice\":true}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("pc_mfa_1=fresh-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=15552000")));
    }

    @Test
    void verifyMfaBackup_shouldReturnJwtWithRemainingBackupCodes() throws Exception {
        UserDetailsImpl principal = userWith(1L, "ADMIN", "USER");
        when(mfaLoginService.verifyLoginBackupCode("challenge-token", "abcd-2345")).thenReturn(new MfaLoginResult(1L, "testuser", 9));
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(principal);
        when(jwtUtils.generateJwtToken(any(Authentication.class))).thenReturn("mfa-jwt");

        mockMvc.perform(post("/api/auth/mfa/verify-backup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"challenge-token\",\"code\":\"abcd-2345\",\"rememberDevice\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mfa-jwt"))
                .andExpect(jsonPath("$.remainingBackupCodes").value(9));
    }

    @Test
    void verifyMfa_shouldReturn400_whenMfaTokenExpired() throws Exception {
        when(mfaLoginService.verifyLoginCode("expired-token", "123456"))
                .thenThrow(new MfaSessionExpiredException());

        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"expired-token\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("MFA_SESSION_EXPIRED"))
                .andExpect(jsonPath("$.message").value("Sesja weryfikacji wygasła. Zaloguj się ponownie."));

        verify(jwtUtils, never()).generateJwtToken(any(Authentication.class));
        verifyNoInteractions(auditLogService);
    }

    @Test
    void verifyMfaBackup_shouldReturnCodedError_whenMfaTokenExpired() throws Exception {
        when(mfaLoginService.verifyLoginBackupCode("expired-token", "abcd-2345"))
                .thenThrow(new MfaSessionExpiredException());

        mockMvc.perform(post("/api/auth/mfa/verify-backup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"expired-token\",\"code\":\"abcd-2345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MFA_SESSION_EXPIRED"))
                .andExpect(jsonPath("$.message").value("Sesja weryfikacji wygasła. Zaloguj się ponownie."));
    }

    @Test
    void verifyMfaBackup_shouldReturn400_whenBackupCodeInvalid() throws Exception {
        when(mfaLoginService.verifyLoginBackupCode("challenge-token", "zzzz-zzzz"))
                .thenThrow(new ValidationException("Kod zapasowy jest nieprawidłowy albo został już użyty."));

        mockMvc.perform(post("/api/auth/mfa/verify-backup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"challenge-token\",\"code\":\"zzzz-zzzz\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Kod zapasowy jest nieprawidłowy albo został już użyty."));
    }

    @Test
    void verifyMfa_shouldReturn429_whenVerificationBlocked() throws Exception {
        when(mfaLoginService.verifyLoginCode("challenge-token", "123456"))
                .thenThrow(new TooManyAttemptsException("Zbyt wiele nieudanych prób. Weryfikacja jest zablokowana, spróbuj ponownie za 15 min."));

        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"challenge-token\",\"code\":\"123456\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string(containsString("spróbuj ponownie za 15 min")));
    }

    @Test
    void verifyMfa_shouldReturn400_whenCodeMissing() throws Exception {
        mockMvc.perform(post("/api/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"challenge-token\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(mfaLoginService);
    }

    @Test
    void login_shouldRotateClinicDeviceCookieAndAuditDevice_whenClinicDeviceRecognized() throws Exception {
        Authentication auth = mockAuthentication(userWith(1L, "USER"));
        when(jwtUtils.generateJwtToken(auth)).thenReturn("fake-jwt");
        when(clinicDeviceService.recognizeOnLogin(eq("clinic-token"), eq("testuser"), any(), any()))
                .thenReturn(ClinicDeviceLoginResult.rotated("rotated-clinic-token", "Recepcja"));

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("pc_dev", "clinic-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("fake-jwt"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("pc_dev=rotated-clinic-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=34560000")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));

        verify(auditLogService).logSecurity("User-Login", 1L, "testuser", Map.of("username", "testuser", "device", "Recepcja"), "Recepcja");
    }

    @Test
    void login_shouldNotIssueCookieAndOmitDevice_whenClinicTokenReused() throws Exception {
        Authentication auth = mockAuthentication(userWith(1L, "USER"));
        when(jwtUtils.generateJwtToken(auth)).thenReturn("fake-jwt");
        when(clinicDeviceService.recognizeOnLogin(eq("stale-token"), eq("testuser"), any(), any()))
                .thenReturn(ClinicDeviceLoginResult.reused("Recepcja"));

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("pc_dev", "stale-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("fake-jwt"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verify(auditLogService).logSecurity("User-Login", 1L, "testuser", Map.of("username", "testuser"), "Recepcja");
    }

    @Test
    void login_shouldClearClinicDeviceCookie_whenClinicTokenInvalid() throws Exception {
        Authentication auth = mockAuthentication(userWith(1L, "USER"));
        when(jwtUtils.generateJwtToken(auth)).thenReturn("fake-jwt");
        when(clinicDeviceService.recognizeOnLogin(eq("revoked-token"), eq("testuser"), any(), any()))
                .thenReturn(ClinicDeviceLoginResult.invalid());

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("pc_dev", "revoked-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("pc_dev=;")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
    }

    @Test
    void login_shouldNotRecognizeClinicDevice_whenMfaChallengeReturned() throws Exception {
        mockAuthentication(userWith(1L, "ADMIN", "USER"));
        when(mfaLoginService.isMfaEnabled(1L)).thenReturn(true);
        when(trustedDeviceService.rotateRemembered(eq(1L), eq("testuser"), isNull(), any(), any())).thenReturn(Optional.empty());
        when(mfaLoginService.startLoginChallenge(1L)).thenReturn("challenge-token");

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("pc_dev", "clinic-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true));

        verify(clinicDeviceService, never()).recognizeOnLogin(any(), any(), any(), any());
    }

    @Test
    void verifyMfa_shouldRotateClinicDeviceCookie_whenClinicDeviceRecognized() throws Exception {
        UserDetailsImpl principal = userWith(1L, "ADMIN", "USER");
        when(mfaLoginService.verifyLoginCode("challenge-token", "123456")).thenReturn(new MfaLoginResult(1L, "testuser", null));
        when(userDetailsService.loadUserByUsername("testuser")).thenReturn(principal);
        when(jwtUtils.generateJwtToken(any(Authentication.class))).thenReturn("mfa-jwt");
        when(clinicDeviceService.recognizeOnLogin(eq("clinic-token"), eq("testuser"), any(), any()))
                .thenReturn(ClinicDeviceLoginResult.rotated("rotated-clinic-token", "Gabinet 1"));

        mockMvc.perform(post("/api/auth/mfa/verify")
                        .cookie(new Cookie("pc_dev", "clinic-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mfaToken\":\"challenge-token\",\"code\":\"123456\",\"rememberDevice\":false}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("pc_dev=rotated-clinic-token")));

        verify(auditLogService).logSecurity("User-Login", 1L, "testuser", Map.of("username", "testuser", "device", "Gabinet 1"), "Gabinet 1");
    }

    @Test
    void registerDevice_shouldSetDeviceCookieAndReturnName_whenCodeValid() throws Exception {
        when(clinicDeviceService.register(eq("abc-234"), isNull(), anyString(), any()))
                .thenReturn(new ClinicDeviceRegistration("new-clinic-token", "Recepcja"));

        mockMvc.perform(post("/api/auth/device/register")
                        .header(HttpHeaders.USER_AGENT, "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) Safari/604.1")
                        .header("X-Real-IP", "10.0.0.9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"abc-234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Urządzenie zarejestrowane. Możesz się zalogować."))
                .andExpect(jsonPath("$.deviceName").value("Recepcja"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("pc_dev=new-clinic-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=34560000")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));

        verify(clinicDeviceService).register(eq("abc-234"), isNull(), anyString(), eq("10.0.0.9"));
    }

    @Test
    void registerDevice_shouldPassExistingCookieAndReturn409_whenAlreadyRegistered() throws Exception {
        when(clinicDeviceService.register(eq("ABC234"), eq("existing-token"), any(), any()))
                .thenThrow(new ConflictException("To urządzenie jest już zarejestrowane."));

        mockMvc.perform(post("/api/auth/device/register")
                        .cookie(new Cookie("pc_dev", "existing-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ABC234\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().string("To urządzenie jest już zarejestrowane."))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void registerDevice_shouldReturn400_whenCodeInvalid() throws Exception {
        when(clinicDeviceService.register(any(), any(), any(), any()))
                .thenThrow(new ValidationException("Kod jest nieprawidłowy albo wygasł."));

        mockMvc.perform(post("/api/auth/device/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ZZZ999\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Kod jest nieprawidłowy albo wygasł."));
    }

    @Test
    void registerDevice_shouldReturn429_whenIpBlocked() throws Exception {
        when(clinicDeviceService.register(any(), any(), any(), any()))
                .thenThrow(new TooManyAttemptsException("Zbyt wiele nieudanych prób. Spróbuj ponownie za 15 min."));

        mockMvc.perform(post("/api/auth/device/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ZZZ999\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string("Zbyt wiele nieudanych prób. Spróbuj ponownie za 15 min."));
    }

    private void gateOnWith(LoginDeviceCheck check) {
        when(accessControlService.isGateActive()).thenReturn(true);
        when(clinicDeviceService.inspectForLogin(any(), any(), any())).thenReturn(check);
    }

    private static LoginDeviceCheck allowedDevice() {
        return new LoginDeviceCheck(LoginDeviceCheck.Status.ALLOWED, 5L, "Recepcja", false);
    }

    @Test
    void login_shouldUseIpAndUserScopeAndSkipHours_whenGateInactiveAndNoDevice() throws Exception {
        Authentication auth = mockAuthentication(userWith(1L, "USER"));
        when(jwtUtils.generateJwtToken(auth)).thenReturn("fake-jwt");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody()))
                .andExpect(status().isOk());

        verify(loginAttemptService).isShadowBanned(any(), eq("testuser"), eq(LoginAttemptScope.IP_AND_USER));
        verify(loginAttemptService).isBlocked(any(), eq("testuser"), eq(LoginAttemptScope.IP_AND_USER));
        verify(accessControlService, never()).isWithinWorkingHours();
    }

    @Test
    void login_shouldIgnoreIpBlock_fromRegisteredClinicDevice() throws Exception {
        when(clinicDeviceService.inspectForLogin(eq("clinic-token"), any(), any())).thenReturn(
                new LoginDeviceCheck(LoginDeviceCheck.Status.ALLOWED, 5L, "Recepcja", false));
        when(loginAttemptService.isBlocked(any(), any(), eq(LoginAttemptScope.IP_AND_USER))).thenReturn(true);
        when(loginAttemptService.isShadowBanned(any(), any(), eq(LoginAttemptScope.IP_AND_USER))).thenReturn(true);
        Authentication auth = mockAuthentication(userWith(1L, "USER"));
        when(jwtUtils.generateJwtToken(auth)).thenReturn("fake-jwt");

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("pc_dev", "clinic-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("fake-jwt"));

        verify(loginAttemptService).isBlocked(any(), eq("testuser"), eq(LoginAttemptScope.USER_ONLY));
        verify(loginAttemptService, never()).isBlocked(any(), any(), eq(LoginAttemptScope.IP_AND_USER));
    }

    @Test
    void login_shouldReturn429_whenUsernameBlockedOnRegisteredClinicDevice() throws Exception {
        when(clinicDeviceService.inspectForLogin(eq("clinic-token"), any(), any())).thenReturn(
                new LoginDeviceCheck(LoginDeviceCheck.Status.ALLOWED, 5L, "Recepcja", false));
        when(loginAttemptService.isBlocked(any(), eq("testuser"), eq(LoginAttemptScope.USER_ONLY))).thenReturn(true);

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("pc_dev", "clinic-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isTooManyRequests());

        verifyNoInteractions(authenticationManager);
    }

    @Test
    void login_shouldReturn401_whenUsernameShadowBannedOnRegisteredClinicDeviceWithGateActive() throws Exception {
        when(accessControlService.isGateActive()).thenReturn(true);
        when(clinicDeviceService.inspectForLogin(eq("clinic-token"), any(), any())).thenReturn(
                new LoginDeviceCheck(LoginDeviceCheck.Status.ALLOWED, 5L, "Recepcja", false));
        when(loginAttemptService.isShadowBanned(any(), eq("testuser"), eq(LoginAttemptScope.USER_ONLY))).thenReturn(true);

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("pc_dev", "clinic-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_shouldCountOnlyUsername_whenPasswordWrongOnRegisteredClinicDevice() throws Exception {
        when(clinicDeviceService.inspectForLogin(eq("clinic-token"), any(), any())).thenReturn(
                new LoginDeviceCheck(LoginDeviceCheck.Status.ALLOWED, 5L, "Recepcja", false));
        when(authenticationManager.authenticate(any())).thenThrow(new RuntimeException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("pc_dev", "clinic-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isUnauthorized());

        verify(loginAttemptService).recordFailure(any(), eq("testuser"), eq(LoginAttemptScope.USER_ONLY));
    }

    @Test
    void login_shouldCountIpAndUsername_whenPasswordWrongOutsideClinicDevice() throws Exception {
        when(authenticationManager.authenticate(any())).thenThrow(new RuntimeException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody()))
                .andExpect(status().isUnauthorized());

        verify(loginAttemptService).recordFailure(any(), eq("testuser"), eq(LoginAttemptScope.IP_AND_USER));
    }

    @Test
    void login_shouldTreatSuspectedDeviceAsOutside_forLimiterScope() throws Exception {
        when(clinicDeviceService.inspectForLogin(any(), any(), any())).thenReturn(
                new LoginDeviceCheck(LoginDeviceCheck.Status.SUSPECTED_COPY, 5L, "Recepcja", false));
        when(loginAttemptService.isBlocked(any(), any(), eq(LoginAttemptScope.IP_AND_USER))).thenReturn(true);

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("pc_dev", "clinic-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void login_shouldPassAndRotate_whenGateActiveDeviceAllowedUserWithinHours() throws Exception {
        gateOnWith(allowedDevice());
        when(accessControlService.isWithinWorkingHours()).thenReturn(true);
        Authentication auth = mockAuthentication(userWith(1L, "USER"));
        when(jwtUtils.generateJwtToken(auth)).thenReturn("fake-jwt");
        when(clinicDeviceService.recognizeOnLogin(eq("clinic-token"), eq("testuser"), any(), any()))
                .thenReturn(ClinicDeviceLoginResult.rotated("rotated-token", "Recepcja"));

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("pc_dev", "clinic-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("fake-jwt"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("pc_dev=rotated-token")));

        verify(clinicDeviceService).inspectForLogin(eq("clinic-token"), any(), any());
    }

    @Test
    void login_shouldReturn403WithoutRotation_whenUserOutsideWorkingHours() throws Exception {
        gateOnWith(allowedDevice());
        when(accessControlService.isWithinWorkingHours()).thenReturn(false);
        mockAuthentication(userWith(1L, "USER"));

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("pc_dev", "clinic-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("Brak dostępu"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verify(clinicDeviceService, never()).recognizeOnLogin(any(), any(), any(), any());
        verify(jwtUtils, never()).generateJwtToken(any(Authentication.class));
        verify(auditLogService).logSecurity(eq("Security-Hours-Denied"), eq(1L), eq("testuser"),
                argThat(details -> details instanceof Map<?, ?> map && "Recepcja".equals(map.get("device"))), eq("Recepcja"));
    }

    @Test
    void login_shouldIgnoreWorkingHours_forAdmin() throws Exception {
        gateOnWith(allowedDevice());
        Authentication auth = mockAuthentication(userWith(1L, "ADMIN"));
        when(jwtUtils.generateJwtToken(auth)).thenReturn("fake-jwt");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody()))
                .andExpect(status().isOk());

        verify(accessControlService, never()).isWithinWorkingHours();
    }

    @Test
    void login_shouldReturn403AndAudit_whenUserWithCorrectPasswordHasNoAllowedDevice() throws Exception {
        gateOnWith(LoginDeviceCheck.unknown());
        mockAuthentication(userWith(1L, "USER"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.message").value("Brak dostępu"));

        verify(loginAttemptService).recordSuccess(any(), eq("testuser"), eq(LoginAttemptScope.IP_AND_USER));
        verify(jwtUtils, never()).generateJwtToken(any(Authentication.class));
        verify(auditLogService).logSecurity(eq("Security-Device-Denied"), eq(1L), eq("testuser"),
                argThat(details -> details instanceof Map<?, ?> map && "UNKNOWN_DEVICE".equals(map.get("reason"))), isNull());
    }

    @Test
    void login_shouldReturnSame403AndCountFailure_whenPasswordWrongWithoutAllowedDevice() throws Exception {
        gateOnWith(LoginDeviceCheck.unknown());
        when(authenticationManager.authenticate(any())).thenThrow(new RuntimeException("Bad credentials"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(loginAttemptService).recordFailure(any(), eq("testuser"), any());
        verify(auditLogService, never()).logSecurity(eq("Security-Device-Denied"), any(), any(), any(), any());
    }

    @Test
    void login_shouldReturn403InsteadOf429AndStillAuthenticate_whenBlockedWithoutAllowedDevice() throws Exception {
        gateOnWith(LoginDeviceCheck.unknown());
        when(loginAttemptService.isBlocked(any(), any(), any())).thenReturn(true);
        mockAuthentication(userWith(1L, "ADMIN"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(authenticationManager).authenticate(any());
        verify(loginAttemptService, never()).recordSuccess(any(), any(), any());
        verify(loginAttemptService, never()).recordFailure(any(), any(), any());
    }

    @Test
    void login_shouldReturn403_whenShadowBannedWithoutAllowedDevice() throws Exception {
        gateOnWith(LoginDeviceCheck.unknown());
        when(loginAttemptService.isShadowBanned(any(), any(), any())).thenReturn(true);
        mockAuthentication(userWith(1L, "USER"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void login_shouldLetAdminThrough_whenNoAllowedDevice() throws Exception {
        gateOnWith(LoginDeviceCheck.unknown());
        Authentication auth = mockAuthentication(userWith(1L, "ADMIN"));
        when(jwtUtils.generateJwtToken(auth)).thenReturn("fake-jwt");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("fake-jwt"));

        verify(auditLogService, never()).logSecurity(eq("Security-Device-Denied"), any(), any(), any(), any());
    }

    @Test
    void login_shouldStillRequireMfaForMaster_whenNoAllowedDevice() throws Exception {
        gateOnWith(LoginDeviceCheck.unknown());
        mockAuthentication(userWith(1L, "MASTER"));
        when(mfaLoginService.isMfaEnabled(1L)).thenReturn(true);
        when(trustedDeviceService.rotateRemembered(eq(1L), eq("testuser"), isNull(), any(), any())).thenReturn(Optional.empty());
        when(mfaLoginService.startLoginChallenge(1L)).thenReturn("challenge-token");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true));
    }

    @Test
    void login_shouldFlagReuseAndDeny_whenUserSendsCopiedPreviousToken() throws Exception {
        gateOnWith(new LoginDeviceCheck(LoginDeviceCheck.Status.SUSPECTED_COPY, 5L, "Recepcja", true));
        mockAuthentication(userWith(1L, "USER"));

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("pc_dev", "copied-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(clinicDeviceService).recordLoginReuse(eq(5L), eq("testuser"), any());
        verify(auditLogService).logSecurity(eq("Security-Device-Denied"), eq(1L), eq("testuser"),
                argThat(details -> details instanceof Map<?, ?> map && "SUSPECTED_COPY".equals(map.get("reason"))), eq("Recepcja"));
    }

    @Test
    void login_shouldDenyRevokedDevice_forUser() throws Exception {
        gateOnWith(new LoginDeviceCheck(LoginDeviceCheck.Status.REVOKED, 5L, "Recepcja", false));
        mockAuthentication(userWith(1L, "USER"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody()))
                .andExpect(status().isForbidden());

        verify(clinicDeviceService, never()).recordLoginReuse(any(), any(), any());
        verify(auditLogService).logSecurity(eq("Security-Device-Denied"), eq(1L), eq("testuser"),
                argThat(details -> details instanceof Map<?, ?> map && "REVOKED".equals(map.get("reason"))), eq("Recepcja"));
    }

    @Test
    void login_shouldClearClinicCounters_whenSuccessfulOnRegisteredClinicDevice() throws Exception {
        when(clinicDeviceService.inspectForLogin(eq("clinic-token"), any(), any())).thenReturn(
                new LoginDeviceCheck(LoginDeviceCheck.Status.ALLOWED, 5L, "Recepcja", false));
        Authentication auth = mockAuthentication(userWith(1L, "USER"));
        when(jwtUtils.generateJwtToken(auth)).thenReturn("fake-jwt");

        mockMvc.perform(post("/api/auth/login")
                        .cookie(new Cookie("pc_dev", "clinic-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody()))
                .andExpect(status().isOk());

        verify(loginAttemptService).recordSuccess(any(), eq("testuser"), eq(LoginAttemptScope.USER_ONLY));
    }

    @Test
    void login_shouldIgnoreClinicBlock_whenLoggingInOutsideClinicDevice() throws Exception {
        when(loginAttemptService.isBlocked(any(), any(), eq(LoginAttemptScope.USER_ONLY))).thenReturn(true);
        when(loginAttemptService.isShadowBanned(any(), any(), eq(LoginAttemptScope.USER_ONLY))).thenReturn(true);
        Authentication auth = mockAuthentication(userWith(1L, "USER"));
        when(jwtUtils.generateJwtToken(auth)).thenReturn("fake-jwt");

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody()))
                .andExpect(status().isOk());

        verify(loginAttemptService, never()).isBlocked(any(), any(), eq(LoginAttemptScope.USER_ONLY));
    }

    @Test
    void register_shouldAssignFirstFreeAvatar() throws Exception {
        Role userRole = Role.builder().id(1L).name(RoleType.ROLE_USER).build();
        User savedUser = User.builder().id(1L).username("newuser").password("hash").avatar("avatar3.png").roles(Set.of(userRole)).build();
        when(userRepo.existsByUsername("newuser")).thenReturn(false);
        when(roleRepo.findByName(RoleType.ROLE_USER)).thenReturn(Optional.of(userRole));
        when(avatarService.findFirstFreeAvatar()).thenReturn("avatar3.png");
        when(userRepo.save(any(User.class))).thenReturn(savedUser);

        SignupRequest request = new SignupRequest();
        request.setUsername("newuser");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        verify(userRepo).save(argThat(u -> "avatar3.png".equals(u.getAvatar())));
    }

    @Test
    void register_shouldSucceedWithNullAvatar_whenAllAvatarsTaken() throws Exception {
        Role userRole = Role.builder().id(1L).name(RoleType.ROLE_USER).build();
        User savedUser = User.builder().id(1L).username("newuser").password("hash").roles(Set.of(userRole)).build();
        when(userRepo.existsByUsername("newuser")).thenReturn(false);
        when(roleRepo.findByName(RoleType.ROLE_USER)).thenReturn(Optional.of(userRole));
        when(avatarService.findFirstFreeAvatar()).thenReturn(null);
        when(userRepo.save(any(User.class))).thenReturn(savedUser);

        SignupRequest request = new SignupRequest();
        request.setUsername("newuser");
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/register")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User registered successfully!"));
        verify(userRepo).save(argThat(u -> u.getAvatar() == null && "newuser".equals(u.getUsername())));
    }
}

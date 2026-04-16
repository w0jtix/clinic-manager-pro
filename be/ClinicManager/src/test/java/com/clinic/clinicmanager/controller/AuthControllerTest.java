package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.request.auth.ChangePasswordRequest;
import com.clinic.clinicmanager.DTO.request.auth.ForceChangePasswordRequest;
import com.clinic.clinicmanager.DTO.request.auth.LoginRequest;
import com.clinic.clinicmanager.DTO.request.auth.SignupRequest;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.model.Role;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.repo.RoleRepo;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.EmployeeService;
import com.clinic.clinicmanager.service.LoginAttemptService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(WebSecurityConfig.class)
class AuthControllerTest {

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
        when(loginAttemptService.isBlocked(any(), any())).thenReturn(true);

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
        when(loginAttemptService.isShadowBanned(any(), any())).thenReturn(true);

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
}

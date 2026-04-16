package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.request.auth.ChangePasswordRequest;
import com.clinic.clinicmanager.DTO.request.auth.ForceChangePasswordRequest;
import com.clinic.clinicmanager.DTO.request.auth.LoginRequest;
import com.clinic.clinicmanager.DTO.request.auth.SignupRequest;
import com.clinic.clinicmanager.DTO.response.JwtResponse;
import com.clinic.clinicmanager.DTO.response.MessageResponse;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Role;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.repo.RoleRepo;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.EmployeeService;
import com.clinic.clinicmanager.service.LoginAttemptService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import com.clinic.clinicmanager.utils.SessionUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final EmployeeService employeeService;
    private final AuditLogService auditLogService;
    private final TokenBlacklistService tokenBlacklistService;
    private final LoginAttemptService loginAttemptService;

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        String ip = getClientIp(request);
        String username = loginRequest.getUsername();

        if (loginAttemptService.isShadowBanned(ip, username)) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        if (loginAttemptService.isBlocked(ip, username)) {
            return new ResponseEntity<>(HttpStatus.TOO_MANY_REQUESTS);
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, loginRequest.getPassword()));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateJwtToken(authentication);

            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            List<String> roles = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());

            loginAttemptService.recordSuccess(ip, username);
            auditLogService.logCreate("User-Login", userDetails.getId(), userDetails.getUsername(), Map.of("username", userDetails.getUsername()));

            return new ResponseEntity<>(JwtResponse.builder()
                    .token(jwt)
                    .type(JwtUtils.JWT_TYPE)
                    .id(userDetails.getId())
                    .username(userDetails.getUsername())
                    .avatar(userDetails.getAvatar())
                    .roles(roles)
                    .employee(userDetails.getEmployee())
                    .build(), HttpStatus.OK);
        } catch (Exception e) {
            loginAttemptService.recordFailure(ip, username);
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp;
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> createUser(@Valid @RequestBody SignupRequest signupRequest) {
        if(userRepo.existsByUsername(signupRequest.getUsername())) {
            throw new CreationException("Username already exists: " + signupRequest.getUsername());
        }

        Set<Role> roles = new HashSet<>();

        if(signupRequest.getRole() == null || signupRequest.getRole().isEmpty()) {
            Role userRole = roleRepo.findByName(RoleType.ROLE_USER)
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found."));
            roles.add(userRole);
        } else {
            roles = signupRequest.getRole().stream()
                    .map(role ->
                            roleRepo.findByName(role)
                                    .orElseThrow(() -> new ResourceNotFoundException("Role not found.")))
                    .collect(Collectors.toSet());
        }
        User savedUser = userRepo.save(User.builder()
                .username(signupRequest.getUsername())
                .password(encoder.encode((signupRequest.getPassword())))
                .roles(roles)
                .build());

        auditLogService.logCreate("User", savedUser.getId(), savedUser.getUsername(), Map.of("username", savedUser.getUsername()));

        return new ResponseEntity<>(new MessageResponse("User registered successfully!"), HttpStatus.OK);
    }

    @PostMapping("/change-password")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<MessageResponse> changePassword(@Valid @RequestBody ChangePasswordRequest changePasswordRequest) {
        User loggedUser = userRepo.findOneById(SessionUtils.getUserIdFromSession())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with given id: " + SessionUtils.getUserIdFromSession()));
        if(encoder.matches(changePasswordRequest.getOldPassword(), loggedUser.getPassword())) {
            loggedUser.setPassword(encoder.encode(changePasswordRequest.getNewPassword()));
            userRepo.save(loggedUser);
            auditLogService.logUpdate("User-Password", loggedUser.getId(), loggedUser.getUsername(),
                    Map.of("action", ""),
                    Map.of("action", "Wymuszono zmianę hasła dla Użytkownika: " + loggedUser.getUsername()));
            return new ResponseEntity<>(new MessageResponse("Password changed successfully"), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(new MessageResponse("Old password doesn't match"),HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/logout")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MessageResponse> logout(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            String token = headerAuth.substring(7);
            long remainingMs = jwtUtils.getRemainingExpirationMs(token);
            if (remainingMs > 0) {
                tokenBlacklistService.blacklist(token, remainingMs);
            }
        }
        return new ResponseEntity<>(new MessageResponse("Logged out successfully"), HttpStatus.OK);
    }

    @PostMapping("/force-change-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> forceChangePassword(@Valid @RequestBody ForceChangePasswordRequest forceChangePasswordRequest) {
        User user = userRepo.findOneById(forceChangePasswordRequest.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with given id: " + forceChangePasswordRequest.getUserId()));

        user.setPassword(encoder.encode(forceChangePasswordRequest.getNewPassword()));
        userRepo.save(user);
        auditLogService.logUpdate("User-Password", user.getId(), user.getUsername(),
                Map.of("action", ""),
                Map.of("action", "Wymuszono zmianę hasła dla Użytkownika: " + user.getUsername()));

        return new ResponseEntity<>(new MessageResponse("Password changed successfully"), HttpStatus.OK);
    }
}

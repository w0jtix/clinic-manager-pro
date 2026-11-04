package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.ClinicDeviceLoginResult;
import com.clinic.clinicmanager.DTO.ClinicDeviceRegistration;
import com.clinic.clinicmanager.DTO.LoginDeviceCheck;
import com.clinic.clinicmanager.DTO.MfaLoginResult;
import com.clinic.clinicmanager.DTO.RememberedDeviceToken;
import com.clinic.clinicmanager.DTO.request.auth.ChangePasswordRequest;
import com.clinic.clinicmanager.DTO.request.auth.DeviceRegisterRequest;
import com.clinic.clinicmanager.DTO.request.auth.ElevateRequest;
import com.clinic.clinicmanager.DTO.request.auth.ForceChangePasswordRequest;
import com.clinic.clinicmanager.DTO.request.auth.LoginRequest;
import com.clinic.clinicmanager.DTO.request.auth.MfaVerifyRequest;
import com.clinic.clinicmanager.DTO.request.auth.SignupRequest;
import com.clinic.clinicmanager.DTO.response.CurrentUserResponse;
import com.clinic.clinicmanager.DTO.response.DeviceRegistrationResponse;
import com.clinic.clinicmanager.DTO.response.JwtResponse;
import com.clinic.clinicmanager.DTO.response.MessageResponse;
import com.clinic.clinicmanager.DTO.response.MfaChallengeResponse;
import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.config.security.ClinicDeviceCookie;
import com.clinic.clinicmanager.config.security.MfaDeviceCookie;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.AccessGateDeniedException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
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
import com.clinic.clinicmanager.utils.RoleAssignmentRules;
import com.clinic.clinicmanager.utils.SessionUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
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
    private final CompanyModeService companyModeService;
    private final MfaLoginService mfaLoginService;
    private final TrustedDeviceService trustedDeviceService;
    private final MfaDeviceCookie mfaDeviceCookie;
    private final UserDetailsServiceImpl userDetailsService;
    private final ClinicDeviceService clinicDeviceService;
    private final ClinicDeviceCookie clinicDeviceCookie;
    private final AccessControlService accessControlService;
    private final AvatarService avatarService;

    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
        String ip = getClientIp(request);
        String username = loginRequest.getUsername();
        LoginDeviceCheck deviceCheck = clinicDeviceService.inspectForLogin(
                clinicDeviceCookie.read(request), ip, request.getHeader(HttpHeaders.USER_AGENT));
        boolean gateActive = accessControlService.isGateActive();

        if (gateActive && !deviceCheck.allowed()) {
            return loginWithoutAllowedDevice(loginRequest, deviceCheck, ip, request, response);
        }

        LoginAttemptScope scope = deviceCheck.allowed() ? LoginAttemptScope.USER_ONLY : LoginAttemptScope.IP_AND_USER;
        if (loginAttemptService.isShadowBanned(ip, username, scope)) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }

        if (loginAttemptService.isBlocked(ip, username, scope)) {
            return new ResponseEntity<>(HttpStatus.TOO_MANY_REQUESTS);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, loginRequest.getPassword()));
        } catch (Exception e) {
            loginAttemptService.recordFailure(ip, username, scope);
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        loginAttemptService.recordSuccess(ip, username, scope);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        if (gateActive && AppRoleHierarchy.isGatedAccount(userDetails.getAuthorities())
                && !accessControlService.isWithinWorkingHours()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("username", userDetails.getUsername());
            details.put("ip", ip);
            details.put("device", deviceCheck.deviceName());
            auditLogService.logSecurity("Security-Hours-Denied", userDetails.getId(), userDetails.getUsername(), details,
                    deviceCheck.deviceName());
            throw new AccessGateDeniedException(AccessGateDeniedException.ACCESS_DENIED);
        }

        return continueLogin(authentication, ip, request, response);
    }

    private ResponseEntity<?> loginWithoutAllowedDevice(LoginRequest loginRequest, LoginDeviceCheck deviceCheck, String ip,
                                                        HttpServletRequest request, HttpServletResponse response) {
        String username = loginRequest.getUsername();
        boolean limited = loginAttemptService.isShadowBanned(ip, username, LoginAttemptScope.IP_AND_USER)
                || loginAttemptService.isBlocked(ip, username, LoginAttemptScope.IP_AND_USER);

        Authentication authentication = null;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, loginRequest.getPassword()));
        } catch (Exception e) {
            if (!limited) {
                loginAttemptService.recordFailure(ip, username, LoginAttemptScope.IP_AND_USER);
            }
        }
        if (limited || authentication == null) {
            throw new AccessGateDeniedException(AccessGateDeniedException.ACCESS_DENIED);
        }
        loginAttemptService.recordSuccess(ip, username, LoginAttemptScope.IP_AND_USER);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        if (AppRoleHierarchy.isGatedAccount(userDetails.getAuthorities())) {
            if (deviceCheck.previousTokenReuse()) {
                clinicDeviceService.recordLoginReuse(deviceCheck.deviceId(), userDetails.getUsername(), ip);
            }
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("username", userDetails.getUsername());
            details.put("ip", ip);
            details.put("reason", deviceCheck.status().name());
            auditLogService.logSecurity("Security-Device-Denied", userDetails.getId(), userDetails.getUsername(), details,
                    deviceCheck.deviceName());
            throw new AccessGateDeniedException(AccessGateDeniedException.ACCESS_DENIED);
        }

        return continueLogin(authentication, ip, request, response);
    }

    private ResponseEntity<?> continueLogin(Authentication authentication, String ip,
                                            HttpServletRequest request, HttpServletResponse response) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        if (mfaLoginService.isMfaEnabled(userDetails.getId())) {
            String deviceToken = mfaDeviceCookie.read(request, userDetails.getId());
            Optional<RememberedDeviceToken> rotated = trustedDeviceService.rotateRemembered(
                    userDetails.getId(), userDetails.getUsername(), deviceToken, request.getHeader(HttpHeaders.USER_AGENT), ip);
            if (rotated.isEmpty()) {
                if (deviceToken != null) {
                    mfaDeviceCookie.clear(response, userDetails.getId());
                }
                return new ResponseEntity<>(new MfaChallengeResponse(true, mfaLoginService.startLoginChallenge(userDetails.getId())), HttpStatus.OK);
            }
            mfaDeviceCookie.write(response, userDetails.getId(), rotated.get());
        }

        return new ResponseEntity<>(completeLogin(authentication, null, request, response), HttpStatus.OK);
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<JwtResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest verifyRequest, HttpServletRequest request, HttpServletResponse response) {
        MfaLoginResult result = mfaLoginService.verifyLoginCode(verifyRequest.getMfaToken(), verifyRequest.getCode());
        return new ResponseEntity<>(completeMfaLogin(result, verifyRequest.isRememberDevice(), request, response), HttpStatus.OK);
    }

    @PostMapping("/mfa/verify-backup")
    public ResponseEntity<JwtResponse> verifyMfaBackup(@Valid @RequestBody MfaVerifyRequest verifyRequest, HttpServletRequest request, HttpServletResponse response) {
        MfaLoginResult result = mfaLoginService.verifyLoginBackupCode(verifyRequest.getMfaToken(), verifyRequest.getCode());
        return new ResponseEntity<>(completeMfaLogin(result, verifyRequest.isRememberDevice(), request, response), HttpStatus.OK);
    }

    private JwtResponse completeMfaLogin(MfaLoginResult result, boolean rememberDevice, HttpServletRequest request, HttpServletResponse response) {
        UserDetailsImpl userDetails = (UserDetailsImpl) userDetailsService.loadUserByUsername(result.username());
        if (rememberDevice) {
            RememberedDeviceToken token = trustedDeviceService.rememberDevice(
                    userDetails.getId(), request.getHeader(HttpHeaders.USER_AGENT), getClientIp(request));
            mfaDeviceCookie.write(response, userDetails.getId(), token);
        }
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        return completeLogin(authentication, result.remainingBackupCodes(), request, response);
    }

    private JwtResponse completeLogin(Authentication authentication, Integer remainingBackupCodes,
                                      HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        List<String> roles = AppRoleHierarchy.effectiveRoles(userDetails.getAuthorities());
        ClinicDeviceLoginResult clinicDevice = recognizeClinicDevice(userDetails.getUsername(), request, response);
        Map<String, Object> loginDetails = new LinkedHashMap<>();
        loginDetails.put("username", userDetails.getUsername());
        if (clinicDevice.outcome() == ClinicDeviceLoginResult.Outcome.ROTATED) {
            loginDetails.put("device", clinicDevice.deviceName());
        }
        auditLogService.logSecurity("User-Login", userDetails.getId(), userDetails.getUsername(), loginDetails,
                clinicDevice.deviceName());

        JwtResponse jwtResponse = JwtResponse.of(jwt, userDetails, roles, false);
        jwtResponse.setRemainingBackupCodes(remainingBackupCodes);
        return jwtResponse;
    }

    private ClinicDeviceLoginResult recognizeClinicDevice(String username, HttpServletRequest request, HttpServletResponse response) {
        ClinicDeviceLoginResult result = clinicDeviceService.recognizeOnLogin(
                clinicDeviceCookie.read(request), username, getClientIp(request), request.getHeader(HttpHeaders.USER_AGENT));
        if (result.outcome() == ClinicDeviceLoginResult.Outcome.ROTATED) {
            clinicDeviceCookie.write(response, result.token());
        }
        if (result.outcome() == ClinicDeviceLoginResult.Outcome.INVALID) {
            clinicDeviceCookie.clear(response);
        }
        return result;
    }

    @PostMapping("/device/register")
    public ResponseEntity<DeviceRegistrationResponse> registerDevice(@RequestBody DeviceRegisterRequest registerRequest,
                                                                     HttpServletRequest request, HttpServletResponse response) {
        ClinicDeviceRegistration registration = clinicDeviceService.register(registerRequest.getCode(),
                clinicDeviceCookie.read(request), request.getHeader(HttpHeaders.USER_AGENT), getClientIp(request));
        if (clinicDeviceCookie.isSecure() && !isHttps(request)) {
            log.warn("Clinic device cookie is Secure but the request is plain HTTP; the browser will drop it. "
                    + "Set DEVICE_COOKIE_SECURE=false for HTTP-only environments.");
        }
        clinicDeviceCookie.write(response, registration.token());
        return new ResponseEntity<>(new DeviceRegistrationResponse("Urządzenie zarejestrowane. Możesz się zalogować.",
                registration.deviceName()), HttpStatus.OK);
    }

    private static boolean isHttps(HttpServletRequest request) {
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
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

        if (signupRequest.getRole() != null && signupRequest.getRole().contains(RoleType.ROLE_ADMIN)) {
            throw new ValidationException(RoleAssignmentRules.ADMIN_GRANT_FORBIDDEN);
        }

        Set<Role> roles = new HashSet<>();

        if(signupRequest.getRole() == null || signupRequest.getRole().isEmpty()) {
            Role userRole = roleRepo.findByName(RoleType.ROLE_USER)
                    .orElseThrow(() -> new ResourceNotFoundException("Role not found."));
            roles.add(userRole);
        } else {
            roles = RoleAssignmentRules.singleBusinessRole(signupRequest.getRole().stream()
                    .map(role ->
                            roleRepo.findByName(role)
                                    .orElseThrow(() -> new ResourceNotFoundException("Role not found.")))
                    .collect(Collectors.toSet()));
        }
        String companyPinHash = null;
        boolean master = roles.stream().anyMatch(role -> role.getName() == RoleType.ROLE_MASTER);
        if (master) {
            if (!StringUtils.hasText(signupRequest.getCompanyPin())) {
                throw new ValidationException("PIN trybu firmowego jest wymagany dla roli MASTER.");
            }
            companyPinHash = companyModeService.encodeCompanyPin(signupRequest.getCompanyPin());
        }

        User savedUser = userRepo.save(User.builder()
                .username(signupRequest.getUsername())
                .password(encoder.encode((signupRequest.getPassword())))
                .avatar(avatarService.findFirstFreeAvatar())
                .roles(roles)
                .companyPinHash(companyPinHash)
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
            trustedDeviceService.revokeAllRemembered(loggedUser.getId());
            auditLogService.logSecurity("User-Password", loggedUser.getId(), loggedUser.getUsername(),
                    Map.of("username", loggedUser.getUsername(), "action", "Zmieniono własne hasło"));
            return new ResponseEntity<>(new MessageResponse("Password changed successfully"), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(new MessageResponse("Old password doesn't match"),HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/logout")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<MessageResponse> logout(HttpServletRequest request) {
        String token = parseBearerToken(request);
        if (token != null) {
            long remainingMs = jwtUtils.getRemainingExpirationMs(token);
            if (remainingMs > 0) {
                tokenBlacklistService.blacklist(token, remainingMs);
            }
        }
        return new ResponseEntity<>(new MessageResponse("Logged out successfully"), HttpStatus.OK);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CurrentUserResponse> getCurrentUser() {
        return new ResponseEntity<>(CurrentUserResponse.of(SecurityContextHolder.getContext().getAuthentication()), HttpStatus.OK);
    }

    @PostMapping("/elevate")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<JwtResponse> elevate(@Valid @RequestBody ElevateRequest elevateRequest, HttpServletRequest request) {
        return new ResponseEntity<>(companyModeService.elevate(elevateRequest.getPin(), parseBearerToken(request)), HttpStatus.OK);
    }

    @PostMapping("/drop-elevation")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<JwtResponse> dropElevation(HttpServletRequest request) {
        return new ResponseEntity<>(companyModeService.dropElevation(parseBearerToken(request)), HttpStatus.OK);
    }

    private String parseBearerToken(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }

    @PostMapping("/force-change-password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> forceChangePassword(@Valid @RequestBody ForceChangePasswordRequest forceChangePasswordRequest) {
        User user = userRepo.findOneById(forceChangePasswordRequest.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with given id: " + forceChangePasswordRequest.getUserId()));

        user.setPassword(encoder.encode(forceChangePasswordRequest.getNewPassword()));
        userRepo.save(user);
        trustedDeviceService.revokeAllRemembered(user.getId());
        auditLogService.logSecurity("User-Password", user.getId(), user.getUsername(),
                Map.of("username", user.getUsername(), "action", "Wymuszono zmianę hasła przez administratora"));

        return new ResponseEntity<>(new MessageResponse("Password changed successfully"), HttpStatus.OK);
    }
}

package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.TrustedDeviceDTO;
import com.clinic.clinicmanager.DTO.UserAssignmentDTO;
import com.clinic.clinicmanager.DTO.UserAvatarDTO;
import com.clinic.clinicmanager.DTO.UserDTO;
import com.clinic.clinicmanager.DTO.UserPreferencesDTO;
import com.clinic.clinicmanager.DTO.request.auth.ChangeCompanyPinRequest;
import com.clinic.clinicmanager.DTO.request.auth.SetCompanyPinRequest;
import com.clinic.clinicmanager.DTO.response.MessageResponse;
import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.service.AvatarService;
import com.clinic.clinicmanager.service.CompanyModeService;
import com.clinic.clinicmanager.service.MfaService;
import com.clinic.clinicmanager.service.UserService;
import com.clinic.clinicmanager.utils.SessionUtils;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static java.util.Objects.isNull;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final CompanyModeService companyModeService;
    private final MfaService mfaService;
    private final AvatarService avatarService;

    @GetMapping("/all")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<List<?>> getAllUsers() {
        List<?> users = SessionUtils.hasRole(AppRoleHierarchy.ADMIN)
                ? userService.getAllUsers()
                : userService.getAllUserAssignments();
        return new ResponseEntity<>(users, users.isEmpty() ? HttpStatus.NO_CONTENT : HttpStatus.OK);
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UserDTO> getCurrentUser() {
        Long userId = SessionUtils.getUserIdFromSession();
        UserDTO user = userService.getUserById(userId);
        return new ResponseEntity<>(user, HttpStatus.OK);
    }

    @GetMapping("/me/preferences")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UserPreferencesDTO> getMyPreferences() {
        Long userId = SessionUtils.getUserIdFromSession();
        return new ResponseEntity<>(userService.getMyPreferences(userId), HttpStatus.OK);
    }

    @PutMapping("/me/preferences")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UserPreferencesDTO> updateMyPreferences(@RequestBody UserPreferencesDTO preferences) {
        Long userId = SessionUtils.getUserIdFromSession();
        return new ResponseEntity<>(userService.updateMyPreferences(userId, preferences), HttpStatus.OK);
    }

    @GetMapping("/avatars/taken")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<String>> getTakenAvatars() {
        Long userId = SessionUtils.getUserIdFromSession();
        return new ResponseEntity<>(avatarService.getAvatarsTakenByOthers(userId), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<?> getUserById(@PathVariable(value = "id") Long id) {
        Object user = SessionUtils.hasRole(AppRoleHierarchy.ADMIN)
                ? userService.getUserById(id)
                : userService.getUserAssignmentById(id);
        return new ResponseEntity<>(user, HttpStatus.OK);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<UserAvatarDTO> getUserByEmployeeId(@PathVariable(value = "employeeId") Long employeeId) {
        UserAvatarDTO user = userService.getUserAvatarByEmployeeId(employeeId);
        return new ResponseEntity<>(user, HttpStatus.OK);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> updateUser(@PathVariable(value = "id") Long id, @NonNull @RequestBody UserDTO user) {
        UserDetailsImpl loggedUser = SessionUtils.getUserDetailsFromSession();
        if (isNull(loggedUser)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        boolean self = id.equals(loggedUser.getId());
        if (!self && !SessionUtils.hasRole(AppRoleHierarchy.COMPANY)) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }
        UserDTO saved = userService.updateUser(id, user);
        if (!self && !SessionUtils.hasRole(AppRoleHierarchy.ADMIN)) {
            return new ResponseEntity<>(new UserAssignmentDTO(saved), HttpStatus.OK);
        }
        return new ResponseEntity<>(saved, HttpStatus.OK);
    }

    @PutMapping("/me/company-pin")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<MessageResponse> changeOwnCompanyPin(@Valid @RequestBody ChangeCompanyPinRequest request) {
        companyModeService.changeOwnPin(request.getCurrentPin(), request.getNewPin());
        return new ResponseEntity<>(new MessageResponse("PIN trybu firmowego został zmieniony."), HttpStatus.OK);
    }

    @PutMapping("/{id}/company-pin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> setCompanyPin(@PathVariable(value = "id") Long id, @Valid @RequestBody SetCompanyPinRequest request) {
        companyModeService.setPinByAdmin(id, request.getNewPin());
        return new ResponseEntity<>(new MessageResponse("PIN trybu firmowego został ustawiony."), HttpStatus.OK);
    }

    @GetMapping("/{id}/mfa/trusted-devices")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TrustedDeviceDTO>> getUserTrustedDevices(@PathVariable(value = "id") Long id) {
        return new ResponseEntity<>(mfaService.getTrustedDevicesOfUser(id), HttpStatus.OK);
    }

    @PostMapping("/{id}/mfa/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MessageResponse> resetMfa(@PathVariable(value = "id") Long id) {
        String username = mfaService.resetByAdmin(id);
        return new ResponseEntity<>(new MessageResponse("Zresetowano weryfikację dwuetapową użytkownika " + username + "."), HttpStatus.OK);
    }
}

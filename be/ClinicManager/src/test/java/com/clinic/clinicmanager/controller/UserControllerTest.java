package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.EmployeeSummaryDTO;
import com.clinic.clinicmanager.DTO.RoleDTO;
import com.clinic.clinicmanager.DTO.TrustedDeviceDTO;
import com.clinic.clinicmanager.DTO.UserAssignmentDTO;
import com.clinic.clinicmanager.DTO.UserAvatarDTO;
import com.clinic.clinicmanager.DTO.UserDTO;
import com.clinic.clinicmanager.DTO.UserPreferencesDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.constants.CardTheme;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.service.AvatarService;
import com.clinic.clinicmanager.service.CompanyModeService;
import com.clinic.clinicmanager.service.MfaService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import com.clinic.clinicmanager.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import({WebSecurityConfig.class, AuthEntryPointJwt.class})
class UserControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    UserService userService;

    @MockBean
    CompanyModeService companyModeService;

    @MockBean
    MfaService mfaService;

    @MockBean
    AvatarService avatarService;

    @MockBean
    UserDetailsServiceImpl userDetailsService;

    @MockBean
    JwtUtils jwtUtils;

    @MockBean
    TokenBlacklistService tokenBlacklistService;

    private static UserDetailsImpl userWith(Long id, String... roles) {
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());
        return new UserDetailsImpl(id, "testuser", "pass", null, null, authorities);
    }

    private UserDTO buildUserDTO(Long id) {
        UserDTO dto = new UserDTO();
        dto.setId(id);
        dto.setUsername("testuser");
        return dto;
    }

    @Test
    void getAllUsers_shouldReturn200_whenAdminAndUsersExist() throws Exception {
        List<UserDTO> users = List.of(buildUserDTO(1L), buildUserDTO(2L));
        when(userService.getAllUsers()).thenReturn(users);

        mockMvc.perform(get("/api/user/all")
                        .with(user(userWith(1L, "ADMIN", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getAllUsers_shouldReturn204_whenAdminAndNoUsers() throws Exception {
        when(userService.getAllUsers()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/user/all")
                        .with(user(userWith(1L, "ADMIN", "USER"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void getAllUsers_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/user/all")
                        .with(user(userWith(1L, "USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getCurrentUser_shouldReturn200_withOwnData() throws Exception {
        UserDTO userDTO = buildUserDTO(1L);
        when(userService.getUserById(1L)).thenReturn(userDTO);

        mockMvc.perform(get("/api/user/me")
                        .with(user(userWith(1L, "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    @Test
    void getUserById_shouldReturn200_whenAdmin() throws Exception {
        UserDTO userDTO = buildUserDTO(2L);
        when(userService.getUserById(2L)).thenReturn(userDTO);

        mockMvc.perform(get("/api/user/2")
                        .with(user(userWith(1L, "ADMIN", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void getUserById_shouldReturn404_whenNotFound() throws Exception {
        when(userService.getUserById(99L))
                .thenThrow(new ResourceNotFoundException("User not found with ID: 99"));

        mockMvc.perform(get("/api/user/99")
                        .with(user(userWith(1L, "ADMIN", "USER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUserById_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/user/1")
                        .with(user(userWith(1L, "USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserByEmployeeId_shouldReturnOnlyAvatarFields_whenFound() throws Exception {
        when(userService.getUserAvatarByEmployeeId(5L)).thenReturn(new UserAvatarDTO(1L, "avatar2.png"));

        mockMvc.perform(get("/api/user/employee/5")
                        .with(user(userWith(1L, "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.avatar").value("avatar2.png"))
                .andExpect(jsonPath("$.roles").doesNotExist())
                .andExpect(jsonPath("$.hasCompanyPin").doesNotExist())
                .andExpect(jsonPath("$.employee").doesNotExist());
    }

    @Test
    void getUserByEmployeeId_shouldReturn404_whenNotFound() throws Exception {
        when(userService.getUserAvatarByEmployeeId(99L))
                .thenThrow(new ResourceNotFoundException("User not found with ID: 99"));

        mockMvc.perform(get("/api/user/employee/99")
                        .with(user(userWith(1L,  "USER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateUser_shouldReturn200_whenUserUpdatesOwnProfile() throws Exception {
        UserDTO inputDTO = buildUserDTO(null);
        UserDTO savedDTO = buildUserDTO(1L);
        when(userService.updateUser(eq(1L), any(UserDTO.class))).thenReturn(savedDTO);

        mockMvc.perform(put("/api/user/1")
                        .with(user(userWith(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void updateUser_shouldReturn403_whenUserUpdatesOtherProfile() throws Exception {
        mockMvc.perform(put("/api/user/2")
                        .with(user(userWith(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUserDTO(null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUser_shouldReturn200_whenAdminUpdatesAnyProfile() throws Exception {
        UserDTO inputDTO = buildUserDTO(null);
        UserDTO savedDTO = buildUserDTO(2L);
        when(userService.updateUser(eq(2L), any(UserDTO.class))).thenReturn(savedDTO);

        mockMvc.perform(put("/api/user/2")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2));
    }

    @Test
    void updateUser_shouldReturn400_whenAdminRoleGranted() throws Exception {
        when(userService.updateUser(eq(2L), any(UserDTO.class)))
                .thenThrow(new ValidationException("Nie można nadać roli ADMIN."));

        mockMvc.perform(put("/api/user/2")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUserDTO(2L))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Nie można nadać roli ADMIN."));
    }

    @Test
    void updateUser_shouldReturn400_whenAdminRoleRevoked() throws Exception {
        when(userService.updateUser(eq(1L), any(UserDTO.class)))
                .thenThrow(new ValidationException("Nie można zmienić roli ADMIN."));

        mockMvc.perform(put("/api/user/1")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUserDTO(1L))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Nie można zmienić roli ADMIN."));
    }

    @Test
    void getAllUsers_shouldReturnReducedFields_whenCompanyMode() throws Exception {
        UserAssignmentDTO assignment = new UserAssignmentDTO(2L, "anna", "avatar2.png",
                List.of(new RoleDTO(3L, RoleType.ROLE_MASTER)), new EmployeeSummaryDTO(5L, "Anna", "Nowak"));
        when(userService.getAllUserAssignments()).thenReturn(List.of(assignment));

        mockMvc.perform(get("/api/user/all")
                        .with(user(userWith(1L, "MASTER", "COMPANY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].username").value("anna"))
                .andExpect(jsonPath("$[0].employee.id").value(5))
                .andExpect(jsonPath("$[0].employee.name").value("Anna"))
                .andExpect(jsonPath("$[0].employee.lastName").value("Nowak"))
                .andExpect(jsonPath("$[0].avatar").value("avatar2.png"))
                .andExpect(jsonPath("$[0].roles.length()").value(1))
                .andExpect(jsonPath("$[0].roles[0].id").value(3))
                .andExpect(jsonPath("$[0].roles[0].name").value("ROLE_MASTER"))
                .andExpect(jsonPath("$[0].hasCompanyPin").doesNotExist())
                .andExpect(jsonPath("$[0].companyPin").doesNotExist());
        verify(userService, never()).getAllUsers();
    }

    @Test
    void getAllUsers_shouldReturnFullDto_whenAdmin() throws Exception {
        UserDTO full = buildUserDTO(2L);
        full.setAvatar("avatar2.png");
        full.setHasCompanyPin(true);
        when(userService.getAllUsers()).thenReturn(List.of(full));

        mockMvc.perform(get("/api/user/all")
                        .with(user(userWith(1L, "ADMIN", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].avatar").value("avatar2.png"))
                .andExpect(jsonPath("$[0].hasCompanyPin").value(true))
                .andExpect(jsonPath("$[0].roles").isArray());
        verify(userService, never()).getAllUserAssignments();
    }

    @Test
    void getAllUsers_shouldReturn403_whenMasterWithoutCompanyMode() throws Exception {
        mockMvc.perform(get("/api/user/all")
                        .with(user(userWith(1L, "MASTER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getUserById_shouldReturnReducedFields_whenCompanyMode() throws Exception {
        when(userService.getUserAssignmentById(2L)).thenReturn(new UserAssignmentDTO(2L, "anna", "avatar2.png",
                List.of(new RoleDTO(2L, RoleType.ROLE_USER)), null));

        mockMvc.perform(get("/api/user/2")
                        .with(user(userWith(1L, "MASTER", "COMPANY"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.username").value("anna"))
                .andExpect(jsonPath("$.employee").isEmpty())
                .andExpect(jsonPath("$.avatar").value("avatar2.png"))
                .andExpect(jsonPath("$.roles[0].name").value("ROLE_USER"))
                .andExpect(jsonPath("$.hasCompanyPin").doesNotExist())
                .andExpect(jsonPath("$.companyPin").doesNotExist());
        verify(userService, never()).getUserById(any());
    }

    @Test
    void getUserById_shouldReturn403_whenMasterWithoutCompanyMode() throws Exception {
        mockMvc.perform(get("/api/user/2")
                        .with(user(userWith(1L, "MASTER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUser_shouldReturnReducedFields_whenCompanyUpdatesOtherProfile() throws Exception {
        UserDTO savedDTO = buildUserDTO(2L);
        savedDTO.setAvatar("avatar2.png");
        savedDTO.setRoles(List.of(new RoleDTO(3L, RoleType.ROLE_MASTER)));
        savedDTO.setHasCompanyPin(true);
        savedDTO.setEmployee(new EmployeeSummaryDTO(5L, "Anna", "Nowak"));
        when(userService.updateUser(eq(2L), any(UserDTO.class))).thenReturn(savedDTO);

        mockMvc.perform(put("/api/user/2")
                        .with(user(userWith(1L, "MASTER", "COMPANY")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUserDTO(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.employee.id").value(5))
                .andExpect(jsonPath("$.avatar").value("avatar2.png"))
                .andExpect(jsonPath("$.roles[0].name").value("ROLE_MASTER"))
                .andExpect(jsonPath("$.hasCompanyPin").doesNotExist())
                .andExpect(jsonPath("$.companyPin").doesNotExist());
    }

    @Test
    void updateUser_shouldReturn403_whenCompanyEditsAdminAccount() throws Exception {
        when(userService.updateUser(eq(2L), any(UserDTO.class)))
                .thenThrow(new AccessDeniedException("Nie można edytować konta administratora."));

        mockMvc.perform(put("/api/user/2")
                        .with(user(userWith(1L, "MASTER", "COMPANY")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUserDTO(2L))))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Nie można edytować konta administratora."));
    }

    @Test
    void updateUser_shouldReturn409_whenCompanyAssignsAlreadyAssignedEmployeeToOtherUser() throws Exception {
        when(userService.updateUser(eq(2L), any(UserDTO.class)))
                .thenThrow(new ConflictException("Pracownik jest już przypisany do użytkownika anna."));

        mockMvc.perform(put("/api/user/2")
                        .with(user(userWith(1L, "MASTER", "COMPANY")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUserDTO(2L))))
                .andExpect(status().isConflict())
                .andExpect(content().string("Pracownik jest już przypisany do użytkownika anna."));
    }

    @Test
    void updateUser_shouldReturn409_whenCompanyAssignsAlreadyAssignedEmployeeToSelf() throws Exception {
        when(userService.updateUser(eq(1L), any(UserDTO.class)))
                .thenThrow(new ConflictException("Pracownik jest już przypisany do użytkownika anna."));

        mockMvc.perform(put("/api/user/1")
                        .with(user(userWith(1L, "MASTER", "COMPANY")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUserDTO(1L))))
                .andExpect(status().isConflict())
                .andExpect(content().string("Pracownik jest już przypisany do użytkownika anna."));
    }

    @Test
    void updateUser_shouldReturn403_whenMasterWithoutCompanyUpdatesOtherProfile() throws Exception {
        mockMvc.perform(put("/api/user/2")
                        .with(user(userWith(1L, "MASTER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUserDTO(null))))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateUser_shouldNeverExposeCompanyPin() throws Exception {
        UserDTO savedDTO = buildUserDTO(2L);
        savedDTO.setCompanyPin("482915");
        savedDTO.setHasCompanyPin(true);
        when(userService.updateUser(eq(2L), any(UserDTO.class))).thenReturn(savedDTO);

        mockMvc.perform(put("/api/user/2")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"testuser\",\"roles\":[],\"companyPin\":\"482915\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasCompanyPin").value(true))
                .andExpect(jsonPath("$.companyPin").doesNotExist());
        verify(userService).updateUser(eq(2L), argThat(dto -> "482915".equals(dto.getCompanyPin())));
    }

    @Test
    void changeOwnCompanyPin_shouldReturn200_whenCompanyMode() throws Exception {
        mockMvc.perform(put("/api/user/me/company-pin")
                        .with(user(userWith(1L, "MASTER", "COMPANY")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPin\":\"482915\",\"newPin\":\"739164\"}"))
                .andExpect(status().isOk());
        verify(companyModeService).changeOwnPin("482915", "739164");
    }

    @Test
    void changeOwnCompanyPin_shouldReturn403_whenMasterWithoutCompanyMode() throws Exception {
        mockMvc.perform(put("/api/user/me/company-pin")
                        .with(user(userWith(1L, "MASTER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPin\":\"482915\",\"newPin\":\"739164\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(companyModeService);
    }

    @Test
    void changeOwnCompanyPin_shouldReturn400_whenCurrentPinWrong() throws Exception {
        doThrow(new ValidationException("Nieprawidłowy PIN. Pozostało prób: 4."))
                .when(companyModeService).changeOwnPin("111222", "739164");

        mockMvc.perform(put("/api/user/me/company-pin")
                        .with(user(userWith(1L, "MASTER", "COMPANY")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPin\":\"111222\",\"newPin\":\"739164\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setCompanyPin_shouldReturn200_whenAdmin() throws Exception {
        mockMvc.perform(put("/api/user/2/company-pin")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPin\":\"739164\"}"))
                .andExpect(status().isOk());
        verify(companyModeService).setPinByAdmin(2L, "739164");
    }

    @Test
    void setCompanyPin_shouldReturn403_whenCompanyMode() throws Exception {
        mockMvc.perform(put("/api/user/2/company-pin")
                        .with(user(userWith(1L, "MASTER", "COMPANY")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPin\":\"739164\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(companyModeService);
    }

    @Test
    void setCompanyPin_shouldReturn409_whenTargetIsNotMaster() throws Exception {
        doThrow(new ConflictException("Użytkownik anna nie posiada roli MASTER."))
                .when(companyModeService).setPinByAdmin(2L, "739164");

        mockMvc.perform(put("/api/user/2/company-pin")
                        .with(user(userWith(1L, "ADMIN", "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPin\":\"739164\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void resetMfa_shouldReturn200WithMessage_whenAdmin() throws Exception {
        when(mfaService.resetByAdmin(2L)).thenReturn("anna");

        mockMvc.perform(post("/api/user/2/mfa/reset")
                        .with(user(userWith(1L, "ADMIN", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Zresetowano weryfikację dwuetapową użytkownika anna."));
    }

    @Test
    void resetMfa_shouldReturn403WithMessage_whenTargetIsAdmin() throws Exception {
        when(mfaService.resetByAdmin(3L))
                .thenThrow(new AccessDeniedException("Nie można zresetować 2FA administratora w aplikacji."));

        mockMvc.perform(post("/api/user/3/mfa/reset")
                        .with(user(userWith(1L, "ADMIN", "USER"))))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Nie można zresetować 2FA administratora w aplikacji."));
    }

    @Test
    void resetMfa_shouldReturn409_whenTargetHasNoMfa() throws Exception {
        when(mfaService.resetByAdmin(2L))
                .thenThrow(new ConflictException("Użytkownik nie ma włączonej weryfikacji dwuetapowej."));

        mockMvc.perform(post("/api/user/2/mfa/reset")
                        .with(user(userWith(1L, "ADMIN", "USER"))))
                .andExpect(status().isConflict())
                .andExpect(content().string("Użytkownik nie ma włączonej weryfikacji dwuetapowej."));
    }

    @Test
    void getUserTrustedDevices_shouldReturnList_whenAdmin() throws Exception {
        TrustedDeviceDTO device = new TrustedDeviceDTO(5L, "Safari · iPad", LocalDateTime.of(2026, 9, 1, 8, 0),
                LocalDateTime.of(2026, 10, 16, 18, 15), LocalDateTime.of(2027, 2, 28, 8, 0), "10.0.0.7", false);
        when(mfaService.getTrustedDevicesOfUser(2L)).thenReturn(List.of(device));

        mockMvc.perform(get("/api/user/2/mfa/trusted-devices")
                        .with(user(userWith(1L, "ADMIN", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].label").value("Safari · iPad"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-09-01T08:00:00"))
                .andExpect(jsonPath("$[0].lastUsedAt").value("2026-10-16T18:15:00"))
                .andExpect(jsonPath("$[0].expiresAt").value("2027-02-28T08:00:00"))
                .andExpect(jsonPath("$[0].lastIp").value("10.0.0.7"))
                .andExpect(jsonPath("$[0].current").value(false));
    }

    @Test
    void getUserTrustedDevices_shouldReturn404_whenUserMissing() throws Exception {
        when(mfaService.getTrustedDevicesOfUser(99L))
                .thenThrow(new ResourceNotFoundException("User not found with given id: 99"));

        mockMvc.perform(get("/api/user/99/mfa/trusted-devices")
                        .with(user(userWith(1L, "ADMIN", "USER"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUserTrustedDevices_shouldReturn403_whenCompanyMode() throws Exception {
        mockMvc.perform(get("/api/user/2/mfa/trusted-devices")
                        .with(user(userWith(1L, "MASTER", "COMPANY"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mfaService);
    }

    @Test
    void resetMfa_shouldReturn403_whenCompanyMode() throws Exception {
        mockMvc.perform(post("/api/user/2/mfa/reset")
                        .with(user(userWith(1L, "MASTER", "COMPANY"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mfaService);
    }

    @Test
    void getMyPreferences_shouldReturnLight_whenUserNeverChangedIt() throws Exception {
        when(userService.getMyPreferences(1L)).thenReturn(new UserPreferencesDTO(CardTheme.LIGHT));

        mockMvc.perform(get("/api/user/me/preferences")
                        .with(user(userWith(1L, "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardTheme").value("LIGHT"));
        verify(userService).getMyPreferences(1L);
    }

    @Test
    void updateMyPreferences_shouldReturnDark_whenUserSetsDark() throws Exception {
        when(userService.updateMyPreferences(eq(1L), argThat(p -> p.getCardTheme() == CardTheme.DARK)))
                .thenReturn(new UserPreferencesDTO(CardTheme.DARK));

        mockMvc.perform(put("/api/user/me/preferences")
                        .with(user(userWith(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardTheme\":\"DARK\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardTheme").value("DARK"));
        verify(userService).updateMyPreferences(eq(1L), argThat(p -> p.getCardTheme() == CardTheme.DARK));
    }

    @Test
    void getMyPreferences_shouldReadPreferencesOfLoggedUserOnly() throws Exception {
        when(userService.getMyPreferences(1L)).thenReturn(new UserPreferencesDTO(CardTheme.DARK));
        when(userService.getMyPreferences(2L)).thenReturn(new UserPreferencesDTO(CardTheme.LIGHT));

        mockMvc.perform(get("/api/user/me/preferences")
                        .with(user(userWith(2L, "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardTheme").value("LIGHT"));
        verify(userService).getMyPreferences(2L);
        verify(userService, never()).getMyPreferences(1L);
    }

    @Test
    void updateMyPreferences_shouldUpdateLoggedUserOnly() throws Exception {
        when(userService.updateMyPreferences(eq(2L), any(UserPreferencesDTO.class)))
                .thenReturn(new UserPreferencesDTO(CardTheme.DARK));

        mockMvc.perform(put("/api/user/me/preferences")
                        .with(user(userWith(2L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardTheme\":\"DARK\",\"id\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardTheme").value("DARK"));
        verify(userService).updateMyPreferences(eq(2L), any(UserPreferencesDTO.class));
        verify(userService, never()).updateMyPreferences(eq(1L), any());
    }

    @Test
    void updateMyPreferences_shouldReturn400_whenCardThemeMissing() throws Exception {
        when(userService.updateMyPreferences(eq(1L), argThat(p -> p.getCardTheme() == null)))
                .thenThrow(new ValidationException("Nie wybrano wyglądu karty."));

        mockMvc.perform(put("/api/user/me/preferences")
                        .with(user(userWith(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Nie wybrano wyglądu karty."));
    }

    @Test
    void updateMyPreferences_shouldReturn400_whenCardThemeNull() throws Exception {
        when(userService.updateMyPreferences(eq(1L), argThat(p -> p.getCardTheme() == null)))
                .thenThrow(new ValidationException("Nie wybrano wyglądu karty."));

        mockMvc.perform(put("/api/user/me/preferences")
                        .with(user(userWith(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardTheme\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Nie wybrano wyglądu karty."));
    }

    @Test
    void updateMyPreferences_shouldReturn400_whenCardThemeUnknown() throws Exception {
        mockMvc.perform(put("/api/user/me/preferences")
                        .with(user(userWith(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardTheme\":\"BLUE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Nieprawidłowy format danych."));
        verifyNoInteractions(userService);
    }

    @Test
    void updateMyPreferences_shouldReturn400_whenBodyMissing() throws Exception {
        mockMvc.perform(put("/api/user/me/preferences")
                        .with(user(userWith(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userService);
    }

    @Test
    void getMyPreferences_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/user/me/preferences"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void updateMyPreferences_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(put("/api/user/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardTheme\":\"DARK\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void getTakenAvatars_shouldReturn200AndListOfOtherUsersAvatars_whenUser() throws Exception {
        when(avatarService.getAvatarsTakenByOthers(7L)).thenReturn(List.of("avatar2.png", "avatar7.png"));

        mockMvc.perform(get("/api/user/avatars/taken")
                        .with(user(userWith(7L, "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("avatar2.png"))
                .andExpect(jsonPath("$[1]").value("avatar7.png"));
        verify(avatarService).getAvatarsTakenByOthers(7L);
    }

    @Test
    void getTakenAvatars_shouldReturn200AndEmptyArray_whenNoOtherAvatarsTaken() throws Exception {
        when(avatarService.getAvatarsTakenByOthers(7L)).thenReturn(List.of());

        mockMvc.perform(get("/api/user/avatars/taken")
                        .with(user(userWith(7L, "USER"))))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void getTakenAvatars_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/user/avatars/taken"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(avatarService);
    }

    @Test
    void updateUser_shouldReturn409WithMessage_whenAvatarTakenByOtherUser() throws Exception {
        UserDTO input = buildUserDTO(1L);
        input.setAvatar("avatar7.png");
        when(userService.updateUser(eq(1L), any(UserDTO.class)))
                .thenThrow(new ConflictException("Ten avatar jest już zajęty."));

        mockMvc.perform(put("/api/user/1")
                        .with(user(userWith(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isConflict())
                .andExpect(content().string("Ten avatar jest już zajęty."));
    }

    @Test
    void updateUser_shouldReturn400WithMessage_whenAvatarOutsideCatalog() throws Exception {
        UserDTO input = buildUserDTO(1L);
        input.setAvatar("../x");
        when(userService.updateUser(eq(1L), any(UserDTO.class)))
                .thenThrow(new ValidationException("Nieprawidłowy avatar."));

        mockMvc.perform(put("/api/user/1")
                        .with(user(userWith(1L, "USER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Nieprawidłowy avatar."));
    }
}

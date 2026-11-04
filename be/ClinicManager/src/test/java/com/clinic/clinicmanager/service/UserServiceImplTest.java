package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.EmployeeSummaryDTO;
import com.clinic.clinicmanager.DTO.RoleDTO;
import com.clinic.clinicmanager.DTO.UserAssignmentDTO;
import com.clinic.clinicmanager.DTO.UserAvatarDTO;
import com.clinic.clinicmanager.DTO.UserDTO;
import com.clinic.clinicmanager.DTO.UserPreferencesDTO;
import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.Role;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.CardTheme;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.repo.RoleRepo;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.service.impl.UserServiceImpl;
import com.clinic.clinicmanager.utils.RoleAssignmentRules;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock UserRepo userRepo;
    @Mock EmployeeRepo employeeRepo;
    @Mock AuditLogService auditLogService;
    @Mock RoleRepo roleRepo;
    @Mock CompanyModeService companyModeService;
    @Mock MfaService mfaService;
    @Mock TrustedDeviceService trustedDeviceService;

    @InjectMocks
    UserServiceImpl userService;

    private User existingUser;
    private Role adminRole;
    private Role userRole;
    private Role masterRole;

    @BeforeEach
    void setUp() {
        adminRole = Role.builder().id(1L).name(RoleType.ROLE_ADMIN).build();
        userRole = Role.builder().id(2L).name(RoleType.ROLE_USER).build();
        masterRole = Role.builder().id(3L).name(RoleType.ROLE_MASTER).build();

        existingUser = User.builder()
                .id(1L)
                .username("jan")
                .password("hashedPass")
                .avatar("avatar1.png")
                .roles(Set.of(adminRole))
                .employee(null)
                .build();
    }

    @Test
    void getAllUsers_shouldReturnList() {
        when(userRepo.findAll()).thenReturn(List.of(existingUser));

        List<UserDTO> result = userService.getAllUsers();

        assertEquals(1, result.size());
        assertEquals("jan", result.getFirst().getUsername());
    }

    @Test
    void getAllUsers_shouldFillTrustedDevicesCountFromSingleGroupedQuery() {
        User other = User.builder().id(2L).username("anna").password("hash").roles(Set.of(userRole)).build();
        when(userRepo.findAll()).thenReturn(List.of(existingUser, other));
        when(trustedDeviceService.countActiveRememberedByUser()).thenReturn(Map.of(1L, 3));

        List<UserDTO> result = userService.getAllUsers();

        assertEquals(3, result.get(0).getTrustedDevicesCount());
        assertEquals(0, result.get(1).getTrustedDevicesCount());
        verify(trustedDeviceService, times(1)).countActiveRememberedByUser();
        verify(trustedDeviceService, never()).countActiveRemembered(any());
    }

    @Test
    void getUserById_shouldReturnDTO_whenFound() {
        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(trustedDeviceService.countActiveRemembered(1L)).thenReturn(2);

        UserDTO result = userService.getUserById(1L);

        assertEquals(1L, result.getId());
        assertEquals(2, result.getTrustedDevicesCount());
    }

    @Test
    void getUserById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(userRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(99L));
    }

    @Test
    void getUserAvatarByEmployeeId_shouldReturnAvatarDTO_whenFound() {
        Employee emp = Employee.builder().id(5L).name("Anna").lastName("Nowak").build();
        User userWithEmployee = User.builder()
                .id(2L).username("anna").password("pass").avatar("avatar2.png")
                .roles(Set.of()).employee(emp).build();

        when(userRepo.findByEmployeeId(5L)).thenReturn(Optional.of(userWithEmployee));

        UserAvatarDTO result = userService.getUserAvatarByEmployeeId(5L);

        assertEquals(2L, result.getId());
        assertEquals("avatar2.png", result.getAvatar());
    }

    @Test
    void getUserAvatarByEmployeeId_shouldThrowResourceNotFoundException_whenNotFound() {
        when(userRepo.findByEmployeeId(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserAvatarByEmployeeId(99L));
    }

    @Test
    void updateUser_shouldSaveAndReturnDTO_whenAdmin() {
        UserDTO input = new UserDTO();
        input.setUsername("janUpdated");
        input.setAvatar("avatar2.png");
        input.setRoles(List.of(new RoleDTO(1L, RoleType.ROLE_ADMIN)));

        User savedUser = User.builder()
                .id(1L).username("janUpdated").password("hashedPass")
                .avatar("avatar2.png").roles(Set.of(adminRole)).employee(null).build();

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser)).thenReturn(Optional.of(savedUser));
        when(roleRepo.findById(1L)).thenReturn(Optional.of(adminRole));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.ADMIN)).thenReturn(true);
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            UserDTO result = userService.updateUser(1L, input);

            verify(userRepo).saveAndFlush(any(User.class));
            assertEquals("janUpdated", result.getUsername());
        }
    }

    @Test
    void updateUser_shouldPreserveRolesAndUsername_whenNotAdmin() {
        existingUser.setRoles(Set.of(userRole));
        UserDTO input = new UserDTO();
        input.setUsername("hackerNewName");
        input.setAvatar("avatar3.png");
        input.setRoles(List.of(new RoleDTO(3L, RoleType.ROLE_MASTER)));

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser)).thenReturn(Optional.of(existingUser));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            userService.updateUser(1L, input);

            verify(userRepo).saveAndFlush(argThat(u ->
                    "jan".equals(u.getUsername()) &&
                    u.getRoles().equals(Set.of(userRole))
            ));
        }
    }

    @Test
    void updateUser_shouldThrowConflictException_whenCompanyAssignsEmployeeOfAnotherUserToOtherUser() {
        existingUser.setRoles(Set.of(userRole));
        UserDTO input = inputWithEmployee(5L);

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepo.findByEmployeeId(5L)).thenReturn(Optional.of(userAssignedToEmployee5()));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(2L);

            ConflictException ex = assertThrows(ConflictException.class, () -> userService.updateUser(1L, input));
            assertEquals("Pracownik jest już przypisany do użytkownika other.", ex.getMessage());
            verify(userRepo, never()).saveAndFlush(any());
        }
    }

    @Test
    void updateUser_shouldThrowConflictException_whenCompanyAssignsEmployeeOfAnotherUserToSelf() {
        existingUser.setRoles(Set.of(masterRole));
        UserDTO input = inputWithEmployee(5L);

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepo.findByEmployeeId(5L)).thenReturn(Optional.of(userAssignedToEmployee5()));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            ConflictException ex = assertThrows(ConflictException.class, () -> userService.updateUser(1L, input));
            assertEquals("Pracownik jest już przypisany do użytkownika other.", ex.getMessage());
            verify(userRepo, never()).saveAndFlush(any());
        }
    }

    @Test
    void updateUser_shouldThrowConflictException_whenAdminAssignsEmployeeOfAnotherUserToSelf() {
        UserDTO input = inputWithEmployee(5L);

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepo.findByEmployeeId(5L)).thenReturn(Optional.of(userAssignedToEmployee5()));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.ADMIN)).thenReturn(true);
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            ConflictException ex = assertThrows(ConflictException.class, () -> userService.updateUser(1L, input));
            assertEquals("Pracownik jest już przypisany do użytkownika other.", ex.getMessage());
        }
    }

    @Test
    void updateUser_shouldThrowAccessDenied_whenCompanyEditsAdminAccount() {
        UserDTO input = inputWithEmployee(5L);

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(2L);

            AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> userService.updateUser(1L, input));
            assertEquals("Nie można edytować konta administratora.", ex.getMessage());
            verify(userRepo, never()).saveAndFlush(any());
            verifyNoInteractions(employeeRepo);
            verifyNoInteractions(auditLogService);
        }
    }

    private UserDTO inputWithEmployee(Long employeeId) {
        UserDTO input = new UserDTO();
        input.setUsername("jan");
        input.setRoles(List.of());
        EmployeeSummaryDTO empDTO = new EmployeeSummaryDTO();
        empDTO.setId(employeeId);
        input.setEmployee(empDTO);
        return input;
    }

    private User userAssignedToEmployee5() {
        Employee emp = Employee.builder().id(5L).name("Anna").lastName("Nowak").build();
        return User.builder()
                .id(99L).username("other").password("pass").avatar("av.png")
                .roles(Set.of(userRole)).employee(emp).build();
    }

    @Test
    void updateUser_shouldThrowResourceNotFoundException_whenUserNotFound() {
        when(userRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.updateUser(99L, new UserDTO()));
    }

    @Test
    void getAllUserAssignments_shouldReturnIdUsernameAvatarRolesAndEmployee() {
        Employee emp = Employee.builder().id(5L).name("Anna").lastName("Nowak").build();
        existingUser.setEmployee(emp);
        existingUser.setCompanyPinHash("pinHash");
        when(userRepo.findAll()).thenReturn(List.of(existingUser));

        List<UserAssignmentDTO> result = userService.getAllUserAssignments();

        assertEquals(1, result.size());
        UserAssignmentDTO dto = result.getFirst();
        assertEquals(1L, dto.getId());
        assertEquals("jan", dto.getUsername());
        assertEquals("avatar1.png", dto.getAvatar());
        assertEquals(List.of(new RoleDTO(1L, RoleType.ROLE_ADMIN)), dto.getRoles());
        assertEquals(5L, dto.getEmployee().getId());
    }

    @Test
    void getUserAssignmentById_shouldReturnAvatarAndRoles() {
        existingUser.setRoles(Set.of(masterRole));
        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));

        UserAssignmentDTO result = userService.getUserAssignmentById(1L);

        assertEquals("avatar1.png", result.getAvatar());
        assertEquals(List.of(new RoleDTO(3L, RoleType.ROLE_MASTER)), result.getRoles());
        assertNull(result.getEmployee());
    }

    @Test
    void updateUser_shouldChangeOnlyEmployee_whenCompanyEditsOtherUser() {
        existingUser.setRoles(Set.of(userRole));
        Employee emp = Employee.builder().id(5L).name("Anna").lastName("Nowak").build();
        UserDTO input = new UserDTO();
        input.setUsername("hackerNewName");
        input.setAvatar("avatar9.png");
        input.setRoles(List.of());
        EmployeeSummaryDTO empDTO = new EmployeeSummaryDTO();
        empDTO.setId(5L);
        input.setEmployee(empDTO);

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepo.findByEmployeeId(5L)).thenReturn(Optional.empty());
        when(employeeRepo.findById(5L)).thenReturn(Optional.of(emp));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(2L);

            userService.updateUser(1L, input);

            verify(userRepo).saveAndFlush(argThat(u ->
                    "jan".equals(u.getUsername()) &&
                    "avatar1.png".equals(u.getAvatar()) &&
                    u.getRoles().equals(Set.of(userRole)) &&
                    u.getEmployee() == emp
            ));
        }
    }

    @Test
    void updateUser_shouldKeepEmployee_whenNotCompany() {
        Employee current = Employee.builder().id(3L).name("Ewa").lastName("Kot").build();
        existingUser.setEmployee(current);
        UserDTO input = new UserDTO();
        input.setRoles(List.of());
        EmployeeSummaryDTO empDTO = new EmployeeSummaryDTO();
        empDTO.setId(5L);
        input.setEmployee(empDTO);

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            userService.updateUser(1L, input);

            verify(userRepo).saveAndFlush(argThat(u -> u.getEmployee() == current));
            verify(employeeRepo, never()).findById(any());
        }
    }

    @Test
    void updateUser_shouldRequirePin_whenAdminAssignsMasterToUserWithoutPin() {
        existingUser.setRoles(Set.of(userRole));
        UserDTO input = new UserDTO();
        input.setUsername("jan");
        input.setRoles(List.of(new RoleDTO(3L, RoleType.ROLE_MASTER)));

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(roleRepo.findById(3L)).thenReturn(Optional.of(masterRole));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.ADMIN)).thenReturn(true);
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            assertThrows(ValidationException.class, () -> userService.updateUser(1L, input));
            verify(userRepo, never()).saveAndFlush(any());
        }
    }

    @Test
    void updateUser_shouldSetPinHash_whenAdminAssignsMasterWithPin() {
        existingUser.setRoles(Set.of(userRole));
        UserDTO input = new UserDTO();
        input.setUsername("jan");
        input.setRoles(List.of(new RoleDTO(3L, RoleType.ROLE_MASTER)));
        input.setCompanyPin("482915");

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(roleRepo.findById(3L)).thenReturn(Optional.of(masterRole));
        when(companyModeService.encodeCompanyPin("482915")).thenReturn("pinHash");

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.ADMIN)).thenReturn(true);
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            userService.updateUser(1L, input);

            verify(userRepo).saveAndFlush(argThat(u ->
                    "pinHash".equals(u.getCompanyPinHash()) && u.getRoles().equals(Set.of(masterRole))));
        }
    }

    @Test
    void updateUser_shouldKeepPinHash_whenAdminEditsMasterWithoutNewPin() {
        existingUser.setRoles(Set.of(masterRole));
        existingUser.setCompanyPinHash("existingHash");
        UserDTO input = new UserDTO();
        input.setUsername("jan");
        input.setRoles(List.of(new RoleDTO(3L, RoleType.ROLE_MASTER)));

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(roleRepo.findById(3L)).thenReturn(Optional.of(masterRole));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.ADMIN)).thenReturn(true);

            userService.updateUser(1L, input);

            verify(userRepo).saveAndFlush(argThat(u -> "existingHash".equals(u.getCompanyPinHash())));
            verifyNoInteractions(companyModeService);
        }
    }

    @Test
    void updateUser_shouldClearPinHash_whenAdminRemovesMasterRole() {
        existingUser.setRoles(Set.of(masterRole));
        existingUser.setCompanyPinHash("existingHash");
        UserDTO input = new UserDTO();
        input.setUsername("jan");
        input.setRoles(List.of(new RoleDTO(2L, RoleType.ROLE_USER)));

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(roleRepo.findById(2L)).thenReturn(Optional.of(userRole));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.ADMIN)).thenReturn(true);

            userService.updateUser(1L, input);

            verify(userRepo).saveAndFlush(argThat(u ->
                    u.getCompanyPinHash() == null && u.getRoles().equals(Set.of(userRole))));
        }
    }

    @Test
    void updateUser_shouldClearMfa_whenAdminRemovesMasterRole() {
        existingUser.setRoles(Set.of(masterRole));
        existingUser.setCompanyPinHash("existingHash");
        existingUser.setMfaSecretEnc("v1:secret");
        existingUser.setMfaEnabledAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        UserDTO input = new UserDTO();
        input.setUsername("jan");
        input.setRoles(List.of(new RoleDTO(2L, RoleType.ROLE_USER)));

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(roleRepo.findById(2L)).thenReturn(Optional.of(userRole));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.ADMIN)).thenReturn(true);

            userService.updateUser(1L, input);

            verify(userRepo).saveAndFlush(argThat(u -> u.getMfaSecretEnc() == null && u.getMfaEnabledAt() == null));
            verify(mfaService).purgeMfaArtifacts(1L);
        }
    }

    @Test
    void updateUser_shouldKeepMfa_whenAdminEditsMasterWithoutRoleChange() {
        LocalDateTime enabledAt = LocalDateTime.of(2026, 10, 1, 9, 0);
        existingUser.setRoles(Set.of(masterRole));
        existingUser.setCompanyPinHash("existingHash");
        existingUser.setMfaSecretEnc("v1:secret");
        existingUser.setMfaEnabledAt(enabledAt);
        UserDTO input = new UserDTO();
        input.setUsername("jan");
        input.setRoles(List.of(new RoleDTO(3L, RoleType.ROLE_MASTER)));

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(roleRepo.findById(3L)).thenReturn(Optional.of(masterRole));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.ADMIN)).thenReturn(true);

            userService.updateUser(1L, input);

            verify(userRepo).saveAndFlush(argThat(u ->
                    "v1:secret".equals(u.getMfaSecretEnc()) && enabledAt.equals(u.getMfaEnabledAt())));
            verifyNoInteractions(mfaService);
        }
    }

    @Test
    void updateUser_shouldRejectGrantingAdmin() {
        existingUser.setRoles(Set.of(userRole));
        UserDTO input = new UserDTO();
        input.setUsername("jan");
        input.setRoles(List.of(new RoleDTO(1L, RoleType.ROLE_ADMIN), new RoleDTO(2L, RoleType.ROLE_USER)));

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(roleRepo.findById(1L)).thenReturn(Optional.of(adminRole));
        when(roleRepo.findById(2L)).thenReturn(Optional.of(userRole));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.ADMIN)).thenReturn(true);

            ValidationException ex = assertThrows(ValidationException.class, () -> userService.updateUser(1L, input));
            assertEquals("Nie można nadać roli ADMIN.", ex.getMessage());
            verify(userRepo, never()).saveAndFlush(any());
        }
    }

    @Test
    void updateUser_shouldRejectRevokingAdmin() {
        UserDTO input = new UserDTO();
        input.setUsername("jan");
        input.setRoles(List.of(new RoleDTO(2L, RoleType.ROLE_USER)));

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(roleRepo.findById(2L)).thenReturn(Optional.of(userRole));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.ADMIN)).thenReturn(true);
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(2L);

            ValidationException ex = assertThrows(ValidationException.class, () -> userService.updateUser(1L, input));
            assertEquals("Nie można zmienić roli ADMIN.", ex.getMessage());
            verify(userRepo, never()).saveAndFlush(any());
        }
    }

    @Test
    void updateUser_shouldRejectStrippingOwnAdmin_whenAdminUpdatesSelfWithoutRoles() {
        UserDTO input = new UserDTO();
        input.setUsername("jan");
        input.setRoles(List.of());

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.ADMIN)).thenReturn(true);
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            ValidationException ex = assertThrows(ValidationException.class, () -> userService.updateUser(1L, input));
            assertEquals("Nie można zmienić roli ADMIN.", ex.getMessage());
            verify(userRepo, never()).saveAndFlush(any());
        }
    }

    @Test
    void updateUser_shouldKeepExistingAdminRoles_whenAdminRoleSentBack() {
        existingUser.setRoles(Set.of(adminRole, userRole));
        UserDTO input = new UserDTO();
        input.setUsername("jan");
        input.setRoles(List.of(new RoleDTO(1L, RoleType.ROLE_ADMIN), new RoleDTO(3L, RoleType.ROLE_MASTER)));

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(roleRepo.findById(1L)).thenReturn(Optional.of(adminRole));
        when(roleRepo.findById(3L)).thenReturn(Optional.of(masterRole));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.ADMIN)).thenReturn(true);
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            userService.updateUser(1L, input);

            verify(userRepo).saveAndFlush(argThat(u -> u.getRoles().equals(Set.of(adminRole, userRole))));
        }
    }

    @Test
    void updateUser_shouldKeepOnlyMaster_whenBothBusinessRolesSent() {
        existingUser.setRoles(Set.of(userRole));
        UserDTO input = new UserDTO();
        input.setUsername("jan");
        input.setRoles(List.of(new RoleDTO(2L, RoleType.ROLE_USER), new RoleDTO(3L, RoleType.ROLE_MASTER)));
        input.setCompanyPin("482915");

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(roleRepo.findById(2L)).thenReturn(Optional.of(userRole));
        when(roleRepo.findById(3L)).thenReturn(Optional.of(masterRole));
        when(companyModeService.encodeCompanyPin("482915")).thenReturn("pinHash");

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.ADMIN)).thenReturn(true);

            userService.updateUser(1L, input);

            verify(userRepo).saveAndFlush(argThat(u ->
                    u.getRoles().equals(Set.of(masterRole)) && "pinHash".equals(u.getCompanyPinHash())));
        }
    }

    @Test
    void updateUser_shouldRejectEmptyRoles_whenTargetIsNotAdmin() {
        existingUser.setRoles(Set.of(userRole));
        UserDTO input = new UserDTO();
        input.setUsername("jan");
        input.setRoles(List.of());

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.ADMIN)).thenReturn(true);

            ValidationException ex = assertThrows(ValidationException.class, () -> userService.updateUser(1L, input));
            assertEquals("Użytkownik musi mieć rolę USER lub MASTER.", ex.getMessage());
            verify(userRepo, never()).saveAndFlush(any());
        }
    }

    @Test
    void updateUser_shouldRejectNullRoles_whenAdminEditsNonAdmin() {
        existingUser.setRoles(Set.of(userRole));
        UserDTO input = new UserDTO();
        input.setUsername("jan");
        input.setRoles(null);

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.ADMIN)).thenReturn(true);

            ValidationException ex = assertThrows(ValidationException.class, () -> userService.updateUser(1L, input));
            assertEquals(RoleAssignmentRules.BUSINESS_ROLE_REQUIRED, ex.getMessage());
            verify(userRepo, never()).saveAndFlush(any());
        }
    }

    @Test
    void updateUser_shouldIgnoreNullRoles_whenNotAdmin() {
        existingUser.setRoles(Set.of(userRole));
        UserDTO input = new UserDTO();
        input.setUsername("jan");
        input.setAvatar("avatar3.png");
        input.setRoles(null);

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser)).thenReturn(Optional.of(existingUser));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            userService.updateUser(1L, input);

            verify(userRepo).saveAndFlush(argThat(u -> u.getRoles().equals(Set.of(userRole))));
        }
    }

    @Test
    void getMyPreferences_shouldReturnLight_whenUserNeverChangedIt() {
        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));

        UserPreferencesDTO result = userService.getMyPreferences(1L);

        assertEquals(CardTheme.LIGHT, result.getCardTheme());
    }

    @Test
    void getMyPreferences_shouldThrowResourceNotFoundException_whenUserMissing() {
        when(userRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getMyPreferences(99L));
    }

    @Test
    void updateMyPreferences_shouldSaveDark() {
        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserPreferencesDTO result = userService.updateMyPreferences(1L, new UserPreferencesDTO(CardTheme.DARK));

        assertEquals(CardTheme.DARK, result.getCardTheme());
        assertEquals(CardTheme.DARK, existingUser.getCardTheme());
        verify(userRepo).save(argThat(u -> u.getId().equals(1L) && u.getCardTheme() == CardTheme.DARK));
        verifyNoInteractions(auditLogService);
    }

    @Test
    void updateMyPreferences_shouldNotAffectOtherUser() {
        User otherUser = User.builder().id(2L).username("anna").password("hash").roles(Set.of(userRole)).build();
        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepo.findOneById(2L)).thenReturn(Optional.of(otherUser));
        when(userRepo.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.updateMyPreferences(1L, new UserPreferencesDTO(CardTheme.DARK));
        UserPreferencesDTO otherResult = userService.getMyPreferences(2L);

        assertEquals(CardTheme.LIGHT, otherResult.getCardTheme());
        assertEquals(CardTheme.LIGHT, otherUser.getCardTheme());
        assertEquals(CardTheme.DARK, existingUser.getCardTheme());
        verify(userRepo, never()).save(otherUser);
    }

    @Test
    void updateMyPreferences_shouldThrowValidationException_whenCardThemeNull() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> userService.updateMyPreferences(1L, new UserPreferencesDTO()));

        assertEquals("Nie wybrano wyglądu karty.", ex.getMessage());
        assertEquals(CardTheme.LIGHT, existingUser.getCardTheme());
        verify(userRepo, never()).save(any());
    }

    @Test
    void updateMyPreferences_shouldThrowValidationException_whenBodyNull() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> userService.updateMyPreferences(1L, null));

        assertEquals("Nie wybrano wyglądu karty.", ex.getMessage());
        verify(userRepo, never()).save(any());
    }

    @Test
    void updateMyPreferences_shouldThrowResourceNotFoundException_whenUserMissing() {
        when(userRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> userService.updateMyPreferences(99L, new UserPreferencesDTO(CardTheme.DARK)));
        verify(userRepo, never()).save(any());
    }

    @Test
    void updateUser_shouldKeepCardTheme_whenUserChangesOwnAvatar() {
        existingUser.setRoles(Set.of(userRole));
        existingUser.setCardTheme(CardTheme.DARK);
        UserDTO input = new UserDTO();
        input.setUsername("jan");
        input.setAvatar("avatar3.png");
        input.setRoles(List.of(new RoleDTO(2L, RoleType.ROLE_USER)));

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser)).thenReturn(Optional.of(existingUser));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            userService.updateUser(1L, input);

            verify(userRepo).saveAndFlush(argThat(u ->
                    u.getCardTheme() == CardTheme.DARK &&
                    "avatar3.png".equals(u.getAvatar())
            ));
        }
    }

    @Test
    void updateUser_shouldKeepCardTheme_whenAdminEditsUser() {
        existingUser.setCardTheme(CardTheme.DARK);
        UserDTO input = new UserDTO();
        input.setUsername("janUpdated");
        input.setAvatar("avatar2.png");
        input.setRoles(List.of(new RoleDTO(1L, RoleType.ROLE_ADMIN)));

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser)).thenReturn(Optional.of(existingUser));
        when(roleRepo.findById(1L)).thenReturn(Optional.of(adminRole));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.ADMIN)).thenReturn(true);
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            userService.updateUser(1L, input);

            verify(userRepo).saveAndFlush(argThat(u ->
                    u.getCardTheme() == CardTheme.DARK &&
                    "janUpdated".equals(u.getUsername())
            ));
        }
    }

    private UserDTO ownAvatarInput(String avatar) {
        UserDTO input = new UserDTO();
        input.setUsername("jan");
        input.setAvatar(avatar);
        input.setRoles(List.of(new RoleDTO(2L, RoleType.ROLE_USER)));
        return input;
    }

    @Test
    void updateUser_shouldThrowConflictAndNotSave_whenAvatarTakenByOtherUser() {
        existingUser.setRoles(Set.of(userRole));
        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepo.existsByAvatarAndIdNot("avatar7.png", 1L)).thenReturn(true);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            ConflictException ex = assertThrows(ConflictException.class,
                    () -> userService.updateUser(1L, ownAvatarInput("avatar7.png")));
            assertEquals("Ten avatar jest już zajęty.", ex.getMessage());
            verify(userRepo, never()).saveAndFlush(any());
            verifyNoInteractions(auditLogService);
        }
    }

    @Test
    void updateUser_shouldSaveWithoutTakenCheck_whenAvatarUnchanged() {
        existingUser.setRoles(Set.of(userRole));
        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser)).thenReturn(Optional.of(existingUser));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            UserDTO result = userService.updateUser(1L, ownAvatarInput("avatar1.png"));

            assertEquals("avatar1.png", result.getAvatar());
            verify(userRepo).saveAndFlush(argThat(u -> "avatar1.png".equals(u.getAvatar())));
            verify(userRepo, never()).existsByAvatarAndIdNot(any(), any());
        }
    }

    @Test
    void updateUser_shouldSaveNewAvatar_whenAvatarFree() {
        existingUser.setRoles(Set.of(userRole));
        User saved = User.builder().id(1L).username("jan").password("hashedPass")
                .avatar("avatar7.png").roles(Set.of(userRole)).build();
        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser)).thenReturn(Optional.of(saved));
        when(userRepo.existsByAvatarAndIdNot("avatar7.png", 1L)).thenReturn(false);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            UserDTO result = userService.updateUser(1L, ownAvatarInput("avatar7.png"));

            assertEquals("avatar7.png", result.getAvatar());
            verify(userRepo).saveAndFlush(argThat(u -> "avatar7.png".equals(u.getAvatar())));
        }
    }

    @Test
    void updateUser_shouldThrowValidationException_whenAvatarOutsideCatalog() {
        existingUser.setRoles(Set.of(userRole));
        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            for (String avatar : List.of("avatar21.png", "../x", "avatar0.png", "", "AVATAR1.PNG")) {
                ValidationException ex = assertThrows(ValidationException.class,
                        () -> userService.updateUser(1L, ownAvatarInput(avatar)));
                assertEquals("Nieprawidłowy avatar.", ex.getMessage());
            }
            verify(userRepo, never()).saveAndFlush(any());
            verify(userRepo, never()).existsByAvatarAndIdNot(any(), any());
        }
    }

    @Test
    void updateUser_shouldThrowConflict_whenUniqueAvatarIndexViolatedOnSave() {
        existingUser.setRoles(Set.of(userRole));
        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepo.existsByAvatarAndIdNot("avatar7.png", 1L)).thenReturn(false);
        when(userRepo.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("could not execute statement",
                new SQLException("ERROR: duplicate key value violates unique constraint \"uq_users_avatar\"")));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            ConflictException ex = assertThrows(ConflictException.class,
                    () -> userService.updateUser(1L, ownAvatarInput("avatar7.png")));
            assertEquals("Ten avatar jest już zajęty.", ex.getMessage());
            verifyNoInteractions(auditLogService);
        }
    }

    @Test
    void updateUser_shouldNotTranslateToAvatarConflict_whenOtherConstraintViolated() {
        existingUser.setRoles(Set.of(userRole));
        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepo.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("could not execute statement",
                new SQLException("ERROR: duplicate key value violates unique constraint \"uk_username\"")));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            assertThrows(UpdateException.class, () -> userService.updateUser(1L, ownAvatarInput("avatar1.png")));
        }
    }

    @Test
    void updateUser_shouldKeepExistingAvatarWithoutChecks_whenCompanyEditsOtherUser() {
        existingUser.setRoles(Set.of(userRole));
        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser)).thenReturn(Optional.of(existingUser));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(2L);
            mockedSession.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            userService.updateUser(1L, ownAvatarInput("avatar21.png"));

            verify(userRepo).saveAndFlush(argThat(u -> "avatar1.png".equals(u.getAvatar())));
            verify(userRepo, never()).existsByAvatarAndIdNot(any(), any());
        }
    }

    @Test
    void updateUser_shouldKeepExistingAvatar_whenAvatarNotSent() {
        existingUser.setRoles(Set.of(userRole));
        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser)).thenReturn(Optional.of(existingUser));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            userService.updateUser(1L, ownAvatarInput(null));

            verify(userRepo).saveAndFlush(argThat(u -> "avatar1.png".equals(u.getAvatar())));
            verify(userRepo, never()).existsByAvatarAndIdNot(any(), any());
        }
    }
}

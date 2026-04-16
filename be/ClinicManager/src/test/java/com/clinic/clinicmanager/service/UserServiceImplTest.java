package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.UserDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.Role;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.service.impl.UserServiceImpl;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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

    @InjectMocks
    UserServiceImpl userService;

    private User existingUser;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        adminRole = Role.builder().id(1L).name(RoleType.ROLE_ADMIN).build();

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
    void getUserById_shouldReturnDTO_whenFound() {
        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));

        UserDTO result = userService.getUserById(1L);

        assertEquals(1L, result.getId());
    }

    @Test
    void getUserById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(userRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(99L));
    }

    @Test
    void getUserByEmployeeId_shouldReturnDTO_whenFound() {
        Employee emp = Employee.builder().id(5L).name("Anna").lastName("Nowak").build();
        User userWithEmployee = User.builder()
                .id(2L).username("anna").password("pass").avatar("avatar2.png")
                .roles(Set.of()).employee(emp).build();

        when(userRepo.findByEmployeeId(5L)).thenReturn(Optional.of(userWithEmployee));

        UserDTO result = userService.getUserByEmployeeId(5L);

        assertEquals(2L, result.getId());
    }

    @Test
    void getUserByEmployeeId_shouldThrowResourceNotFoundException_whenNotFound() {
        when(userRepo.findByEmployeeId(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserByEmployeeId(99L));
    }

    @Test
    void updateUser_shouldSaveAndReturnDTO_whenAdmin() {
        UserDTO input = new UserDTO();
        input.setUsername("janUpdated");
        input.setAvatar("avatar2.png");
        input.setRoles(List.of());

        User savedUser = User.builder()
                .id(1L).username("janUpdated").password("hashedPass")
                .avatar("avatar2.png").roles(Set.of()).employee(null).build();

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser)).thenReturn(Optional.of(savedUser));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasUserRole(RoleType.ROLE_ADMIN)).thenReturn(true);

            UserDTO result = userService.updateUser(1L, input);

            verify(userRepo).saveAndFlush(any(User.class));
            assertEquals("janUpdated", result.getUsername());
        }
    }

    @Test
    void updateUser_shouldPreserveRolesAndUsername_whenNotAdmin() {
        UserDTO input = new UserDTO();
        input.setUsername("hackerNewName");
        input.setAvatar("avatar3.png");
        input.setRoles(List.of());

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser)).thenReturn(Optional.of(existingUser));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasUserRole(RoleType.ROLE_ADMIN)).thenReturn(false);

            userService.updateUser(1L, input);

            verify(userRepo).saveAndFlush(argThat(u ->
                    "jan".equals(u.getUsername()) &&
                    u.getRoles().contains(adminRole)
            ));
        }
    }

    @Test
    void updateUser_shouldThrowConflictException_whenEmployeeAlreadyAssignedToAnotherUser() {
        Employee emp = Employee.builder().id(5L).name("Anna").lastName("Nowak").build();
        User otherUser = User.builder()
                .id(99L).username("other").password("pass").avatar("av.png")
                .roles(Set.of()).employee(emp).build();

        UserDTO input = new UserDTO();
        input.setRoles(List.of());
        com.clinic.clinicmanager.DTO.EmployeeSummaryDTO empDTO = new com.clinic.clinicmanager.DTO.EmployeeSummaryDTO();
        empDTO.setId(5L);
        input.setEmployee(empDTO);

        when(userRepo.findOneById(1L)).thenReturn(Optional.of(existingUser));
        when(userRepo.findByEmployeeId(5L)).thenReturn(Optional.of(otherUser));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasUserRole(RoleType.ROLE_ADMIN)).thenReturn(true);

            assertThrows(ConflictException.class, () -> userService.updateUser(1L, input));
        }
    }

    @Test
    void updateUser_shouldThrowResourceNotFoundException_whenUserNotFound() {
        when(userRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.updateUser(99L, new UserDTO()));
    }
}

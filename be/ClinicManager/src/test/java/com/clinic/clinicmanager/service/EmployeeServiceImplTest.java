package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.EmployeeDTO;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.constants.EmploymentType;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.repo.projection.EmployeeAccountRoleProjection;
import com.clinic.clinicmanager.service.impl.EmployeeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock EmployeeRepo employeeRepo;
    @Mock AuditLogService auditLogService;
    @Mock UserRepo userRepo;

    @InjectMocks
    EmployeeServiceImpl employeeService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
                .id(1L)
                .name("Anna")
                .lastName("Kowalska")
                .isDeleted(false)
                .employmentType(EmploymentType.FULL)
                .bonusPercent(10.0)
                .saleBonusPercent(5.0)
                .drawingColor("#dc2626")
                .build();
    }

    @Test
    void getAllEmployees_shouldReturnMappedList() {
        when(employeeRepo.findAllActive()).thenReturn(List.of(employee));

        List<EmployeeDTO> result = employeeService.getAllEmployees();

        assertEquals(1, result.size());
        assertEquals("Anna", result.getFirst().getName());
        assertEquals("Kowalska", result.getFirst().getLastName());
    }

    @Test
    void getAllEmployees_shouldReturnEmptyList_whenNoActiveEmployees() {
        when(employeeRepo.findAllActive()).thenReturn(List.of());

        List<EmployeeDTO> result = employeeService.getAllEmployees();

        assertTrue(result.isEmpty());
    }

    @Test
    void getEmployeeById_shouldReturnDTO_whenEmployeeExists() {
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));

        EmployeeDTO result = employeeService.getEmployeeById(1L);

        assertEquals(1L, result.getId());
        assertEquals("Anna", result.getName());
    }

    @Test
    void getEmployeeById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(employeeRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> employeeService.getEmployeeById(99L));
    }

    @Test
    void createEmployee_shouldSaveAndReturnDTO() {
        EmployeeDTO inputDTO = new EmployeeDTO();
        inputDTO.setName("Jan");
        inputDTO.setLastName("Nowak");
        inputDTO.setEmploymentType(EmploymentType.FULL);
        inputDTO.setBonusPercent(0.0);
        inputDTO.setSaleBonusPercent(0.0);

        Employee savedEmployee = Employee.builder()
                .id(2L).name("Jan").lastName("Nowak")
                .isDeleted(false).employmentType(EmploymentType.FULL)
                .bonusPercent(0.0).saleBonusPercent(0.0).build();

        when(employeeRepo.save(any(Employee.class))).thenReturn(savedEmployee);

        EmployeeDTO result = employeeService.createEmployee(inputDTO);

        assertEquals(2L, result.getId());
        assertEquals("Jan", result.getName());
        verify(auditLogService).logCreate(eq("Employee"), eq(2L), anyString(), any());
    }

    @Test
    void createEmployee_shouldReturnNull_whenInputIsNull() {
        EmployeeDTO result = employeeService.createEmployee(null);

        assertNull(result);
        verifyNoInteractions(employeeRepo);
    }

    @Test
    void updateEmployee_shouldUpdateAndReturnDTO() {
        EmployeeDTO updateDTO = new EmployeeDTO();
        updateDTO.setName("Anna");
        updateDTO.setLastName("Nowak");
        updateDTO.setEmploymentType(EmploymentType.FULL);
        updateDTO.setBonusPercent(15.0);
        updateDTO.setSaleBonusPercent(5.0);

        Employee updatedEmployee = Employee.builder()
                .id(1L).name("Anna").lastName("Nowak")
                .isDeleted(false).employmentType(EmploymentType.FULL)
                .bonusPercent(15.0).saleBonusPercent(5.0).build();

        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepo.save(any(Employee.class))).thenReturn(updatedEmployee);

        EmployeeDTO result = employeeService.updateEmployee(1L, updateDTO);

        assertEquals(1L, result.getId());
        assertEquals("Nowak", result.getLastName());
        verify(auditLogService).logUpdate(eq("Employee"), eq(1L), anyString(), any(), any());
    }

    @Test
    void updateEmployee_shouldThrowResourceNotFoundException_whenNotFound() {
        EmployeeDTO updateDTO = new EmployeeDTO();
        when(employeeRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> employeeService.updateEmployee(99L, updateDTO));
    }

    @Test
    void deleteEmployeeById_shouldSoftDelete_whenEmployeeExists() {
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));

        employeeService.deleteEmployeeById(1L);

        assertTrue(employee.getIsDeleted());
        verify(employeeRepo).save(employee);
        verify(auditLogService).logDelete(eq("Employee"), eq(1L), anyString(), any());
    }

    @Test
    void deleteEmployeeById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(employeeRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> employeeService.deleteEmployeeById(99L));
    }

    @Test
    void deleteEmployeeById_shouldThrowDeletionException_whenAlreadySoftDeleted() {
        employee.softDelete();
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));

        assertThrows(DeletionException.class, () -> employeeService.deleteEmployeeById(1L));
        verify(employeeRepo, never()).save(any());
    }

    private String createdColor(List<String> usedColors) {
        when(employeeRepo.findAllDrawingColors()).thenReturn(usedColors);
        when(employeeRepo.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });
        EmployeeDTO input = new EmployeeDTO();
        input.setName("Ewa");
        input.setLastName("Lis");

        employeeService.createEmployee(input);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepo).save(captor.capture());
        return captor.getValue().getDrawingColor();
    }

    @Test
    void createEmployee_assignsFirstPaletteColor_whenNoEmployees() {
        assertEquals("#2563eb", createdColor(List.of()));
    }

    @Test
    void createEmployee_assignsFirstFreeColor_inPaletteOrder() {
        assertEquals("#16a34a", createdColor(List.of("#2563eb", "#dc2626", "#9333ea")));
    }

    @Test
    void createEmployee_treatsColorsOfDeletedEmployeesAsTaken() {
        assertEquals("#dc2626", createdColor(List.of("#2563eb")));
    }

    @Test
    void createEmployee_allColorsTaken_assignsLeastUsed() {
        List<String> used = new ArrayList<>(List.of(
                "#2563eb", "#dc2626", "#16a34a", "#9333ea", "#ea580c",
                "#0d9488", "#db2777", "#92400e", "#4f46e5", "#65a30d"));
        used.addAll(List.of("#2563eb", "#dc2626", "#16a34a", "#9333ea", "#0d9488"));

        assertEquals("#ea580c", createdColor(used));
    }

    @Test
    void createEmployee_allColorsUsedEqually_assignsFirstInPalette() {
        List<String> used = new ArrayList<>();
        Collections.addAll(used, "#65a30d", "#4f46e5", "#92400e", "#db2777", "#0d9488",
                "#ea580c", "#9333ea", "#16a34a", "#dc2626", "#2563eb");

        assertEquals("#2563eb", createdColor(used));
    }

    @Test
    void updateEmployee_keepsExistingDrawingColor() {
        EmployeeDTO updateDTO = new EmployeeDTO();
        updateDTO.setName("Anna");
        updateDTO.setLastName("Nowak");
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepo.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        employeeService.updateEmployee(1L, updateDTO);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepo).save(captor.capture());
        assertEquals("#dc2626", captor.getValue().getDrawingColor());
        verify(employeeRepo, never()).findAllDrawingColors();
    }

    private static EmployeeAccountRoleProjection accountRole(Long employeeId, RoleType roleName) {
        return accountRole(employeeId, employeeId * 100, null, roleName);
    }

    private static EmployeeAccountRoleProjection accountRole(Long employeeId, Long userId, String avatar,
                                                             RoleType roleName) {
        return new EmployeeAccountRoleProjection() {
            @Override
            public Long getEmployeeId() {
                return employeeId;
            }

            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public String getAvatar() {
                return avatar;
            }

            @Override
            public RoleType getRoleName() {
                return roleName;
            }
        };
    }

    private String accountRoleFromGetAll(List<EmployeeAccountRoleProjection> rows) {
        when(employeeRepo.findAllActive()).thenReturn(List.of(employee));
        when(userRepo.findAccountRolesByEmployeeIdIn(List.of(1L))).thenReturn(rows);

        List<EmployeeDTO> result = employeeService.getAllEmployees();

        assertEquals(1, result.size());
        return result.getFirst().getAccountRole();
    }

    @Test
    void getAllEmployees_returnsUserRole_whenAccountHasOnlyUserRole() {
        assertEquals("ROLE_USER", accountRoleFromGetAll(List.of(accountRole(1L, RoleType.ROLE_USER))));
    }

    @Test
    void getAllEmployees_returnsMasterRole_whenAccountHasMasterAndUserRoles() {
        assertEquals("ROLE_MASTER", accountRoleFromGetAll(List.of(
                accountRole(1L, RoleType.ROLE_USER),
                accountRole(1L, RoleType.ROLE_MASTER))));
    }

    @Test
    void getAllEmployees_returnsAdminRole_whenAccountHasAdminRole() {
        assertEquals("ROLE_ADMIN", accountRoleFromGetAll(List.of(accountRole(1L, RoleType.ROLE_ADMIN))));
    }

    @Test
    void getAllEmployees_returnsNullRole_whenEmployeeHasNoAccount() {
        assertNull(accountRoleFromGetAll(List.of()));
    }

    @Test
    void getAllEmployees_returnsHighestRole_whenTwoAccountsLinkedToEmployee() {
        assertEquals("ROLE_MASTER", accountRoleFromGetAll(List.of(
                accountRole(1L, RoleType.ROLE_MASTER),
                accountRole(1L, RoleType.ROLE_USER))));
    }

    @Test
    void getAllEmployees_resolvesRolesWithSingleQuery_andKeepsOrder() {
        Employee second = Employee.builder().id(2L).name("Ewa").lastName("Lis").isDeleted(false).build();
        Employee third = Employee.builder().id(3L).name("Ola").lastName("Kot").isDeleted(false).build();
        when(employeeRepo.findAllActive()).thenReturn(List.of(employee, second, third));
        when(userRepo.findAccountRolesByEmployeeIdIn(List.of(1L, 2L, 3L))).thenReturn(List.of(
                accountRole(3L, RoleType.ROLE_ADMIN),
                accountRole(1L, RoleType.ROLE_USER)));

        List<EmployeeDTO> result = employeeService.getAllEmployees();

        assertEquals(List.of(1L, 2L, 3L), result.stream().map(EmployeeDTO::getId).toList());
        assertEquals("ROLE_USER", result.get(0).getAccountRole());
        assertNull(result.get(1).getAccountRole());
        assertEquals("ROLE_ADMIN", result.get(2).getAccountRole());
        verify(userRepo, times(1)).findAccountRolesByEmployeeIdIn(any());
        verifyNoMoreInteractions(userRepo);
    }

    @Test
    void getAllEmployees_skipsRoleQuery_whenNoActiveEmployees() {
        when(employeeRepo.findAllActive()).thenReturn(List.of());

        assertTrue(employeeService.getAllEmployees().isEmpty());
        verifyNoInteractions(userRepo);
    }

    @Test
    void getEmployeeById_returnsAccountRole() {
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(userRepo.findAccountRolesByEmployeeIdIn(List.of(1L))).thenReturn(List.of(
                accountRole(1L, RoleType.ROLE_USER),
                accountRole(1L, RoleType.ROLE_ADMIN)));

        EmployeeDTO result = employeeService.getEmployeeById(1L);

        assertEquals(1L, result.getId());
        assertEquals("ROLE_ADMIN", result.getAccountRole());
    }

    @Test
    void getEmployeeById_returnsNullRole_whenEmployeeHasNoAccount() {
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(userRepo.findAccountRolesByEmployeeIdIn(List.of(1L))).thenReturn(List.of());

        assertNull(employeeService.getEmployeeById(1L).getAccountRole());
    }

    @Test
    void getAllEmployees_returnsAvatarOfLinkedAccount_andNullForEmployeeWithoutAccount() {
        Employee second = Employee.builder().id(2L).name("Ewa").lastName("Lis").isDeleted(false).build();
        when(employeeRepo.findAllActive()).thenReturn(List.of(employee, second));
        when(userRepo.findAccountRolesByEmployeeIdIn(List.of(1L, 2L))).thenReturn(List.of(
                accountRole(1L, 10L, "avatar3", RoleType.ROLE_USER)));

        List<EmployeeDTO> result = employeeService.getAllEmployees();

        assertEquals("avatar3", result.get(0).getAvatar());
        assertEquals("ROLE_USER", result.get(0).getAccountRole());
        assertNull(result.get(1).getAvatar());
        assertNull(result.get(1).getAccountRole());
        verify(userRepo, times(1)).findAccountRolesByEmployeeIdIn(any());
        verifyNoMoreInteractions(userRepo);
    }

    @Test
    void getAllEmployees_returnsAvatarOfHighestRoleAccount_whenEmployeeHasTwoAccounts() {
        assertHighestAccountAvatar(List.of(
                accountRole(1L, 10L, "avatarA", RoleType.ROLE_USER),
                accountRole(1L, 20L, "avatarB", RoleType.ROLE_ADMIN)));
        assertHighestAccountAvatar(List.of(
                accountRole(1L, 20L, "avatarB", RoleType.ROLE_ADMIN),
                accountRole(1L, 10L, "avatarA", RoleType.ROLE_USER)));
    }

    private void assertHighestAccountAvatar(List<EmployeeAccountRoleProjection> rows) {
        when(employeeRepo.findAllActive()).thenReturn(List.of(employee));
        when(userRepo.findAccountRolesByEmployeeIdIn(List.of(1L))).thenReturn(rows);

        EmployeeDTO result = employeeService.getAllEmployees().getFirst();

        assertEquals("ROLE_ADMIN", result.getAccountRole());
        assertEquals("avatarB", result.getAvatar());
    }

    @Test
    void getAllEmployees_usesHighestRoleOfSingleAccount_whenAccountHasManyRoles() {
        when(employeeRepo.findAllActive()).thenReturn(List.of(employee));
        when(userRepo.findAccountRolesByEmployeeIdIn(List.of(1L))).thenReturn(List.of(
                accountRole(1L, 10L, "avatarA", RoleType.ROLE_USER),
                accountRole(1L, 20L, "avatarB", RoleType.ROLE_USER),
                accountRole(1L, 20L, "avatarB", RoleType.ROLE_MASTER)));

        EmployeeDTO result = employeeService.getAllEmployees().getFirst();

        assertEquals("ROLE_MASTER", result.getAccountRole());
        assertEquals("avatarB", result.getAvatar());
    }

    @Test
    void getAllEmployees_prefersNonEmptyAvatar_whenAccountsTieOnHighestRole() {
        when(employeeRepo.findAllActive()).thenReturn(List.of(employee));
        when(userRepo.findAccountRolesByEmployeeIdIn(List.of(1L))).thenReturn(List.of(
                accountRole(1L, 10L, null, RoleType.ROLE_MASTER),
                accountRole(1L, 20L, "avatar5", RoleType.ROLE_MASTER),
                accountRole(1L, 30L, "avatar9", RoleType.ROLE_USER)));

        EmployeeDTO result = employeeService.getAllEmployees().getFirst();

        assertEquals("ROLE_MASTER", result.getAccountRole());
        assertEquals("avatar5", result.getAvatar());
    }

    @Test
    void getAllEmployees_returnsNullAvatar_whenHighestRoleAccountHasNoAvatar() {
        when(employeeRepo.findAllActive()).thenReturn(List.of(employee));
        when(userRepo.findAccountRolesByEmployeeIdIn(List.of(1L))).thenReturn(List.of(
                accountRole(1L, 10L, "avatarA", RoleType.ROLE_USER),
                accountRole(1L, 20L, null, RoleType.ROLE_ADMIN)));

        EmployeeDTO result = employeeService.getAllEmployees().getFirst();

        assertEquals("ROLE_ADMIN", result.getAccountRole());
        assertNull(result.getAvatar());
    }

    @Test
    void getEmployeeById_returnsAvatar() {
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(userRepo.findAccountRolesByEmployeeIdIn(List.of(1L))).thenReturn(List.of(
                accountRole(1L, 10L, "avatar3", RoleType.ROLE_MASTER)));

        EmployeeDTO result = employeeService.getEmployeeById(1L);

        assertEquals("avatar3", result.getAvatar());
        assertEquals("ROLE_MASTER", result.getAccountRole());
    }

    @Test
    void getEmployeeById_returnsNullAvatar_whenEmployeeHasNoAccount() {
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(userRepo.findAccountRolesByEmployeeIdIn(List.of(1L))).thenReturn(List.of());

        assertNull(employeeService.getEmployeeById(1L).getAvatar());
    }

    @Test
    void updateEmployee_ignoresAvatarFromInput() {
        EmployeeDTO updateDTO = new EmployeeDTO();
        updateDTO.setName("Anna");
        updateDTO.setLastName("Nowak");
        updateDTO.setAvatar("avatar3");
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepo.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeeDTO result = employeeService.updateEmployee(1L, updateDTO);

        assertNull(result.getAvatar());
        verifyNoInteractions(userRepo);
        verify(auditLogService).logUpdate(eq("Employee"), eq(1L), anyString(),
                argThat(old -> old instanceof EmployeeDTO d && d.getAvatar() == null),
                argThat(updated -> updated instanceof EmployeeDTO d && d.getAvatar() == null));
    }

    @Test
    void createEmployee_ignoresAccountRoleFromInput() {
        EmployeeDTO input = new EmployeeDTO();
        input.setName("Jan");
        input.setLastName("Nowak");
        input.setEmploymentType(EmploymentType.FULL);
        input.setAccountRole("ROLE_ADMIN");
        when(employeeRepo.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });

        EmployeeDTO result = employeeService.createEmployee(input);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepo).save(captor.capture());
        Employee saved = captor.getValue();
        assertEquals("Jan", saved.getName());
        assertEquals("Nowak", saved.getLastName());
        assertEquals(EmploymentType.FULL, saved.getEmploymentType());
        assertFalse(saved.getIsDeleted());
        assertEquals(7L, result.getId());
        assertNull(result.getAccountRole());
        verifyNoInteractions(userRepo);
    }

    @Test
    void updateEmployee_ignoresAccountRoleFromInput() {
        EmployeeDTO input = new EmployeeDTO();
        input.setName("Anna");
        input.setLastName("Nowak");
        input.setAccountRole("ROLE_ADMIN");
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepo.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeeDTO result = employeeService.updateEmployee(1L, input);

        assertEquals("Nowak", result.getLastName());
        assertNull(result.getAccountRole());
        verifyNoInteractions(userRepo);
    }
}

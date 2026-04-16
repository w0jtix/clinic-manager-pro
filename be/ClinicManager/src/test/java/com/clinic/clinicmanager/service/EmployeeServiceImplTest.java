package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.EmployeeDTO;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.constants.EmploymentType;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.service.impl.EmployeeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock EmployeeRepo employeeRepo;
    @Mock AuditLogService auditLogService;

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

}

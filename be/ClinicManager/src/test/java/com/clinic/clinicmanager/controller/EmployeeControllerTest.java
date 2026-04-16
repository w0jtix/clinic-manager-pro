package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.EmployeeDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.constants.EmploymentType;
import com.clinic.clinicmanager.service.EmployeeService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployeeController.class)
@Import(WebSecurityConfig.class)
class EmployeeControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    EmployeeService employeeService;

    @MockBean
    UserDetailsServiceImpl userDetailsService;

    @MockBean
    AuthEntryPointJwt authEntryPointJwt;

    @MockBean
    JwtUtils jwtUtils;

    @MockBean
    TokenBlacklistService tokenBlacklistService;

    private EmployeeDTO buildEmployeeDTO(Long id) {
        EmployeeDTO dto = new EmployeeDTO();
        dto.setId(id);
        dto.setName("Jan");
        dto.setLastName("Nowak");
        dto.setEmploymentType(EmploymentType.FULL);
        dto.setBonusPercent(10.0);
        dto.setSaleBonusPercent(5.0);
        dto.setIsDeleted(false);
        return dto;
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAllEmployees_shouldReturn200_whenEmployeesExist() throws Exception {
        List<EmployeeDTO> employees = List.of(buildEmployeeDTO(1L), buildEmployeeDTO(2L));
        when(employeeService.getAllEmployees()).thenReturn(employees);

        mockMvc.perform(get("/api/employee/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Jan"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getAllEmployees_shouldReturn204_whenNoEmployees() throws Exception {
        when(employeeService.getAllEmployees()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/employee/all"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getEmployeeById_shouldReturn200WithBody_whenFound() throws Exception {
        EmployeeDTO employee = buildEmployeeDTO(1L);
        when(employeeService.getEmployeeById(1L)).thenReturn(employee);

        mockMvc.perform(get("/api/employee/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Jan"))
                .andExpect(jsonPath("$.bonusPercent").value(10.0));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getEmployeeById_shouldReturn404_whenNotFound() throws Exception {
        when(employeeService.getEmployeeById(99L))
                .thenThrow(new ResourceNotFoundException("Employee not found with ID: 99"));

        mockMvc.perform(get("/api/employee/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createEmployee_shouldReturn201_whenValid() throws Exception {
        EmployeeDTO inputDTO = buildEmployeeDTO(null);
        EmployeeDTO savedDTO = buildEmployeeDTO(1L);
        when(employeeService.createEmployee(any(EmployeeDTO.class))).thenReturn(savedDTO);

        mockMvc.perform(post("/api/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Jan"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createEmployee_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/api/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildEmployeeDTO(null))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createEmployee_shouldReturn400_whenBonusPercentIsNegative() throws Exception {
        EmployeeDTO inputDTO = buildEmployeeDTO(null);
        inputDTO.setBonusPercent(-10.0);

        mockMvc.perform(post("/api/employee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDTO)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateEmployee_shouldReturn200_whenAdmin() throws Exception {
        EmployeeDTO inputDTO = buildEmployeeDTO(null);
        EmployeeDTO savedDTO = buildEmployeeDTO(1L);
        when(employeeService.updateEmployee(eq(1L), any(EmployeeDTO.class))).thenReturn(savedDTO);

        mockMvc.perform(put("/api/employee/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateEmployee_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(put("/api/employee/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildEmployeeDTO(null))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateEmployee_shouldReturn400_whenBonusPercentIsNegative() throws Exception {
        EmployeeDTO inputDTO = buildEmployeeDTO(null);
        inputDTO.setBonusPercent(-10.0);

        mockMvc.perform(put("/api/employee/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDTO)))
                .andExpect(status().isBadRequest());
    }
}

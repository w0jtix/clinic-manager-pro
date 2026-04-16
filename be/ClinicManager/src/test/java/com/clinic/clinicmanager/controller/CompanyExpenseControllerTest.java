package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.CompanyExpenseDTO;
import com.clinic.clinicmanager.DTO.request.CompanyExpenseFilterDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.constants.ExpenseCategory;
import com.clinic.clinicmanager.service.CompanyExpenseService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CompanyExpenseController.class)
@Import(WebSecurityConfig.class)
class CompanyExpenseControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean CompanyExpenseService companyExpenseService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private CompanyExpenseDTO buildExpenseDTO(Long id) {
        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setId(id);
        return dto;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getExpenses_shouldReturn200_whenAdmin() throws Exception {
        Page<CompanyExpenseDTO> page = new PageImpl<>(List.of(buildExpenseDTO(1L)));
        when(companyExpenseService.getExpenses(any(), eq(0), eq(30))).thenReturn(page);

        mockMvc.perform(post("/api/company-expenses/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CompanyExpenseFilterDTO())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getExpenses_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/api/company-expenses/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CompanyExpenseFilterDTO())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getExpenseById_shouldReturn200_whenAdmin() throws Exception {
        when(companyExpenseService.getExpenseById(1L)).thenReturn(buildExpenseDTO(1L));

        mockMvc.perform(get("/api/company-expenses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getExpenseById_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/company-expenses/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getExpenseById_shouldReturn404_whenNotFound() throws Exception {
        when(companyExpenseService.getExpenseById(99L))
                .thenThrow(new ResourceNotFoundException("Expense not found with ID: 99"));

        mockMvc.perform(get("/api/company-expenses/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getLatestExpenseByCategory_shouldReturn200_whenFound() throws Exception {
        when(companyExpenseService.getLatestExpenseByCategory(ExpenseCategory.RENT))
                .thenReturn(Optional.of(buildExpenseDTO(1L)));

        mockMvc.perform(get("/api/company-expenses/latest")
                        .param("category", "RENT"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getLatestExpenseByCategory_shouldReturn204_whenNotFound() throws Exception {
        when(companyExpenseService.getLatestExpenseByCategory(any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/company-expenses/latest")
                        .param("category", "RENT"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getLatestExpenseByCategory_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/company-expenses/latest")
                        .param("category", "RENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createExpense_shouldReturn201_whenAdmin() throws Exception {
        when(companyExpenseService.createExpense(any())).thenReturn(buildExpenseDTO(1L));

        mockMvc.perform(post("/api/company-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildExpenseDTO(null))))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createExpense_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/api/company-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildExpenseDTO(null))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateExpense_shouldReturn200_whenAdmin() throws Exception {
        when(companyExpenseService.updateExpense(eq(1L), any())).thenReturn(buildExpenseDTO(1L));

        mockMvc.perform(put("/api/company-expenses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildExpenseDTO(null))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateExpense_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(put("/api/company-expenses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildExpenseDTO(null))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteExpense_shouldReturn204_whenAdmin() throws Exception {
        mockMvc.perform(delete("/api/company-expenses/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteExpense_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(delete("/api/company-expenses/1"))
                .andExpect(status().isForbidden());
    }
}

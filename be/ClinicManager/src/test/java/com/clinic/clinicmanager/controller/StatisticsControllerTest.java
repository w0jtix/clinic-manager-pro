package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.CompanyFinancialSummaryDTO;
import com.clinic.clinicmanager.DTO.CompanyRevenueDTO;
import com.clinic.clinicmanager.DTO.CompanyStatsDTO;
import com.clinic.clinicmanager.DTO.EmployeeBonusDTO;
import com.clinic.clinicmanager.DTO.EmployeeRevenueDTO;
import com.clinic.clinicmanager.DTO.EmployeeStatsDTO;
import com.clinic.clinicmanager.DTO.request.EmployeeBonusFilterDTO;
import com.clinic.clinicmanager.DTO.request.EmployeeRevenueFilterDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.service.CompanyStatsService;
import com.clinic.clinicmanager.service.StatisticsService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StatisticsController.class)
@Import(WebSecurityConfig.class)
class StatisticsControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean StatisticsService statisticsService;
    @MockBean CompanyStatsService companyStatsService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getEmployeeRevenue_shouldReturn200_whenAdmin() throws Exception {
        when(statisticsService.getEmployeeRevenue(any())).thenReturn(new EmployeeRevenueDTO());

        mockMvc.perform(post("/api/statistics/employee-revenue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeRevenueFilterDTO())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getEmployeeRevenue_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/api/statistics/employee-revenue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeRevenueFilterDTO())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getEmployeeStats_shouldReturn200_whenAdmin() throws Exception {
        when(statisticsService.getEmployeeStats(any())).thenReturn(List.of(new EmployeeStatsDTO()));

        mockMvc.perform(post("/api/statistics/employee-stats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeRevenueFilterDTO())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getEmployeeStats_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/api/statistics/employee-stats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeRevenueFilterDTO())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getEmployeeBonus_shouldReturn200_whenAdmin() throws Exception {
        when(statisticsService.getEmployeeBonus(any())).thenReturn(new EmployeeBonusDTO());

        mockMvc.perform(post("/api/statistics/employee-services-bonus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeBonusFilterDTO())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getEmployeeBonus_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/api/statistics/employee-services-bonus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeBonusFilterDTO())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCompanySummary_shouldReturn200_whenAdmin() throws Exception {
        when(companyStatsService.getFinancialSummary(any())).thenReturn(new CompanyFinancialSummaryDTO());

        mockMvc.perform(post("/api/statistics/company-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeRevenueFilterDTO())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getCompanySummary_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/api/statistics/company-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeRevenueFilterDTO())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCompanyStats_shouldReturn200_whenAdmin() throws Exception {
        when(companyStatsService.getCompanyStats(any())).thenReturn(new CompanyStatsDTO());

        mockMvc.perform(post("/api/statistics/company-stats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeRevenueFilterDTO())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getCompanyStats_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/api/statistics/company-stats")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeRevenueFilterDTO())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCompanyRevenue_shouldReturn200_whenAdmin() throws Exception {
        when(companyStatsService.getCompanyRevenueChart(any())).thenReturn(new CompanyRevenueDTO());

        mockMvc.perform(post("/api/statistics/company-revenue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeRevenueFilterDTO())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getCompanyRevenue_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/api/statistics/company-revenue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new EmployeeRevenueFilterDTO())))
                .andExpect(status().isForbidden());
    }
}

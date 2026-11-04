package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.CompanyExpenseDTO;
import com.clinic.clinicmanager.DTO.ExpenseSellerDTO;
import com.clinic.clinicmanager.DTO.KsefSellerDTO;
import com.clinic.clinicmanager.DTO.KsefSellerSummaryDTO;
import com.clinic.clinicmanager.DTO.KsefSpendChartDTO;
import com.clinic.clinicmanager.DTO.KsefSpendChartPointDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.KsefSeller;
import com.clinic.clinicmanager.service.KsefSellerService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(KsefSellerController.class)
@Import(WebSecurityConfig.class)
class KsefSellerControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean KsefSellerService sellerService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private KsefSellerDTO sellerDTO() {
        return new KsefSellerDTO(KsefSeller.builder()
                .id(3L).nip("5261040828").name("Dostawca").address("ul. Prosta 1").autoMarkProducts(false).build());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getByNip_shouldReturn200_whenFound() throws Exception {
        when(sellerService.getSellerByNip("5261040828")).thenReturn(sellerDTO());

        mockMvc.perform(get("/api/ksef/sellers/by-nip/5261040828"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.nip").value("5261040828"))
                .andExpect(jsonPath("$.name").value("Dostawca"))
                .andExpect(jsonPath("$.address").value("ul. Prosta 1"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getByNip_shouldReturn404_whenNotFound() throws Exception {
        when(sellerService.getSellerByNip(anyString()))
                .thenThrow(new ResourceNotFoundException("KSeF seller not found with NIP: 7740001454"));

        mockMvc.perform(get("/api/ksef/sellers/by-nip/7740001454"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getByNip_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/ksef/sellers/by-nip/5261040828"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSummary_shouldExposeManualExpenseFields() throws Exception {
        when(sellerService.getSummary(3L)).thenReturn(new KsefSellerSummaryDTO(
                2L, new BigDecimal("200.00"), 0L, 0L, 0L, List.of(), 3L, new BigDecimal("450.50")));

        mockMvc.perform(get("/api/ksef/sellers/3/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceCount").value(2))
                .andExpect(jsonPath("$.manualExpenseCount").value(3))
                .andExpect(jsonPath("$.manualExpenseTotal").value(450.50));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSpendChart_shouldExposeManualExpenseCountPerPoint() throws Exception {
        when(sellerService.getSpendChart(3L, 2026)).thenReturn(new KsefSpendChartDTO(2025, 2026,
                List.of(new KsefSpendChartPointDTO(1, new BigDecimal("200.00"), 1L, 2L))));

        mockMvc.perform(get("/api/ksef/sellers/3/spend-chart").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].gross").value(200.00))
                .andExpect(jsonPath("$.points[0].invoiceCount").value(1))
                .andExpect(jsonPath("$.points[0].manualExpenseCount").value(2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getManualExpenses_shouldReturn200WithPage() throws Exception {
        CompanyExpenseDTO expense = new CompanyExpenseDTO();
        expense.setId(7L);
        expense.setSellerId(3L);
        expense.setSeller(new ExpenseSellerDTO(3L, "5261040828", "Dostawca", "ul. Prosta 1"));
        when(sellerService.getManualExpenses(3L, 1, 5))
                .thenReturn(new PageImpl<>(List.of(expense), PageRequest.of(1, 5), 6));

        mockMvc.perform(get("/api/ksef/sellers/3/expenses").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(7))
                .andExpect(jsonPath("$.content[0].seller.nip").value("5261040828"))
                .andExpect(jsonPath("$.totalElements").value(6));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getManualExpenses_shouldReturn200_whenEmpty() throws Exception {
        when(sellerService.getManualExpenses(3L, 0, 20))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/ksef/sellers/3/expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getManualExpenses_shouldReturn404_whenSellerMissing() throws Exception {
        when(sellerService.getManualExpenses(99L, 0, 20))
                .thenThrow(new ResourceNotFoundException("KSeF seller not found: 99"));

        mockMvc.perform(get("/api/ksef/sellers/99/expenses"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getManualExpenses_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/ksef/sellers/3/expenses"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {"MASTER", "COMPANY"})
    void getByNip_shouldReturn200_whenCompanyMode() throws Exception {
        when(sellerService.getSellerByNip("5261040828")).thenReturn(sellerDTO());

        mockMvc.perform(get("/api/ksef/sellers/by-nip/5261040828"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MASTER")
    void getByNip_shouldReturn403_whenMasterWithoutCompanyMode() throws Exception {
        mockMvc.perform(get("/api/ksef/sellers/by-nip/5261040828"))
                .andExpect(status().isForbidden());
    }
}

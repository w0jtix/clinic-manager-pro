package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.CashLedgerDTO;
import com.clinic.clinicmanager.DTO.request.CashLedgerFilterDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.service.CashLedgerService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CashLedgerController.class)
@Import(WebSecurityConfig.class)
class CashLedgerControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean CashLedgerService cashLedgerService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCashLedgers_shouldReturn200_whenAdmin() throws Exception {
        when(cashLedgerService.getCashLedgers(any(), anyInt(), anyInt()))
                .thenReturn(new PageImpl<>(List.of(new CashLedgerDTO())));

        mockMvc.perform(post("/api/cash-ledger/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CashLedgerFilterDTO())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getCashLedgers_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/api/cash-ledger/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CashLedgerFilterDTO())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCashLedgerById_shouldReturn200_whenAdmin() throws Exception {
        when(cashLedgerService.getCashLedgerById(1L)).thenReturn(new CashLedgerDTO());

        mockMvc.perform(get("/api/cash-ledger/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getCashLedgerById_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/cash-ledger/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateCashLedger_shouldReturn200_whenAdmin() throws Exception {
        when(cashLedgerService.updateCashLedger(eq(1L), any())).thenReturn(new CashLedgerDTO());

        mockMvc.perform(put("/api/cash-ledger/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CashLedgerDTO())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateCashLedger_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(put("/api/cash-ledger/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CashLedgerDTO())))
                .andExpect(status().isForbidden());
    }
}

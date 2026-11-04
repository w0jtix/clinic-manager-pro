package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.DTO.ClientDebtDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.constants.DebtType;
import com.clinic.clinicmanager.model.constants.PaymentStatus;
import com.clinic.clinicmanager.service.ClientDebtService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientDebtController.class)
@Import(WebSecurityConfig.class)
class ClientDebtControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ClientDebtService debtService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private ClientDebtDTO buildDebtDTO(Long id, String reason) {
        ClientDTO client = new ClientDTO();
        client.setId(1L);
        client.setFirstName("Jan");
        client.setLastName("Kowalski");

        ClientDebtDTO dto = new ClientDebtDTO();
        dto.setId(id);
        dto.setClient(client);
        dto.setType(DebtType.ABSENCE_FEE);
        dto.setValue(BigDecimal.valueOf(100));
        dto.setPaymentStatus(PaymentStatus.UNPAID);
        dto.setReason(reason);
        return dto;
    }

    @Test
    @WithMockUser(roles = "USER")
    void getDebtById_shouldReturnReasonInJson() throws Exception {
        when(debtService.getDebtById(1L)).thenReturn(buildDebtDTO(1L, "Brak gotówki"));

        mockMvc.perform(get("/api/client_debts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.reason").value("Brak gotówki"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getDebtById_shouldReturnNullReason_whenNotSet() throws Exception {
        when(debtService.getDebtById(1L)).thenReturn(buildDebtDTO(1L, null));

        mockMvc.perform(get("/api/client_debts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value(nullValue()));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getDebts_shouldReturnReasonInList() throws Exception {
        when(debtService.getDebts(any())).thenReturn(List.of(buildDebtDTO(1L, "Brak gotówki")));

        mockMvc.perform(post("/api/client_debts/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("Brak gotówki"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createDebt_shouldPassReasonToServiceAndReturnIt() throws Exception {
        when(debtService.createDebt(any())).thenReturn(buildDebtDTO(1L, "Brak gotówki"));

        mockMvc.perform(post("/api/client_debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDebtDTO(null, "  Brak gotówki  "))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value("Brak gotówki"));

        verify(debtService).createDebt(argThat(d -> "  Brak gotówki  ".equals(d.getReason())));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createDebt_shouldReturn400_whenReasonTooLong() throws Exception {
        when(debtService.createDebt(any()))
                .thenThrow(new ValidationException("Przyczyna może mieć maksymalnie 500 znaków."));

        String body = mockMvc.perform(post("/api/client_debts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDebtDTO(null, "a".repeat(501)))))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);

        assertEquals("Przyczyna może mieć maksymalnie 500 znaków.", body);
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateDebt_shouldReturnUpdatedReason() throws Exception {
        when(debtService.updateDebt(eq(1L), any())).thenReturn(buildDebtDTO(1L, "B"));

        mockMvc.perform(put("/api/client_debts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDebtDTO(1L, "B"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("B"));
    }
}

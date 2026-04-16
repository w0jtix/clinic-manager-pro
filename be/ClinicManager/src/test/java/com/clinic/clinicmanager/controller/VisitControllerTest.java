package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.DTO.VisitDTO;
import com.clinic.clinicmanager.DTO.request.VisitFilterDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import com.clinic.clinicmanager.service.VisitService;
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

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VisitController.class)
@Import(WebSecurityConfig.class)
class VisitControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean VisitService visitService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private VisitDTO buildVisitDTO(Long id) {
        VisitDTO dto = new VisitDTO();
        dto.setId(id);
        ClientDTO client = new ClientDTO();
        client.setId(1L);
        client.setFirstName("Jan");
        client.setLastName("Kowalski");
        dto.setClient(client);
        dto.setDate(LocalDate.of(2025, 1, 15));
        return dto;
    }

    @Test
    @WithMockUser(roles = "USER")
    void getVisits_shouldReturn200_whenResultsFound() throws Exception {
        Page<VisitDTO> page = new PageImpl<>(List.of(buildVisitDTO(1L), buildVisitDTO(2L)));
        when(visitService.getVisits(any(VisitFilterDTO.class), eq(0), eq(30))).thenReturn(page);

        mockMvc.perform(post("/api/visits/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VisitFilterDTO())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getVisits_shouldReturn204_whenNoResults() throws Exception {
        Page<VisitDTO> emptyPage = new PageImpl<>(Collections.emptyList());
        when(visitService.getVisits(any(), eq(0), eq(30))).thenReturn(emptyPage);

        mockMvc.perform(post("/api/visits/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VisitFilterDTO())))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getVisitById_shouldReturn200_whenFound() throws Exception {
        when(visitService.getVisitById(1L)).thenReturn(buildVisitDTO(1L));

        mockMvc.perform(get("/api/visits/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.client.firstName").value("Jan"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getVisitById_shouldReturn404_whenNotFound() throws Exception {
        when(visitService.getVisitById(99L))
                .thenThrow(new ResourceNotFoundException("Visit not found with ID: 99"));

        mockMvc.perform(get("/api/visits/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getVisitPreview_shouldReturn201_whenValid() throws Exception {
        VisitDTO input = buildVisitDTO(null);
        VisitDTO preview = buildVisitDTO(null);
        when(visitService.getVisitPreview(any(VisitDTO.class))).thenReturn(preview);

        mockMvc.perform(post("/api/visits/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getVisitPreview_shouldReturn404_whenClientNotFound() throws Exception {
        when(visitService.getVisitPreview(any(VisitDTO.class)))
                .thenThrow(new ResourceNotFoundException("Client not found"));

        mockMvc.perform(post("/api/visits/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildVisitDTO(null))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createVisit_shouldReturn400_whenCreationFails() throws Exception {
        when(visitService.createVisit(any(VisitDTO.class)))
                .thenThrow(new CreationException("Failed to create visit"));

        mockMvc.perform(post("/api/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildVisitDTO(null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createVisit_shouldReturn201_whenValid() throws Exception {
        VisitDTO input = buildVisitDTO(null);
        VisitDTO saved = buildVisitDTO(1L);
        when(visitService.createVisit(any(VisitDTO.class))).thenReturn(saved);

        mockMvc.perform(post("/api/visits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteVisit_shouldReturn204_whenDeleted() throws Exception {
        mockMvc.perform(delete("/api/visits/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void findVisitPaidByVoucher_shouldReturn200_whenFound() throws Exception {
        when(visitService.findVisitPaidByVoucher(1L)).thenReturn(buildVisitDTO(1L));

        mockMvc.perform(get("/api/visits/search/voucher/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void findVisitPaidByVoucher_shouldReturn404_whenNotFound() throws Exception {
        when(visitService.findVisitPaidByVoucher(99L))
                .thenThrow(new ResourceNotFoundException("Visit not found"));

        mockMvc.perform(get("/api/visits/search/voucher/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void findByDebtSourceVisitId_shouldReturn200_whenFound() throws Exception {
        when(visitService.findByDebtSourceVisitId(1L)).thenReturn(buildVisitDTO(1L));

        mockMvc.perform(get("/api/visits/search/debt-source/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void findByDebtSourceVisitId_shouldReturn404_whenNotFound() throws Exception {
        when(visitService.findByDebtSourceVisitId(99L))
                .thenThrow(new ResourceNotFoundException("Visit not found"));

        mockMvc.perform(get("/api/visits/search/debt-source/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getVisitByDebtSourceId_shouldReturn200_whenFound() throws Exception {
        when(visitService.getVisitByDebtSourceId(1L)).thenReturn(buildVisitDTO(1L));

        mockMvc.perform(get("/api/visits/search/debt/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getVisitByDebtSourceId_shouldReturn404_whenNotFound() throws Exception {
        when(visitService.getVisitByDebtSourceId(99L))
                .thenThrow(new ResourceNotFoundException("Visit not found"));

        mockMvc.perform(get("/api/visits/search/debt/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void findVisitByReviewId_shouldReturn200_whenFound() throws Exception {
        when(visitService.findByReviewId(1L)).thenReturn(buildVisitDTO(1L));

        mockMvc.perform(get("/api/visits/search/review/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void findVisitByReviewId_shouldReturn404_whenNotFound() throws Exception {
        when(visitService.findByReviewId(99L))
                .thenThrow(new ResourceNotFoundException("Visit not found"));

        mockMvc.perform(get("/api/visits/search/review/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void countVisitsByClientId_shouldReturn200_withCount() throws Exception {
        when(visitService.countVisitsByClientId(1L)).thenReturn(5L);

        mockMvc.perform(get("/api/visits/count/client/1"))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getVisitsWithCashPaymentByDate_shouldReturn200_whenFound() throws Exception {
        when(visitService.findAllByDateWithCashPayment(LocalDate.of(2025, 1, 15)))
                .thenReturn(List.of(buildVisitDTO(1L)));

        mockMvc.perform(get("/api/visits/search/cash")
                        .param("date", "2025-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getVisitsWithCashPaymentByDate_shouldReturn204_whenNoResults() throws Exception {
        when(visitService.findAllByDateWithCashPayment(any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/visits/search/cash")
                        .param("date", "2025-01-15"))
                .andExpect(status().isNoContent());
    }
}

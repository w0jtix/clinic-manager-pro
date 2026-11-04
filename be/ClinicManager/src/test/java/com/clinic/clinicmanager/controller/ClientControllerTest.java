package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.DTO.ClientDuplicateCheckDTO;
import com.clinic.clinicmanager.DTO.ClientIdentityDTO;
import com.clinic.clinicmanager.DTO.ClientMatchDTO;
import com.clinic.clinicmanager.DTO.ClientReviewInfoDTO;
import com.clinic.clinicmanager.DTO.ClientSpendChartDTO;
import com.clinic.clinicmanager.DTO.ClientSummaryDTO;
import com.clinic.clinicmanager.DTO.ClientTopItemDTO;
import com.clinic.clinicmanager.DTO.request.ClientFilterDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.DuplicateClientException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.model.constants.ClientMatchReason;
import com.clinic.clinicmanager.service.ClientService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientController.class)
@Import(WebSecurityConfig.class)
class ClientControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ClientService clientService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private ClientDTO buildClientDTO(Long id) {
        ClientDTO dto = new ClientDTO();
        dto.setId(id);
        dto.setFirstName("Jan");
        dto.setLastName("Kowalski");
        dto.setIsDeleted(false);
        return dto;
    }

    private ClientDTO buildClientWithAddress(Long id) {
        ClientDTO dto = buildClientDTO(id);
        dto.setAddress("Kwiatowa 5/2");
        dto.setPostalCode("00-001");
        dto.setCity("Warszawa");
        return dto;
    }

    @Test
    @WithMockUser(roles = "USER")
    void getClientById_shouldReturn200_whenFound() throws Exception {
        when(clientService.getClientById(1L)).thenReturn(buildClientDTO(1L));

        mockMvc.perform(get("/api/clients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Jan"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getClientById_shouldMaskAddress_whenUserRole() throws Exception {
        when(clientService.getClientById(1L)).thenReturn(buildClientWithAddress(1L));

        mockMvc.perform(get("/api/clients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jan"))
                .andExpect(jsonPath("$.address").value(nullValue()))
                .andExpect(jsonPath("$.postalCode").value(nullValue()))
                .andExpect(jsonPath("$.city").value(nullValue()));
    }

    @Test
    @WithMockUser(roles = "MASTER")
    void getClientById_shouldReturnAddress_whenMasterRole() throws Exception {
        when(clientService.getClientById(1L)).thenReturn(buildClientWithAddress(1L));

        mockMvc.perform(get("/api/clients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("Kwiatowa 5/2"))
                .andExpect(jsonPath("$.postalCode").value("00-001"))
                .andExpect(jsonPath("$.city").value("Warszawa"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getClientById_shouldReturn404_whenNotFound() throws Exception {
        when(clientService.getClientById(99L))
                .thenThrow(new ResourceNotFoundException("Client not found with ID: 99"));

        mockMvc.perform(get("/api/clients/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = {"USER", "ADMIN"})
    void getClientSummary_shouldReturn200_withAggregates() throws Exception {
        ClientSummaryDTO summary = new ClientSummaryDTO(
                7L,
                new BigDecimal("1234.50"),
                2L,
                1L,
                new ClientTopItemDTO("Manicure", 5L),
                new ClientTopItemDTO("Krem", 3L),
                true,
                new BigDecimal("149.50"),
                1,
                new ClientReviewInfoDTO(true, true),
                new ClientReviewInfoDTO(false, false));
        when(clientService.getClientSummary(1L)).thenReturn(summary);

        mockMvc.perform(get("/api/clients/1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitsCount").value(7))
                .andExpect(jsonPath("$.totalSpent").value(1234.50))
                .andExpect(jsonPath("$.absencesCount").value(2))
                .andExpect(jsonPath("$.latenessCount").value(1))
                .andExpect(jsonPath("$.topService.name").value("Manicure"))
                .andExpect(jsonPath("$.topService.quantity").value(5))
                .andExpect(jsonPath("$.topProduct.name").value("Krem"))
                .andExpect(jsonPath("$.hasDebts").value(true))
                .andExpect(jsonPath("$.debtTotal").value(149.50))
                .andExpect(jsonPath("$.activeVouchersCount").value(1))
                .andExpect(jsonPath("$.vouchers").doesNotExist())
                .andExpect(jsonPath("$.googleReview.present").value(true))
                .andExpect(jsonPath("$.googleReview.rewardUsed").value(true))
                .andExpect(jsonPath("$.booksyReview.present").value(false));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getClientSummary_shouldOmitTotalSpent_whenNotAdmin() throws Exception {
        ClientSummaryDTO summary = new ClientSummaryDTO(
                7L,
                null,
                2L,
                1L,
                new ClientTopItemDTO("Manicure", 5L),
                new ClientTopItemDTO("Krem", 3L),
                true,
                new BigDecimal("149.50"),
                1,
                new ClientReviewInfoDTO(true, true),
                new ClientReviewInfoDTO(false, false));
        when(clientService.getClientSummary(1L)).thenReturn(summary);

        mockMvc.perform(get("/api/clients/1/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSpent").doesNotExist())
                .andExpect(jsonPath("$.visitsCount").value(7))
                .andExpect(jsonPath("$.absencesCount").value(2))
                .andExpect(jsonPath("$.latenessCount").value(1))
                .andExpect(jsonPath("$.topService.name").value("Manicure"))
                .andExpect(jsonPath("$.hasDebts").value(true))
                .andExpect(jsonPath("$.debtTotal").value(149.50))
                .andExpect(jsonPath("$.activeVouchersCount").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getClientSummary_shouldReturn404_whenClientNotFound() throws Exception {
        when(clientService.getClientSummary(99L))
                .thenThrow(new ResourceNotFoundException("Client not found with given id: 99"));

        mockMvc.perform(get("/api/clients/99/summary"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getSpendChart_shouldReturn200_withTwelveMonths() throws Exception {
        List<BigDecimal> months = new ArrayList<>(Collections.nCopies(12, BigDecimal.ZERO));
        months.set(0, new BigDecimal("300.00"));
        List<Integer> visitCounts = new ArrayList<>(Collections.nCopies(12, 0));
        visitCounts.set(0, 2);
        when(clientService.getSpendChart(1L, 2025))
                .thenReturn(new ClientSpendChartDTO(2025, 2021, 2026, months, visitCounts));

        mockMvc.perform(get("/api/clients/1/spend-chart").param("year", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.firstYear").value(2021))
                .andExpect(jsonPath("$.lastYear").value(2026))
                .andExpect(jsonPath("$.months.length()").value(12))
                .andExpect(jsonPath("$.months[0]").value(300.00))
                .andExpect(jsonPath("$.visitCounts.length()").value(12))
                .andExpect(jsonPath("$.visitCounts[0]").value(2))
                .andExpect(jsonPath("$.visitCounts[1]").value(0));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getSpendChart_shouldPassNullYear_whenParamOmitted() throws Exception {
        when(clientService.getSpendChart(eq(1L), isNull()))
                .thenReturn(new ClientSpendChartDTO(2026, 2026, 2026,
                        new ArrayList<>(Collections.nCopies(12, BigDecimal.ZERO)),
                        new ArrayList<>(Collections.nCopies(12, 0))));

        mockMvc.perform(get("/api/clients/1/spend-chart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026));

        verify(clientService).getSpendChart(1L, null);
    }

    @Test
    @WithMockUser(roles = "USER")
    void getSpendChart_shouldReturn404_whenClientNotFound() throws Exception {
        when(clientService.getSpendChart(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Client not found with given id: 99"));

        mockMvc.perform(get("/api/clients/99/spend-chart"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getClients_shouldReturn200_whenResultsFound() throws Exception {
        when(clientService.getClients(any(ClientFilterDTO.class)))
                .thenReturn(List.of(buildClientDTO(1L), buildClientDTO(2L)));

        mockMvc.perform(post("/api/clients/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClientFilterDTO())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getClients_shouldReturn204_whenNoResults() throws Exception {
        when(clientService.getClients(any())).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/clients/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClientFilterDTO())))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createClient_shouldReturn201_whenValid() throws Exception {
        ClientDTO input = buildClientDTO(null);
        ClientDTO saved = buildClientDTO(1L);
        when(clientService.createClient(any(ClientDTO.class))).thenReturn(saved);

        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Jan"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createClient_shouldReturn400_whenCreationFails() throws Exception {
        when(clientService.createClient(any(ClientDTO.class)))
                .thenThrow(new CreationException("Failed to create client"));

        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildClientDTO(null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateClient_shouldReturn200_whenValid() throws Exception {
        ClientDTO input = buildClientDTO(null);
        ClientDTO saved = buildClientDTO(1L);
        when(clientService.updateClient(eq(1L), any(ClientDTO.class))).thenReturn(saved);

        mockMvc.perform(put("/api/clients/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateClient_shouldReturn400_whenUpdateFails() throws Exception {
        when(clientService.updateClient(eq(1L), any(ClientDTO.class)))
                .thenThrow(new UpdateException("Failed to update client"));

        mockMvc.perform(put("/api/clients/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildClientDTO(null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateClient_shouldReturn409_whenClientDeleted() throws Exception {
        when(clientService.updateClient(eq(1L), any(ClientDTO.class)))
                .thenThrow(new ConflictException("Client is deleted. Restore the client before editing."));

        mockMvc.perform(put("/api/clients/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildClientDTO(null))))
                .andExpect(status().isConflict())
                .andExpect(content().string("Client is deleted. Restore the client before editing."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteClient_shouldReturn204_whenDeleted() throws Exception {
        mockMvc.perform(delete("/api/clients/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteClient_shouldReturn400_whenDeletionFails() throws Exception {
        doThrow(new DeletionException("Failed to delete client"))
                .when(clientService).deleteClientById(1L);

        mockMvc.perform(delete("/api/clients/1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void checkDuplicates_shouldReturn200_withMatches() throws Exception {
        ClientMatchDTO exact = new ClientMatchDTO(9L, "Jan", "Kowalski", null, "600100200", null, true, ClientMatchReason.EXACT);
        ClientMatchDTO similar = new ClientMatchDTO(10L, "Jan", "Kowalski", null, "511222333", null, false, ClientMatchReason.SAME_NAME);
        when(clientService.checkDuplicates("Jan", "Kowalski", "600100200", 3L))
                .thenReturn(new ClientDuplicateCheckDTO(exact, List.of(similar)));

        mockMvc.perform(get("/api/clients/duplicates")
                        .param("firstName", "Jan")
                        .param("lastName", "Kowalski")
                        .param("phoneNumber", "600100200")
                        .param("excludeId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exactMatch.id").value(9))
                .andExpect(jsonPath("$.exactMatch.isDeleted").value(true))
                .andExpect(jsonPath("$.exactMatch.matchReason").value("EXACT"))
                .andExpect(jsonPath("$.similar[0].matchReason").value("SAME_NAME"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void restoreClient_shouldReturn200() throws Exception {
        when(clientService.restoreClient(eq(1L), any(ClientIdentityDTO.class))).thenReturn(buildClientDTO(1L));

        mockMvc.perform(post("/api/clients/1/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClientIdentityDTO("Jan", "Kowalski", "600100200"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDeleted").value(false));
    }

    @Test
    @WithMockUser(roles = "USER")
    void restoreClient_shouldReturn403_whenIdentityDiffers() throws Exception {
        when(clientService.restoreClient(eq(1L), any())).thenThrow(new AccessDeniedException("mismatch"));

        mockMvc.perform(post("/api/clients/1/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClientIdentityDTO("Jan", "Nowak", ""))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void restoreClient_shouldReturn409_whenClientNotDeleted() throws Exception {
        when(clientService.restoreClient(eq(1L), any())).thenThrow(new ConflictException("Client is not deleted."));

        mockMvc.perform(post("/api/clients/1/restore")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createClient_shouldReturn409_withDuplicateJson() throws Exception {
        when(clientService.createClient(any()))
                .thenThrow(new DuplicateClientException(5L, false, true, "Jan", "Kowalski"));

        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildClientDTO(null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CLIENT"))
                .andExpect(jsonPath("$.clientId").value(5))
                .andExpect(jsonPath("$.deleted").value(false))
                .andExpect(jsonPath("$.withoutPhone").value(true));
    }
}

package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.CardHistoryChangeDTO;
import com.clinic.clinicmanager.DTO.CardHistoryEditedEntryDTO;
import com.clinic.clinicmanager.DTO.CardHistoryEntrySnapshotDTO;
import com.clinic.clinicmanager.DTO.CardHistoryItemDTO;
import com.clinic.clinicmanager.DTO.ClientCardDTO;
import com.clinic.clinicmanager.DTO.ClientCardDataDTO;
import com.clinic.clinicmanager.DTO.ClientCardListItemDTO;
import com.clinic.clinicmanager.config.security.CardSecurityConfig;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.DuplicateClientException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.constants.CardAccessAction;
import com.clinic.clinicmanager.service.CardHistoryService;
import com.clinic.clinicmanager.service.ClientCardService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientCardController.class)
@Import({WebSecurityConfig.class, CardSecurityConfig.class, AuthEntryPointJwt.class})
class ClientCardControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ClientCardService clientCardService;
    @MockBean CardHistoryService cardHistoryService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private ClientCardDTO cardDTO() {
        return cardDTO(null);
    }

    private ClientCardDTO cardDTO(LocalDate lastAbsenceUnpaidDate) {
        return new ClientCardDTO(1L, "Jan", "Kowalski", LocalDate.of(1980, 1, 1), "600100200",
                false, true, "Rocznica ślubu 12.05", false, LocalDate.of(2026, 3, 5), lastAbsenceUnpaidDate,
                LocalDateTime.of(2026, 9, 1, 12, 0));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    void getCard_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/cards/1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(clientCardService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void getCard_shouldReturn200_withNoStoreCacheHeader() throws Exception {
        when(clientCardService.getCard(1L)).thenReturn(cardDTO());

        mockMvc.perform(get("/api/cards/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.clientId").value(1))
                .andExpect(jsonPath("$.birthDate").value("1980-01-01"))
                .andExpect(jsonPath("$.lastVisitDate").value("2026-03-05"))
                .andExpect(jsonPath("$.remarks").value("Rocznica ślubu 12.05"))
                .andExpect(jsonPath("$.lastAbsenceUnpaidDate").value(nullValue()))
                .andExpect(jsonPath("$.cardCreatedAt").exists());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getCard_shouldReturnLastAbsenceUnpaidDate_whenPresent() throws Exception {
        when(clientCardService.getCard(1L)).thenReturn(cardDTO(LocalDate.of(2026, 9, 22)));

        mockMvc.perform(get("/api/cards/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastVisitDate").value("2026-03-05"))
                .andExpect(jsonPath("$.lastAbsenceUnpaidDate").value("2026-09-22"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getCard_shouldReturn404_whenServiceThrowsNotFound() throws Exception {
        when(clientCardService.getCard(1L)).thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/api/cards/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void search_shouldReturn200_withPage() throws Exception {
        ClientCardListItemDTO item = new ClientCardListItemDTO(1L, "Jan", "Kowalski", null, "600100200", false,
                LocalDate.of(2026, 10, 20), true);
        when(clientCardService.search(any(), eq(0), eq(25)))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 25), 1));

        mockMvc.perform(post("/api/cards/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"kow\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].lastName").value("Kowalski"))
                .andExpect(jsonPath("$.content[0].lastVisitDate").value("2026-10-20"))
                .andExpect(jsonPath("$.content[0].lastVisitAbsence").value(true))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void search_shouldReturn204_whenPageEmpty() throws Exception {
        when(clientCardService.search(any(), eq(2), eq(10))).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(post("/api/cards/search?page=2&size=10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createCard_shouldReturn201() throws Exception {
        when(clientCardService.createCardWithClient(any())).thenReturn(cardDTO());

        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ClientCardDataDTO("Jan", "Kowalski", LocalDate.of(1980, 1, 1), "600100200", false, true, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientId").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createCard_shouldReturn400_whenFirstNameBlank() throws Exception {
        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ClientCardDataDTO(" ", "Kowalski", null, null, false, false, null))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(clientCardService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void createCard_shouldReturn400_whenBirthDateInFuture() throws Exception {
        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ClientCardDataDTO("Jan", "Kowalski", LocalDate.now().plusDays(1), null, false, false, null))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(clientCardService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void createCard_shouldReturn400_whenBirthDateBefore1900() throws Exception {
        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ClientCardDataDTO("Jan", "Kowalski", LocalDate.of(1899, 12, 31), null, false, false, null))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(clientCardService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void createCard_shouldReturn400_whenPhoneTooLong() throws Exception {
        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ClientCardDataDTO("Jan", "Kowalski", null, "1".repeat(31), false, false, null))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(clientCardService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void createCard_shouldReturn409_withDuplicateJson() throws Exception {
        when(clientCardService.createCardWithClient(any()))
                .thenThrow(new DuplicateClientException(123L, true, false, "Jan", "Kowalski"));

        mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ClientCardDataDTO("Jan", "Kowalski", null, "600100200", false, false, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CLIENT"))
                .andExpect(jsonPath("$.clientId").value(123))
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.withoutPhone").value(false))
                .andExpect(jsonPath("$.firstName").value("Jan"))
                .andExpect(jsonPath("$.lastName").value("Kowalski"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateClientData_shouldReturn200() throws Exception {
        when(clientCardService.updateClientData(eq(1L), any())).thenReturn(cardDTO());

        mockMvc.perform(put("/api/cards/1/client-data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ClientCardDataDTO("Jan", "Kowalski", null, "600100200", false, true, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signedRegulations").value(true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateClientData_shouldPassRemarks_andReturnThem() throws Exception {
        when(clientCardService.updateClientData(eq(1L), any())).thenReturn(cardDTO());

        mockMvc.perform(put("/api/cards/1/client-data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ClientCardDataDTO("Jan", "Kowalski", null, "600100200", false, true,
                                "Rocznica ślubu 12.05"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remarks").value("Rocznica ślubu 12.05"));

        ArgumentCaptor<ClientCardDataDTO> captor = ArgumentCaptor.forClass(ClientCardDataDTO.class);
        verify(clientCardService).updateClientData(eq(1L), captor.capture());
        assertThat(captor.getValue().getRemarks()).isEqualTo("Rocznica ślubu 12.05");
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateClientData_shouldReturn400_whenRemarksTooLong() throws Exception {
        mockMvc.perform(put("/api/cards/1/client-data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ClientCardDataDTO("Jan", "Kowalski", null, null, false, false, "a".repeat(2001)))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(clientCardService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateClientData_shouldReturn200_whenRemarksAtLimit() throws Exception {
        when(clientCardService.updateClientData(eq(1L), any())).thenReturn(cardDTO());

        mockMvc.perform(put("/api/cards/1/client-data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ClientCardDataDTO("Jan", "Kowalski", null, null, false, false, "a".repeat(2000)))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateClientData_shouldReturn400_whenLastNameMissing() throws Exception {
        mockMvc.perform(put("/api/cards/1/client-data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ClientCardDataDTO("Jan", null, null, null, false, false, null))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(clientCardService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateClientData_shouldReturn409_whenClientDeleted() throws Exception {
        when(clientCardService.updateClientData(eq(1L), any())).thenThrow(new ConflictException("Client is deleted."));

        mockMvc.perform(put("/api/cards/1/client-data")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ClientCardDataDTO("Jan", "Kowalski", null, null, false, false, null))))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = {"USER", "ADMIN"})
    void getHistory_shouldReturn200_forAdmin_withContract() throws Exception {
        CardHistoryItemDTO item = new CardHistoryItemDTO(123L, LocalDateTime.of(2026, 10, 13, 10, 15),
                CardAccessAction.UPDATE_QUESTIONNAIRE, 4L, "Anna Kowalska", "1.2.3.4", "Recepcja", null, null,
                null, null, List.of(new CardHistoryChangeDTO("Cukrzyca", "YES_NO", "NIE", "TAK")));
        when(cardHistoryService.getHistory(1L, 0, 30))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 30), 1));

        mockMvc.perform(get("/api/cards/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(123))
                .andExpect(jsonPath("$.content[0].at").value("2026-10-13T10:15:00"))
                .andExpect(jsonPath("$.content[0].action").value("UPDATE_QUESTIONNAIRE"))
                .andExpect(jsonPath("$.content[0].userId").value(4))
                .andExpect(jsonPath("$.content[0].userName").value("Anna Kowalska"))
                .andExpect(jsonPath("$.content[0].ipAddress").value("1.2.3.4"))
                .andExpect(jsonPath("$.content[0].deviceName").value("Recepcja"))
                .andExpect(jsonPath("$.content[0].entryDate").doesNotExist())
                .andExpect(jsonPath("$.content[0].createdEntry").doesNotExist())
                .andExpect(jsonPath("$.content[0].editedEntry").doesNotExist())
                .andExpect(jsonPath("$.content[0].invalidatedEntry").doesNotExist())
                .andExpect(jsonPath("$.content[0].changes[0].label").value("Cukrzyca"))
                .andExpect(jsonPath("$.content[0].changes[0].type").value("YES_NO"))
                .andExpect(jsonPath("$.content[0].changes[0].before").value("NIE"))
                .andExpect(jsonPath("$.content[0].changes[0].after").value("TAK"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = {"USER", "ADMIN"})
    void getHistory_shouldSerializeCreatedEntry_forCreateTherapyEntry() throws Exception {
        CardHistoryItemDTO item = new CardHistoryItemDTO(124L, LocalDateTime.of(2026, 10, 13, 10, 15),
                CardAccessAction.CREATE_THERAPY_ENTRY, 4L, "Anna Kowalska", "1.2.3.4", null, LocalDate.of(2026, 9, 15),
                new CardHistoryEntrySnapshotDTO(LocalDate.of(2026, 9, 10), "Pierwotna treść"), null, null, null);
        when(cardHistoryService.getHistory(1L, 0, 30))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 30), 1));

        mockMvc.perform(get("/api/cards/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("CREATE_THERAPY_ENTRY"))
                .andExpect(jsonPath("$.content[0].entryDate").value("2026-09-15"))
                .andExpect(jsonPath("$.content[0].createdEntry.entryDate").value("2026-09-10"))
                .andExpect(jsonPath("$.content[0].createdEntry.content").value("Pierwotna treść"))
                .andExpect(jsonPath("$.content[0].changes").doesNotExist());
    }

    @Test
    @WithMockUser(roles = {"USER", "ADMIN"})
    void getHistory_shouldSerializeEditedEntry_forUpdateTherapyEntry() throws Exception {
        CardHistoryItemDTO item = new CardHistoryItemDTO(125L, LocalDateTime.of(2026, 10, 13, 10, 15),
                CardAccessAction.UPDATE_THERAPY_ENTRY, 4L, "Anna Kowalska", "1.2.3.4", null, LocalDate.of(2026, 9, 18),
                null, new CardHistoryEditedEntryDTO(LocalDate.of(2026, 9, 15), "Przed edycją",
                        LocalDate.of(2026, 9, 18), "Po edycji"), null, null);
        when(cardHistoryService.getHistory(1L, 0, 30))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 30), 1));

        mockMvc.perform(get("/api/cards/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("UPDATE_THERAPY_ENTRY"))
                .andExpect(jsonPath("$.content[0].entryDate").value("2026-09-18"))
                .andExpect(jsonPath("$.content[0].editedEntry.beforeEntryDate").value("2026-09-15"))
                .andExpect(jsonPath("$.content[0].editedEntry.beforeContent").value("Przed edycją"))
                .andExpect(jsonPath("$.content[0].editedEntry.afterEntryDate").value("2026-09-18"))
                .andExpect(jsonPath("$.content[0].editedEntry.afterContent").value("Po edycji"))
                .andExpect(jsonPath("$.content[0].createdEntry").doesNotExist())
                .andExpect(jsonPath("$.content[0].invalidatedEntry").doesNotExist());
    }

    @Test
    @WithMockUser(roles = {"USER", "ADMIN"})
    void getHistory_shouldSerializeInvalidatedEntry_forInvalidateTherapyEntry() throws Exception {
        CardHistoryItemDTO item = new CardHistoryItemDTO(126L, LocalDateTime.of(2026, 10, 13, 10, 15),
                CardAccessAction.INVALIDATE_THERAPY_ENTRY, 4L, "Anna Kowalska", "1.2.3.4", null, LocalDate.of(2026, 9, 1),
                null, null, new CardHistoryEntrySnapshotDTO(LocalDate.of(2026, 9, 1), "Błędny wpis"), null);
        when(cardHistoryService.getHistory(1L, 0, 30))
                .thenReturn(new PageImpl<>(List.of(item), PageRequest.of(0, 30), 1));

        mockMvc.perform(get("/api/cards/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("INVALIDATE_THERAPY_ENTRY"))
                .andExpect(jsonPath("$.content[0].invalidatedEntry.entryDate").value("2026-09-01"))
                .andExpect(jsonPath("$.content[0].invalidatedEntry.content").value("Błędny wpis"))
                .andExpect(jsonPath("$.content[0].createdEntry").doesNotExist())
                .andExpect(jsonPath("$.content[0].editedEntry").doesNotExist());
    }

    @Test
    @WithMockUser(roles = {"USER", "ADMIN"})
    void getHistory_shouldPassPagingParams() throws Exception {
        when(cardHistoryService.getHistory(1L, 2, 10)).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/cards/1/history").param("page", "2").param("size", "10"))
                .andExpect(status().isOk());

        verify(cardHistoryService).getHistory(1L, 2, 10);
    }

    @Test
    @WithMockUser(roles = "USER")
    void getHistory_shouldReturn403_forUser() throws Exception {
        mockMvc.perform(get("/api/cards/1/history"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(cardHistoryService);
    }

    @Test
    void getHistory_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/cards/1/history"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(cardHistoryService);
    }

    @Test
    @WithMockUser(roles = {"USER", "ADMIN"})
    void getHistory_shouldReturn404_whenClientMissing() throws Exception {
        when(cardHistoryService.getHistory(1L, 0, 30)).thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/api/cards/1/history"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = {"USER", "ADMIN"})
    void getHistory_shouldReturn400_whenPageNegative() throws Exception {
        when(cardHistoryService.getHistory(1L, -1, 30))
                .thenThrow(new ValidationException("Numer strony nie może być ujemny."));

        mockMvc.perform(get("/api/cards/1/history").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Numer strony nie może być ujemny."));
    }

    @Test
    @WithMockUser(roles = "MASTER")
    void getCard_shouldReturn200_whenOnlyMasterRoleStored() throws Exception {
        when(clientCardService.getCard(1L)).thenReturn(cardDTO());

        mockMvc.perform(get("/api/cards/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MASTER")
    void getHistory_shouldReturn200_whenMasterWithoutCompanyMode() throws Exception {
        when(cardHistoryService.getHistory(1L, 0, 30))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 30), 0));

        mockMvc.perform(get("/api/cards/1/history"))
                .andExpect(status().isOk());
    }
}

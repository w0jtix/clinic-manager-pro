package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.TherapyEntryDTO;
import com.clinic.clinicmanager.DTO.TherapyEntryRevisionDTO;
import com.clinic.clinicmanager.DTO.TherapyTimelineDTO;
import com.clinic.clinicmanager.DTO.TimelineItemDTO;
import com.clinic.clinicmanager.DTO.VisitSummaryDTO;
import com.clinic.clinicmanager.DTO.VisitSummaryProductDTO;
import com.clinic.clinicmanager.DTO.VisitSummaryServiceDTO;
import com.clinic.clinicmanager.config.security.CardSecurityConfig;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.TherapyEntryConflictException;
import com.clinic.clinicmanager.exceptions.TherapyEntryDayTakenException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.constants.PaymentStatus;
import com.clinic.clinicmanager.model.constants.TimelineItemType;
import com.clinic.clinicmanager.service.TherapyEntryService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TherapyEntryController.class)
@Import({WebSecurityConfig.class, CardSecurityConfig.class, AuthEntryPointJwt.class})
class TherapyEntryControllerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 6);
    private static final String CREATE_BODY = "{\"entryDate\":\"2026-10-06\",\"content\":\"Opatrunek\"}";
    private static final String UPDATE_BODY = "{\"entryDate\":\"2026-10-06\",\"content\":\"Opatrunek\",\"expectedVersion\":2}";

    @Autowired MockMvc mockMvc;

    @MockBean TherapyEntryService therapyEntryService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private static VisitSummaryDTO visit(boolean absence, PaymentStatus status) {
        return new VisitSummaryDTO(31L, "Anna",
                List.of(new VisitSummaryServiceDTO("Pedicure", "143,118,230"), new VisitSummaryServiceDTO("Klamra", null)),
                List.of(new VisitSummaryProductDTO("Krem", 2L, "20,184,166"), new VisitSummaryProductDTO("Pasta", 1L, null)),
                absence, status, new BigDecimal("245.50"));
    }

    private static TimelineItemDTO entryItem() {
        TherapyEntryDTO entry = new TherapyEntryDTO(12L, "Opatrunek", 7L, "Anna",
                LocalDateTime.of(2026, 10, 6, 9, 30), LocalDateTime.of(2026, 10, 6, 10, 0), true,
                false, null, 2L, true);
        return new TimelineItemDTO(TimelineItemType.ENTRY, DAY, entry, List.of(visit(false, PaymentStatus.PAID)));
    }

    @Test
    void timeline_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/cards/1/timeline"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(therapyEntryService);
    }

    @Test
    @WithMockUser(roles = "GUEST")
    void createEntry_shouldReturn403_withoutUserRole() throws Exception {
        mockMvc.perform(post("/api/cards/1/therapy-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(therapyEntryService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void timeline_shouldReturn200_withShape_andNoStore() throws Exception {
        TimelineItemDTO absence = new TimelineItemDTO(TimelineItemType.ABSENCE, DAY.minusDays(3), null,
                List.of(visit(true, PaymentStatus.PARTIAL)));
        when(therapyEntryService.getTimeline(1L, DAY, 10))
                .thenReturn(new TherapyTimelineDTO(List.of(entryItem(), absence), DAY.minusDays(3)));

        mockMvc.perform(get("/api/cards/1/timeline").param("beforeDate", "2026-10-06").param("days", "10"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.nextBeforeDate").value("2026-10-03"))
                .andExpect(jsonPath("$.items[0].type").value("ENTRY"))
                .andExpect(jsonPath("$.items[0].date").value("2026-10-06"))
                .andExpect(jsonPath("$.items[0].entry.id").value(12))
                .andExpect(jsonPath("$.items[0].entry.content").value("Opatrunek"))
                .andExpect(jsonPath("$.items[0].entry.authorUserId").value(7))
                .andExpect(jsonPath("$.items[0].entry.authorName").value("Anna"))
                .andExpect(jsonPath("$.items[0].entry.createdAt").value("2026-10-06T09:30:00"))
                .andExpect(jsonPath("$.items[0].entry.updatedAt").value("2026-10-06T10:00:00"))
                .andExpect(jsonPath("$.items[0].entry.edited").value(true))
                .andExpect(jsonPath("$.items[0].entry.invalid").value(false))
                .andExpect(jsonPath("$.items[0].entry.invalidatedAt").isEmpty())
                .andExpect(jsonPath("$.items[0].entry.version").value(2))
                .andExpect(jsonPath("$.items[0].entry.canEdit").value(true))
                .andExpect(jsonPath("$.items[0].visits[0].id").value(31))
                .andExpect(jsonPath("$.items[0].visits[0].employeeName").value("Anna"))
                .andExpect(jsonPath("$.items[0].visits[0].services[0].name").value("Pedicure"))
                .andExpect(jsonPath("$.items[0].visits[0].services[0].color").value("143,118,230"))
                .andExpect(jsonPath("$.items[0].visits[0].services[1].name").value("Klamra"))
                .andExpect(jsonPath("$.items[0].visits[0].services[1].color").isEmpty())
                .andExpect(jsonPath("$.items[0].visits[0].products[0].name").value("Krem"))
                .andExpect(jsonPath("$.items[0].visits[0].products[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].visits[0].products[0].color").value("20,184,166"))
                .andExpect(jsonPath("$.items[0].visits[0].products[1].name").value("Pasta"))
                .andExpect(jsonPath("$.items[0].visits[0].products[1].color").isEmpty())
                .andExpect(jsonPath("$.items[0].visits[0].absence").value(false))
                .andExpect(jsonPath("$.items[0].visits[0].paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.items[0].visits[0].totalValue").value(245.50))
                .andExpect(jsonPath("$.items[1].type").value("ABSENCE"))
                .andExpect(jsonPath("$.items[1].entry").isEmpty())
                .andExpect(jsonPath("$.items[1].visits[0].paymentStatus").value("PARTIAL"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void timeline_shouldDefaultToTwentyDaysWithoutCursor() throws Exception {
        when(therapyEntryService.getTimeline(eq(1L), isNull(), eq(20)))
                .thenReturn(new TherapyTimelineDTO(List.of(), null));

        mockMvc.perform(get("/api/cards/1/timeline"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"items\":[],\"nextBeforeDate\":null}"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void timeline_shouldReturn400_onMalformedDate() throws Exception {
        mockMvc.perform(get("/api/cards/1/timeline").param("beforeDate", "06.10.2026"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(therapyEntryService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void timeline_shouldReturn404_forHiddenClient() throws Exception {
        when(therapyEntryService.getTimeline(eq(1L), any(), anyInt()))
                .thenThrow(new ResourceNotFoundException("Client not found with given id: 1"));

        mockMvc.perform(get("/api/cards/1/timeline"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createEntry_shouldReturn201_withEntryItem() throws Exception {
        when(therapyEntryService.createEntry(eq(1L), argThat(dto ->
                DAY.equals(dto.getEntryDate()) && "Opatrunek".equals(dto.getContent()))))
                .thenReturn(entryItem());

        mockMvc.perform(post("/api/cards/1/therapy-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ENTRY"))
                .andExpect(jsonPath("$.entry.id").value(12))
                .andExpect(jsonPath("$.visits[0].id").value(31));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createEntry_shouldReturn400_onMalformedDate() throws Exception {
        mockMvc.perform(post("/api/cards/1/therapy-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryDate\":\"2026-13-01\",\"content\":\"Opatrunek\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Nieprawidłowy format danych."));

        verifyNoInteractions(therapyEntryService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void createEntry_shouldReturn409_withDayTakenJson() throws Exception {
        when(therapyEntryService.createEntry(eq(1L), any())).thenThrow(new TherapyEntryDayTakenException(55L));

        mockMvc.perform(post("/api/cards/1/therapy-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"code\":\"THERAPY_ENTRY_DAY_TAKEN\",\"entryId\":55}", true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createEntry_shouldReturn400_onValidationError() throws Exception {
        when(therapyEntryService.createEntry(eq(1L), any()))
                .thenThrow(new ValidationException("Data wpisu nie może być z przyszłości."));

        mockMvc.perform(post("/api/cards/1/therapy-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Data wpisu nie może być z przyszłości."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createEntry_shouldReturn409_whenClientDeleted() throws Exception {
        when(therapyEntryService.createEntry(eq(1L), any()))
                .thenThrow(new ConflictException("Klient jest usunięty. Przywróć klienta przed edycją karty."));

        mockMvc.perform(post("/api/cards/1/therapy-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateEntry_shouldReturn200_andPassExpectedVersion() throws Exception {
        when(therapyEntryService.updateEntry(eq(1L), eq(12L), argThat(dto -> Long.valueOf(2L).equals(dto.getExpectedVersion()))))
                .thenReturn(entryItem());

        mockMvc.perform(put("/api/cards/1/therapy-entries/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.version").value(2));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateEntry_shouldReturn409_withVersionConflictJson() throws Exception {
        when(therapyEntryService.updateEntry(eq(1L), eq(12L), any())).thenThrow(new TherapyEntryConflictException(4L));

        mockMvc.perform(put("/api/cards/1/therapy-entries/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().json("{\"code\":\"THERAPY_ENTRY_CONFLICT\",\"currentVersion\":4}", true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateEntry_shouldReturn409_withDayTakenJson() throws Exception {
        when(therapyEntryService.updateEntry(eq(1L), eq(12L), any())).thenThrow(new TherapyEntryDayTakenException(56L));

        mockMvc.perform(put("/api/cards/1/therapy-entries/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THERAPY_ENTRY_DAY_TAKEN"))
                .andExpect(jsonPath("$.entryId").value(56));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateEntry_shouldReturn403_forNonAuthor() throws Exception {
        when(therapyEntryService.updateEntry(eq(1L), eq(12L), any()))
                .thenThrow(new AccessDeniedException("Możesz edytować tylko własne wpisy."));

        mockMvc.perform(put("/api/cards/1/therapy-entries/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Możesz edytować tylko własne wpisy."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateEntry_shouldReturn404_forEntryOfOtherClient() throws Exception {
        when(therapyEntryService.updateEntry(eq(1L), eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Therapy entry not found with given id: 99"));

        mockMvc.perform(put("/api/cards/1/therapy-entries/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateEntry_shouldReturn409_forInvalidEntry() throws Exception {
        when(therapyEntryService.updateEntry(eq(1L), eq(12L), any()))
                .thenThrow(new ConflictException("Błędnego wpisu nie można edytować."));

        mockMvc.perform(put("/api/cards/1/therapy-entries/12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(UPDATE_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().string("Błędnego wpisu nie można edytować."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void invalidateEntry_shouldReturn200() throws Exception {
        when(therapyEntryService.invalidateEntry(1L, 12L)).thenReturn(entryItem());

        mockMvc.perform(post("/api/cards/1/therapy-entries/12/invalidate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entry.id").value(12));
    }

    @Test
    @WithMockUser(roles = "USER")
    void invalidateEntry_shouldReturn403_forNonAuthor() throws Exception {
        when(therapyEntryService.invalidateEntry(1L, 12L))
                .thenThrow(new AccessDeniedException("Możesz oznaczać jako błędne tylko własne wpisy."));

        mockMvc.perform(post("/api/cards/1/therapy-entries/12/invalidate"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "USER")
    void invalidateEntry_shouldReturn409_whenAlreadyInvalid() throws Exception {
        when(therapyEntryService.invalidateEntry(1L, 12L))
                .thenThrow(new ConflictException("Wpis jest już oznaczony jako błędny."));

        mockMvc.perform(post("/api/cards/1/therapy-entries/12/invalidate"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "USER")
    void revisions_shouldReturn200_withShape() throws Exception {
        when(therapyEntryService.getRevisions(1L, 12L)).thenReturn(List.of(
                new TherapyEntryRevisionDTO(DAY.minusDays(1), "Stara treść", LocalDateTime.of(2026, 10, 6, 10, 0), "Anna")));

        mockMvc.perform(get("/api/cards/1/therapy-entries/12/revisions"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"previousEntryDate\":\"2026-10-05\",\"previousContent\":\"Stara treść\","
                        + "\"changedAt\":\"2026-10-06T10:00:00\",\"changedByName\":\"Anna\"}]", true));
    }
}

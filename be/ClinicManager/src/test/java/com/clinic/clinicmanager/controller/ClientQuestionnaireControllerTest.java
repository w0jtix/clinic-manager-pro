package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.ClientQuestionnaireDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireAnswerDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireItemDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireUpdateDTO;
import com.clinic.clinicmanager.config.security.CardSecurityConfig;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.QuestionnaireConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.constants.QuestionnaireFieldType;
import com.clinic.clinicmanager.service.ClientQuestionnaireService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientQuestionnaireController.class)
@Import({WebSecurityConfig.class, CardSecurityConfig.class, AuthEntryPointJwt.class})
class ClientQuestionnaireControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ClientQuestionnaireService clientQuestionnaireService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private ClientQuestionnaireDTO questionnaire() {
        return new ClientQuestionnaireDTO(3L, LocalDateTime.of(2026, 10, 2, 10, 15), true, List.of(
                new QuestionnaireItemDTO(5L, "Choroby", "Diseases", QuestionnaireFieldType.SECTION, false, null, null),
                new QuestionnaireItemDTO(1L, "Cukrzyca", "Diabetes", QuestionnaireFieldType.CHECKBOX, false, true, null),
                new QuestionnaireItemDTO(2L, "Leki", null, QuestionnaireFieldType.TEXT, false, null, "Metformina")));
    }

    private String body() throws Exception {
        return objectMapper.writeValueAsString(new QuestionnaireUpdateDTO(3L, List.of(
                new QuestionnaireAnswerDTO(1L, true, null),
                new QuestionnaireAnswerDTO(2L, null, "Metformina"))));
    }

    @Test
    void getQuestionnaire_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/cards/1/questionnaire"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(clientQuestionnaireService);
    }

    @Test
    void updateQuestionnaire_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(put("/api/cards/1/questionnaire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(clientQuestionnaireService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void getQuestionnaire_shouldReturn200_withShape_andNoStore() throws Exception {
        when(clientQuestionnaireService.getQuestionnaire(1L)).thenReturn(questionnaire());

        mockMvc.perform(get("/api/cards/1/questionnaire"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-02T10:15:00"))
                .andExpect(jsonPath("$.englishEnabled").value(true))
                .andExpect(jsonPath("$.items[0].fieldId").value(5))
                .andExpect(jsonPath("$.items[0].label").value("Choroby"))
                .andExpect(jsonPath("$.items[0].labelEn").value("Diseases"))
                .andExpect(jsonPath("$.items[0].type").value("SECTION"))
                .andExpect(jsonPath("$.items[0].archived").value(false))
                .andExpect(jsonPath("$.items[0].checked").isEmpty())
                .andExpect(jsonPath("$.items[0].text").isEmpty())
                .andExpect(jsonPath("$.items[1].fieldId").value(1))
                .andExpect(jsonPath("$.items[1].labelEn").value("Diabetes"))
                .andExpect(jsonPath("$.items[1].type").value("CHECKBOX"))
                .andExpect(jsonPath("$.items[1].archived").value(false))
                .andExpect(jsonPath("$.items[1].checked").value(true))
                .andExpect(jsonPath("$.items[1].text").isEmpty())
                .andExpect(jsonPath("$.items[2].label").value("Leki"))
                .andExpect(jsonPath("$.items[2].labelEn").isEmpty())
                .andExpect(jsonPath("$.items[2].checked").isEmpty())
                .andExpect(jsonPath("$.items[2].text").value("Metformina"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getQuestionnaire_shouldReturn404_whenNotFound() throws Exception {
        when(clientQuestionnaireService.getQuestionnaire(1L)).thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/api/cards/1/questionnaire"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateQuestionnaire_shouldReturn200() throws Exception {
        when(clientQuestionnaireService.updateQuestionnaire(eq(1L), any())).thenReturn(questionnaire());

        mockMvc.perform(put("/api/cards/1/questionnaire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.englishEnabled").value(true))
                .andExpect(jsonPath("$.items[0].type").value("SECTION"));

        verify(clientQuestionnaireService).updateQuestionnaire(eq(1L), argThat(dto ->
                dto.getExpectedVersion() == 3L && dto.getAnswers().size() == 2
                        && "Metformina".equals(dto.getAnswers().get(1).getText())));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateQuestionnaire_shouldReturn409_withConflictJson() throws Exception {
        when(clientQuestionnaireService.updateQuestionnaire(eq(1L), any()))
                .thenThrow(new QuestionnaireConflictException(5L));

        mockMvc.perform(put("/api/cards/1/questionnaire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("QUESTIONNAIRE_CONFLICT"))
                .andExpect(jsonPath("$.currentVersion").value(5));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateQuestionnaire_shouldReturn409_whenClientDeleted() throws Exception {
        when(clientQuestionnaireService.updateQuestionnaire(eq(1L), any()))
                .thenThrow(new ConflictException("Klient jest usunięty. Przywróć klienta przed edycją karty."));

        mockMvc.perform(put("/api/cards/1/questionnaire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateQuestionnaire_shouldReturn400_onValidationError() throws Exception {
        when(clientQuestionnaireService.updateQuestionnaire(eq(1L), any()))
                .thenThrow(new ValidationException("Pole ankiety o id 99 nie istnieje."));

        mockMvc.perform(put("/api/cards/1/questionnaire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Pole ankiety o id 99 nie istnieje."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateQuestionnaire_shouldReturn400_whenAnsweringSection() throws Exception {
        when(clientQuestionnaireService.updateQuestionnaire(eq(1L), any()))
                .thenThrow(new ValidationException("Tytuł sekcji \"Choroby\" nie przyjmuje odpowiedzi."));

        mockMvc.perform(put("/api/cards/1/questionnaire")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Tytuł sekcji \"Choroby\" nie przyjmuje odpowiedzi."));
    }
}

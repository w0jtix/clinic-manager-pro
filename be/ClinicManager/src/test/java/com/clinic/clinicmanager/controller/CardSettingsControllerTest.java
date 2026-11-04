package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.DrawingMarkerDTO;
import com.clinic.clinicmanager.DTO.DrawingTemplateImageDTO;
import com.clinic.clinicmanager.DTO.DrawingTemplateSettingsDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireFieldDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireFieldUpdateDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireSettingsDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireSettingsUpdateDTO;
import com.clinic.clinicmanager.config.security.CardSecurityConfig;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.DTO.request.DrawingMarkerRequest;
import com.clinic.clinicmanager.model.constants.DrawingMarkerShape;
import com.clinic.clinicmanager.model.constants.DrawingTemplateDeleteResult;
import com.clinic.clinicmanager.model.constants.QuestionnaireFieldType;
import com.clinic.clinicmanager.service.DrawingMarkerService;
import com.clinic.clinicmanager.service.DrawingTemplateService;
import com.clinic.clinicmanager.service.QuestionnaireFieldService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CardSettingsController.class)
@Import({WebSecurityConfig.class, CardSecurityConfig.class, AuthEntryPointJwt.class})
class CardSettingsControllerTest {

    private static final String URL = "/api/card-settings/questionnaire";
    private static final String TEMPLATES_URL = "/api/card-settings/drawing-templates";
    private static final String MARKERS_URL = "/api/card-settings/drawing-markers";
    private static final String MARKER_JSON =
            "{\"name\":\"Modzel\",\"shape\":\"CIRCLE\",\"color\":\"#FF0000\",\"filled\":true}";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean QuestionnaireFieldService questionnaireFieldService;
    @MockBean DrawingTemplateService drawingTemplateService;
    @MockBean DrawingMarkerService drawingMarkerService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private QuestionnaireSettingsDTO settings() {
        return new QuestionnaireSettingsDTO(true, List.of(
                new QuestionnaireFieldDTO(1L, "Choroby i przeciwwskazania", "Diseases and contraindications",
                        QuestionnaireFieldType.SECTION, 0, false, false),
                new QuestionnaireFieldDTO(2L, "Cukrzyca", null, QuestionnaireFieldType.CHECKBOX, 1, false, true)));
    }

    private static DrawingTemplateSettingsDTO activeTemplate() {
        return new DrawingTemplateSettingsDTO(3L, "Stopy", 1, false, null,
                new DrawingTemplateImageDTO(30L, 1536, 2048, 4567), 2L);
    }

    private static DrawingTemplateSettingsDTO archivedTemplate() {
        return new DrawingTemplateSettingsDTO(4L, "Pięty", 2, true, LocalDateTime.of(2026, 10, 1, 9, 0), null, 1L);
    }

    private static MockMultipartHttpServletRequestBuilder putMultipart(String url) {
        MockMultipartHttpServletRequestBuilder builder = multipart(url);
        builder.with(request -> {
            request.setMethod("PUT");
            return request;
        });
        return builder;
    }

    private static MockMultipartFile templateFile() {
        return new MockMultipartFile("file", "stopy.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
    }

    private String body() throws Exception {
        return objectMapper.writeValueAsString(new QuestionnaireSettingsUpdateDTO(true, List.of(
                new QuestionnaireFieldUpdateDTO(1L, "Choroby", "Diseases", QuestionnaireFieldType.SECTION, false),
                new QuestionnaireFieldUpdateDTO(null, "Leki", null, QuestionnaireFieldType.TEXT, false))));
    }

    @Test
    void getSettings_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(questionnaireFieldService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void getSettings_shouldReturn403_forUser() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isForbidden());

        verifyNoInteractions(questionnaireFieldService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateSettings_shouldReturn403_forUser() throws Exception {
        mockMvc.perform(put(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(questionnaireFieldService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void oldQuestionnaireFieldsEndpoint_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/card-settings/questionnaire-fields"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/card-settings/questionnaire-fields")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(questionnaireFieldService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSettings_shouldReturn200_withSettingsShape() throws Exception {
        when(questionnaireFieldService.getSettings()).thenReturn(settings());

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.englishEnabled").value(true))
                .andExpect(jsonPath("$.fields[0].id").value(1))
                .andExpect(jsonPath("$.fields[0].label").value("Choroby i przeciwwskazania"))
                .andExpect(jsonPath("$.fields[0].labelEn").value("Diseases and contraindications"))
                .andExpect(jsonPath("$.fields[0].type").value("SECTION"))
                .andExpect(jsonPath("$.fields[0].displayOrder").value(0))
                .andExpect(jsonPath("$.fields[0].archived").value(false))
                .andExpect(jsonPath("$.fields[0].hasAnswers").value(false))
                .andExpect(jsonPath("$.fields[1].type").value("CHECKBOX"))
                .andExpect(jsonPath("$.fields[1].labelEn").isEmpty())
                .andExpect(jsonPath("$.fields[1].hasAnswers").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSettings_shouldReturn200_withEmptyFields() throws Exception {
        when(questionnaireFieldService.getSettings()).thenReturn(new QuestionnaireSettingsDTO(false, List.of()));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"englishEnabled\":false,\"fields\":[]}"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSettings_shouldReturn200() throws Exception {
        when(questionnaireFieldService.updateSettings(any())).thenReturn(settings());

        mockMvc.perform(put(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.englishEnabled").value(true))
                .andExpect(jsonPath("$.fields[0].type").value("SECTION"));

        verify(questionnaireFieldService).updateSettings(argThat(dto ->
                Boolean.TRUE.equals(dto.getEnglishEnabled())
                        && dto.getFields().size() == 2
                        && dto.getFields().get(0).getType() == QuestionnaireFieldType.SECTION
                        && "Diseases".equals(dto.getFields().get(0).getLabelEn())
                        && dto.getFields().get(1).getId() == null
                        && dto.getFields().get(1).getLabelEn() == null));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSettings_shouldReturn400_withPolishMessage() throws Exception {
        when(questionnaireFieldService.updateSettings(any()))
                .thenThrow(new ValidationException("Tytułu sekcji nie archiwizuje się - usuń go z listy."));

        mockMvc.perform(put(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Tytułu sekcji nie archiwizuje się - usuń go z listy."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSettings_shouldReturn409_whenFieldHasAnswers() throws Exception {
        when(questionnaireFieldService.updateSettings(any()))
                .thenThrow(new ConflictException("Nie można zmienić typu pola, które ma już odpowiedzi w kartach."));

        mockMvc.perform(put(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isConflict())
                .andExpect(content().string("Nie można zmienić typu pola, które ma już odpowiedzi w kartach."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void drawingTemplateEndpoints_shouldReturn403_forUser() throws Exception {
        mockMvc.perform(get(TEMPLATES_URL))
                .andExpect(status().isForbidden());
        mockMvc.perform(multipart(TEMPLATES_URL).file(templateFile()).param("name", "Stopy"))
                .andExpect(status().isForbidden());
        mockMvc.perform(putMultipart(TEMPLATES_URL + "/3").param("name", "Stopy"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(TEMPLATES_URL + "/3"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(TEMPLATES_URL + "/3/restore"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(TEMPLATES_URL + "/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2]}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(drawingTemplateService);
    }

    @Test
    void drawingTemplates_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get(TEMPLATES_URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(drawingTemplateService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void oldDrawingTemplateEndpoints_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/card-settings/drawing-template"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/card-settings/drawing-template/image"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/card-settings/drawing-template"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(drawingTemplateService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDrawingTemplates_shouldReturn200_withShape() throws Exception {
        when(drawingTemplateService.getTemplates()).thenReturn(List.of(activeTemplate(), archivedTemplate()));

        mockMvc.perform(get(TEMPLATES_URL))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        [{"id":3,"name":"Stopy","displayOrder":1,"archived":false,"archivedAt":null,
                          "image":{"id":30,"width":1536,"height":2048,"sizeBytes":4567},"clientCount":2},
                         {"id":4,"name":"Pięty","displayOrder":2,"archived":true,
                          "archivedAt":"2026-10-01T09:00:00","image":null,"clientCount":1}]
                        """, true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createDrawingTemplate_shouldPassNameAndFile() throws Exception {
        when(drawingTemplateService.createTemplate(eq("Stopy"), any())).thenReturn(activeTemplate());

        mockMvc.perform(multipart(TEMPLATES_URL).file(templateFile()).param("name", "Stopy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.image.sizeBytes").value(4567));

        verify(drawingTemplateService).createTemplate(eq("Stopy"), argThat(file ->
                file != null && "file".equals(file.getName()) && MediaType.IMAGE_JPEG_VALUE.equals(file.getContentType())));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createDrawingTemplate_withoutFile_passesNull() throws Exception {
        when(drawingTemplateService.createTemplate(eq("Stopy"), isNull())).thenReturn(activeTemplate());

        mockMvc.perform(multipart(TEMPLATES_URL).param("name", "Stopy"))
                .andExpect(status().isOk());

        verify(drawingTemplateService).createTemplate(eq("Stopy"), isNull());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createDrawingTemplate_shouldReturn400_and409_withPolishMessages() throws Exception {
        doThrow(new ValidationException("Nazwa szablonu jest wymagana (maksymalnie 60 znaków)."))
                .when(drawingTemplateService).createTemplate(isNull(), any());
        doThrow(new ConflictException("Szablon o tej nazwie już istnieje."))
                .when(drawingTemplateService).createTemplate(eq("Stopy"), any());
        doThrow(new ConflictException("Można mieć maksymalnie 10 aktywnych szablonów."))
                .when(drawingTemplateService).createTemplate(eq("Jedenasty"), any());

        mockMvc.perform(multipart(TEMPLATES_URL))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Nazwa szablonu jest wymagana (maksymalnie 60 znaków)."));
        mockMvc.perform(multipart(TEMPLATES_URL).param("name", "Stopy"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Szablon o tej nazwie już istnieje."));
        mockMvc.perform(multipart(TEMPLATES_URL).param("name", "Jedenasty"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Można mieć maksymalnie 10 aktywnych szablonów."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateDrawingTemplate_shouldUsePut_andPassNameFileAndRemoveImage() throws Exception {
        when(drawingTemplateService.updateTemplate(eq(3L), eq("Stopy"), any(), eq(false))).thenReturn(activeTemplate());
        when(drawingTemplateService.updateTemplate(eq(3L), eq("Stopy"), isNull(), eq(true))).thenReturn(activeTemplate());

        mockMvc.perform(putMultipart(TEMPLATES_URL + "/3").file(templateFile()).param("name", "Stopy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Stopy"));
        mockMvc.perform(putMultipart(TEMPLATES_URL + "/3").param("name", "Stopy").param("removeImage", "true"))
                .andExpect(status().isOk());

        verify(drawingTemplateService).updateTemplate(eq(3L), eq("Stopy"), argThat(file -> file != null), eq(false));
        verify(drawingTemplateService).updateTemplate(eq(3L), eq("Stopy"), isNull(), eq(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateDrawingTemplate_shouldMapErrors() throws Exception {
        when(drawingTemplateService.updateTemplate(eq(3L), any(), any(), eq(true)))
                .thenThrow(new ValidationException("Nie można jednocześnie wgrać nowego obrazu i usunąć obecnego."));
        when(drawingTemplateService.updateTemplate(eq(4L), any(), any(), anyBoolean()))
                .thenThrow(new ConflictException("Szablon jest zarchiwizowany."));
        when(drawingTemplateService.updateTemplate(eq(5L), any(), any(), anyBoolean()))
                .thenThrow(new ResourceNotFoundException("Drawing template not found with given id: 5"));

        mockMvc.perform(putMultipart(TEMPLATES_URL + "/3").file(templateFile()).param("name", "Stopy")
                        .param("removeImage", "true"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Nie można jednocześnie wgrać nowego obrazu i usunąć obecnego."));
        mockMvc.perform(putMultipart(TEMPLATES_URL + "/4").param("name", "Stopy"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Szablon jest zarchiwizowany."));
        mockMvc.perform(putMultipart(TEMPLATES_URL + "/5").param("name", "Stopy"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteDrawingTemplate_shouldReturnResult() throws Exception {
        when(drawingTemplateService.deleteTemplate(3L)).thenReturn(DrawingTemplateDeleteResult.DELETED);
        when(drawingTemplateService.deleteTemplate(4L)).thenReturn(DrawingTemplateDeleteResult.ARCHIVED);
        when(drawingTemplateService.deleteTemplate(5L)).thenThrow(new ConflictException("Szablon jest zarchiwizowany."));

        mockMvc.perform(delete(TEMPLATES_URL + "/3"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"result\":\"DELETED\"}", true));
        mockMvc.perform(delete(TEMPLATES_URL + "/4"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"result\":\"ARCHIVED\"}", true));
        mockMvc.perform(delete(TEMPLATES_URL + "/5"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Szablon jest zarchiwizowany."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void restoreDrawingTemplate_shouldReturnTemplate() throws Exception {
        when(drawingTemplateService.restoreTemplate(3L)).thenReturn(activeTemplate());

        mockMvc.perform(put(TEMPLATES_URL + "/3/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(false))
                .andExpect(jsonPath("$.displayOrder").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reorderDrawingTemplates_shouldPassIdsInOrder() throws Exception {
        when(drawingTemplateService.reorderTemplates(List.of(3L, 1L, 2L))).thenReturn(List.of(activeTemplate()));

        mockMvc.perform(put(TEMPLATES_URL + "/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[3,1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3));

        verify(drawingTemplateService).reorderTemplates(List.of(3L, 1L, 2L));
        verify(drawingTemplateService, never()).updateTemplate(any(), any(), any(), anyBoolean());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reorderDrawingTemplates_shouldReturn400_forInvalidIds() throws Exception {
        when(drawingTemplateService.reorderTemplates(any()))
                .thenThrow(new ValidationException("Lista szablonów do ustawienia kolejności jest nieaktualna. Odśwież stronę."));

        mockMvc.perform(put(TEMPLATES_URL + "/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,1]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Lista szablonów do ustawienia kolejności jest nieaktualna. Odśwież stronę."));
    }

    private static DrawingMarkerDTO marker(long id, String name, int displayOrder) {
        return new DrawingMarkerDTO(id, name, DrawingMarkerShape.CIRCLE, "#ff0000", true, displayOrder);
    }

    private static boolean isModzelRequest(DrawingMarkerRequest request) {
        return "Modzel".equals(request.getName()) && request.getShape() == DrawingMarkerShape.CIRCLE
                && "#FF0000".equals(request.getColor()) && Boolean.TRUE.equals(request.getFilled());
    }

    @Test
    @WithMockUser(roles = "USER")
    void drawingMarkers_shouldReturn403_forUser_onEveryEndpoint() throws Exception {
        mockMvc.perform(get(MARKERS_URL))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(MARKERS_URL).contentType(MediaType.APPLICATION_JSON).content(MARKER_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(MARKERS_URL + "/1").contentType(MediaType.APPLICATION_JSON).content(MARKER_JSON))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(MARKERS_URL + "/1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(MARKERS_URL + "/order").contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[1]}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(drawingMarkerService);
    }

    @Test
    @WithMockUser(roles = "MASTER")
    void drawingMarkers_shouldReturn403_forMaster() throws Exception {
        mockMvc.perform(get(MARKERS_URL))
                .andExpect(status().isForbidden());

        verifyNoInteractions(drawingMarkerService);
    }

    @Test
    void drawingMarkers_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get(MARKERS_URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(drawingMarkerService);
    }

    @Test
    @WithMockUser(roles = "COMPANY")
    void getDrawingMarkers_shouldReturnListInOrder() throws Exception {
        when(drawingMarkerService.getMarkers()).thenReturn(List.of(marker(2L, "Modzel", 1), marker(1L, "Odcisk", 2)));

        mockMvc.perform(get(MARKERS_URL))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"id\":2,\"name\":\"Modzel\",\"shape\":\"CIRCLE\",\"color\":\"#ff0000\","
                        + "\"filled\":true,\"displayOrder\":1},{\"id\":1,\"name\":\"Odcisk\",\"shape\":\"CIRCLE\","
                        + "\"color\":\"#ff0000\",\"filled\":true,\"displayOrder\":2}]", true));
    }

    @Test
    @WithMockUser(roles = "COMPANY")
    void createDrawingMarker_shouldPassJsonRequest_andReturnDto() throws Exception {
        when(drawingMarkerService.createMarker(any())).thenReturn(marker(5L, "Modzel", 4));

        mockMvc.perform(post(MARKERS_URL).contentType(MediaType.APPLICATION_JSON).content(MARKER_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.color").value("#ff0000"))
                .andExpect(jsonPath("$.displayOrder").value(4));

        verify(drawingMarkerService).createMarker(argThat(CardSettingsControllerTest::isModzelRequest));
    }

    @Test
    @WithMockUser(roles = "COMPANY")
    void createDrawingMarker_shouldReturn409_forDuplicate_and400_forLimit() throws Exception {
        when(drawingMarkerService.createMarker(any()))
                .thenThrow(new ConflictException("Oznaczenie o tej nazwie już istnieje."))
                .thenThrow(new ValidationException("Osiągnięto limit 30 oznaczeń."));

        mockMvc.perform(post(MARKERS_URL).contentType(MediaType.APPLICATION_JSON).content(MARKER_JSON))
                .andExpect(status().isConflict())
                .andExpect(content().string("Oznaczenie o tej nazwie już istnieje."));
        mockMvc.perform(post(MARKERS_URL).contentType(MediaType.APPLICATION_JSON).content(MARKER_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Osiągnięto limit 30 oznaczeń."));
    }

    @Test
    @WithMockUser(roles = "COMPANY")
    void createDrawingMarker_unknownShape_shouldReturn400_withoutCallingService() throws Exception {
        mockMvc.perform(post(MARKERS_URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Modzel\",\"shape\":\"STAR\",\"color\":\"#ff0000\",\"filled\":true}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(drawingMarkerService);
    }

    @Test
    @WithMockUser(roles = "COMPANY")
    void updateDrawingMarker_shouldPassIdAndRequest() throws Exception {
        when(drawingMarkerService.updateMarker(eq(5L), any())).thenReturn(marker(5L, "Modzel", 2));

        mockMvc.perform(put(MARKERS_URL + "/5").contentType(MediaType.APPLICATION_JSON).content(MARKER_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.name").value("Modzel"));

        verify(drawingMarkerService).updateMarker(eq(5L), argThat(CardSettingsControllerTest::isModzelRequest));
        verify(drawingMarkerService, never()).reorderMarkers(any());
    }

    @Test
    @WithMockUser(roles = "COMPANY")
    void updateDrawingMarker_shouldReturn404_whenUnknown() throws Exception {
        when(drawingMarkerService.updateMarker(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Drawing marker not found with given id: 99"));

        mockMvc.perform(put(MARKERS_URL + "/99").contentType(MediaType.APPLICATION_JSON).content(MARKER_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "COMPANY")
    void deleteDrawingMarker_shouldReturn204() throws Exception {
        mockMvc.perform(delete(MARKERS_URL + "/5"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(drawingMarkerService).deleteMarker(5L);
    }

    @Test
    @WithMockUser(roles = "COMPANY")
    void reorderDrawingMarkers_shouldPassIdsInOrder() throws Exception {
        when(drawingMarkerService.reorderMarkers(List.of(3L, 1L, 2L)))
                .thenReturn(List.of(marker(3L, "C", 1), marker(1L, "A", 2), marker(2L, "B", 3)));

        mockMvc.perform(put(MARKERS_URL + "/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[3,1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[1].id").value(1))
                .andExpect(jsonPath("$[2].id").value(2));

        verify(drawingMarkerService).reorderMarkers(List.of(3L, 1L, 2L));
        verify(drawingMarkerService, never()).updateMarker(any(), any());
    }

    @Test
    @WithMockUser(roles = "COMPANY")
    void reorderDrawingMarkers_shouldReturn400_forInvalidIds() throws Exception {
        when(drawingMarkerService.reorderMarkers(any()))
                .thenThrow(new ValidationException("Lista oznaczeń do ustawienia kolejności jest nieaktualna. Odśwież stronę."));

        mockMvc.perform(put(MARKERS_URL + "/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Lista oznaczeń do ustawienia kolejności jest nieaktualna. Odśwież stronę."));
    }
}

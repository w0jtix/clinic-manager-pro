package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.ClientDrawingDTO;
import com.clinic.clinicmanager.DTO.DrawItem;
import com.clinic.clinicmanager.DTO.DrawMarkerItem;
import com.clinic.clinicmanager.DTO.DrawStrokeItem;
import com.clinic.clinicmanager.DTO.DrawTextItem;
import com.clinic.clinicmanager.DTO.DrawingGroupDTO;
import com.clinic.clinicmanager.DTO.DrawingLayerDTO;
import com.clinic.clinicmanager.DTO.DrawingMarkerDTO;
import com.clinic.clinicmanager.DTO.DrawingSheetDTO;
import com.clinic.clinicmanager.DTO.DrawingTemplateImageRefDTO;
import com.clinic.clinicmanager.DTO.DrawingTemplateRefDTO;
import com.clinic.clinicmanager.DTO.DrawingUserDTO;
import com.clinic.clinicmanager.config.security.CardSecurityConfig;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.DrawingConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.constants.DrawingMarkerShape;
import com.clinic.clinicmanager.service.ClientDrawingService;
import com.clinic.clinicmanager.service.DrawingMarkerService;
import com.clinic.clinicmanager.service.DrawingTemplateService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientDrawingController.class)
@Import({WebSecurityConfig.class, CardSecurityConfig.class, AuthEntryPointJwt.class})
class ClientDrawingControllerTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};
    private static final String LAYER_URL = "/api/cards/1/drawing/sheets/12/layers/7";
    private static final String SHEETS_URL = "/api/cards/1/drawing/sheets";
    private static final String ITEMS_JSON = "[{\"kind\":\"stroke\",\"width\":4.0,\"pressureSensitive\":true,"
            + "\"points\":[[10.0,20.0,0.5],[11.0,30.0,1.0]]},"
            + "{\"kind\":\"text\",\"text\":\"Modzel\",\"x\":30.0,\"y\":40.0,\"size\":24.0,\"bold\":true},"
            + "{\"kind\":\"marker\",\"markerId\":3,\"shape\":\"CIRCLE\",\"color\":\"#ff0000\",\"filled\":true,"
            + "\"name\":\"Modzel\",\"x\":50.0,\"y\":60.0,\"size\":20.0}]";
    private static final String IMAGE_URL = "/api/cards/drawing-template-images/30/image";

    @Autowired MockMvc mockMvc;

    @MockBean ClientDrawingService clientDrawingService;
    @MockBean DrawingTemplateService drawingTemplateService;
    @MockBean DrawingMarkerService drawingMarkerService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private static ClientDrawingDTO drawing() {
        DrawingLayerDTO anna = new DrawingLayerDTO(31L, 7L, "Anna", "#2563eb", 4L,
                LocalDateTime.of(2026, 10, 2, 10, 15), 9L, "Ola");
        DrawingLayerDTO old = new DrawingLayerDTO(21L, 8L, "recepcja", "#6b7280", 1L,
                LocalDateTime.of(2026, 9, 1, 7, 0), 8L, "recepcja");
        DrawingGroupDTO feet = new DrawingGroupDTO(
                new DrawingTemplateRefDTO(3L, "Stopy", false, new DrawingTemplateImageRefDTO(30L, 1536, 2048)),
                new DrawingSheetDTO(12L, 2, 3L, 30L, 1536, 2048,
                        LocalDateTime.of(2026, 9, 1, 8, 0), null, List.of(anna)),
                List.of(new DrawingSheetDTO(11L, 1, 3L, 29L, 1536, 2048,
                        LocalDateTime.of(2026, 6, 15, 9, 30), LocalDateTime.of(2026, 9, 1, 8, 0), List.of(old))));
        DrawingGroupDTO hands = new DrawingGroupDTO(
                new DrawingTemplateRefDTO(4L, "Dłonie", false, null), null, List.of());
        DrawingGroupDTO heels = new DrawingGroupDTO(
                new DrawingTemplateRefDTO(5L, "Pięty", true, null),
                null,
                List.of(new DrawingSheetDTO(13L, 1, 5L, null, 1536, 2048,
                        LocalDateTime.of(2026, 5, 4, 12, 0), LocalDateTime.of(2026, 8, 1, 8, 0), List.of())));
        return new ClientDrawingDTO(new DrawingUserDTO(7L, "Anna", "#2563eb"), List.of(feet, hands, heels));
    }

    private static String layerBody(String items, String expectedVersion) {
        return "{\"items\":" + items + ",\"expectedVersion\":" + expectedVersion + "}";
    }

    private static String sheetBody(String templateId, String closeSheetId, String items) {
        return "{\"templateId\":" + templateId + ",\"closeSheetId\":" + closeSheetId + ",\"items\":" + items + "}";
    }

    private static MockHttpServletRequestBuilder putLayer(String body) {
        return put(LAYER_URL).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder postSheet(String body) {
        return post(SHEETS_URL).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static boolean isSampleItems(List<DrawItem> items) {
        return items != null && items.size() == 3
                && items.get(0) instanceof DrawStrokeItem stroke && stroke.points().length == 2
                && stroke.points()[1][1] == 30.0 && Boolean.TRUE.equals(stroke.pressureSensitive())
                && items.get(1) instanceof DrawTextItem text && "Modzel".equals(text.text())
                && items.get(2) instanceof DrawMarkerItem marker && marker.markerId() == 3L
                && marker.shape() == DrawingMarkerShape.CIRCLE && "#ff0000".equals(marker.color());
    }

    @Test
    void getDrawing_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/cards/1/drawing"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(clientDrawingService);
    }

    @Test
    void templateImage_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get(IMAGE_URL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(drawingTemplateService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void getDrawing_shouldReturn200_withGroupsShape() throws Exception {
        when(clientDrawingService.getDrawing(1L)).thenReturn(drawing());

        mockMvc.perform(get("/api/cards/1/drawing"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.activeTemplate").doesNotExist())
                .andExpect(jsonPath("$.currentSheet").doesNotExist())
                .andExpect(jsonPath("$.currentUser.userId").value(7))
                .andExpect(jsonPath("$.currentUser.name").value("Anna"))
                .andExpect(jsonPath("$.currentUser.color").value("#2563eb"))
                .andExpect(jsonPath("$.groups.length()").value(3))
                .andExpect(jsonPath("$.groups[0].template.id").value(3))
                .andExpect(jsonPath("$.groups[0].template.name").value("Stopy"))
                .andExpect(jsonPath("$.groups[0].template.archived").value(false))
                .andExpect(jsonPath("$.groups[0].template.image.id").value(30))
                .andExpect(jsonPath("$.groups[0].template.image.width").value(1536))
                .andExpect(jsonPath("$.groups[0].template.image.height").value(2048))
                .andExpect(jsonPath("$.groups[0].template.image.sizeBytes").doesNotExist())
                .andExpect(jsonPath("$.groups[0].currentSheet.id").value(12))
                .andExpect(jsonPath("$.groups[0].currentSheet.sheetNumber").value(2))
                .andExpect(jsonPath("$.groups[0].currentSheet.templateId").value(3))
                .andExpect(jsonPath("$.groups[0].currentSheet.templateImageId").value(30))
                .andExpect(jsonPath("$.groups[0].currentSheet.width").value(1536))
                .andExpect(jsonPath("$.groups[0].currentSheet.height").value(2048))
                .andExpect(jsonPath("$.groups[0].currentSheet.createdAt").value("2026-09-01T08:00:00"))
                .andExpect(jsonPath("$.groups[0].currentSheet.closedAt").isEmpty())
                .andExpect(jsonPath("$.groups[0].currentSheet.layers[0].id").value(31))
                .andExpect(jsonPath("$.groups[0].currentSheet.layers[0].authorName").value("Anna"))
                .andExpect(jsonPath("$.groups[0].currentSheet.layers[0].color").value("#2563eb"))
                .andExpect(jsonPath("$.groups[0].currentSheet.layers[0].version").value(4))
                .andExpect(jsonPath("$.groups[0].currentSheet.layers[0].updatedAt").value("2026-10-02T10:15:00"))
                .andExpect(jsonPath("$.groups[0].currentSheet.layers[0].updatedByName").value("Ola"))
                .andExpect(jsonPath("$.groups[0].closedSheets[0].id").value(11))
                .andExpect(jsonPath("$.groups[0].closedSheets[0].templateImageId").value(29))
                .andExpect(jsonPath("$.groups[0].closedSheets[0].createdAt").value("2026-06-15T09:30:00"))
                .andExpect(jsonPath("$.groups[0].closedSheets[0].closedAt").value("2026-09-01T08:00:00"))
                .andExpect(jsonPath("$.groups[0].closedSheets[0].layers[0].color").value("#6b7280"))
                .andExpect(jsonPath("$.groups[1].template.name").value("Dłonie"))
                .andExpect(jsonPath("$.groups[1].template.image").isEmpty())
                .andExpect(jsonPath("$.groups[1].currentSheet").isEmpty())
                .andExpect(jsonPath("$.groups[1].closedSheets").isEmpty())
                .andExpect(jsonPath("$.groups[2].template.archived").value(true))
                .andExpect(jsonPath("$.groups[2].closedSheets[0].templateImageId").isEmpty())
                .andExpect(jsonPath("$.groups[2].closedSheets[0].createdAt").value("2026-05-04T12:00:00"))
                .andExpect(jsonPath("$.groups[2].closedSheets[0].closedAt").value("2026-08-01T08:00:00"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getDrawing_shouldReturnEmptyGroups() throws Exception {
        when(clientDrawingService.getDrawing(1L)).thenReturn(new ClientDrawingDTO(
                new DrawingUserDTO(7L, "Anna", "#2563eb"), List.of()));

        mockMvc.perform(get("/api/cards/1/drawing"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"currentUser\":{\"userId\":7,\"name\":\"Anna\",\"color\":\"#2563eb\"},"
                        + "\"groups\":[]}", true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getDrawing_shouldReturn404_whenClientHidden() throws Exception {
        when(clientDrawingService.getDrawing(1L)).thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/api/cards/1/drawing"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getLayerItems_shouldReturnJson_withNoStore() throws Exception {
        when(clientDrawingService.getLayerItems(1L, 12L, 31L)).thenReturn(ITEMS_JSON.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/cards/1/drawing/sheets/12/layers/31/items"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].kind").value("stroke"))
                .andExpect(jsonPath("$[0].points[1][1]").value(30.0))
                .andExpect(jsonPath("$[1].text").value("Modzel"))
                .andExpect(jsonPath("$[2].color").value("#ff0000"))
                .andExpect(content().bytes(ITEMS_JSON.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getLayerItems_shouldReturnEmptyArray() throws Exception {
        when(clientDrawingService.getLayerItems(1L, 12L, 31L)).thenReturn("[]".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get("/api/cards/1/drawing/sheets/12/layers/31/items"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]", true));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getLayerItems_shouldReturn404_forLayerOutsideClient() throws Exception {
        when(clientDrawingService.getLayerItems(1L, 12L, 99L)).thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/api/cards/1/drawing/sheets/12/layers/99/items"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getLayerItems_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/cards/1/drawing/sheets/12/layers/31/items"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(clientDrawingService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void logSheetView_shouldReturn204_withoutBody() throws Exception {
        mockMvc.perform(post(SHEETS_URL + "/12/view"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(clientDrawingService).logSheetView(1L, 12L);
    }

    @Test
    @WithMockUser(roles = "USER")
    void logSheetView_shouldReturn404_forSheetOutsideClient() throws Exception {
        doThrow(new ResourceNotFoundException("Drawing sheet not found with given id: 99"))
                .when(clientDrawingService).logSheetView(1L, 99L);

        mockMvc.perform(post(SHEETS_URL + "/99/view"))
                .andExpect(status().isNotFound());
    }

    @Test
    void logSheetView_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(post(SHEETS_URL + "/12/view"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(clientDrawingService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void drawingMarkers_shouldReturn200_forUser() throws Exception {
        when(drawingMarkerService.getMarkers()).thenReturn(List.of(
                new DrawingMarkerDTO(2L, "Modzel", DrawingMarkerShape.CIRCLE, "#ff0000", true, 1),
                new DrawingMarkerDTO(1L, "Odcisk", DrawingMarkerShape.CROSS, "#2563eb", false, 2)));

        mockMvc.perform(get("/api/cards/drawing-markers"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"id\":2,\"name\":\"Modzel\",\"shape\":\"CIRCLE\",\"color\":\"#ff0000\","
                        + "\"filled\":true,\"displayOrder\":1},{\"id\":1,\"name\":\"Odcisk\",\"shape\":\"CROSS\","
                        + "\"color\":\"#2563eb\",\"filled\":false,\"displayOrder\":2}]", true));
    }

    @Test
    void drawingMarkers_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/cards/drawing-markers"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(drawingMarkerService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void templateImage_shouldReturnPng_withImmutableCache() throws Exception {
        when(drawingTemplateService.getTemplateImage(30L)).thenReturn(PNG);

        mockMvc.perform(get(IMAGE_URL))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", allOf(
                        containsString("private"), containsString("max-age=31536000"), containsString("immutable"))))
                .andExpect(header().string("Cache-Control", not(containsString("no-store"))))
                .andExpect(content().bytes(PNG));
    }

    @Test
    @WithMockUser(roles = "USER")
    void templateImage_shouldReturn404_whenUnknown() throws Exception {
        when(drawingTemplateService.getTemplateImage(30L)).thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get(IMAGE_URL))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void oldEndpoints_shouldReturn404() throws Exception {
        mockMvc.perform(get("/api/cards/drawing-templates/3/image"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/cards/1/drawing/sheets/12/image"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/cards/1/drawing/sheets/12/image").contentType(MediaType.APPLICATION_JSON)
                        .content(layerBody("[]", "1")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/cards/1/drawing/sheets/12/layers/31/image"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(LAYER_URL + "/image").contentType(MediaType.APPLICATION_JSON)
                        .content(layerBody("[]", "1")))
                .andExpect(status().isNotFound());

        verifyNoInteractions(clientDrawingService, drawingTemplateService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void createSheet_withItems_shouldPassTemplateIdAndParsedItems() throws Exception {
        when(clientDrawingService.createSheet(eq(1L), eq(3L), any(), isNull())).thenReturn(drawing());

        mockMvc.perform(postSheet(sheetBody("3", "null", ITEMS_JSON)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].currentSheet.id").value(12))
                .andExpect(jsonPath("$.groups[0].currentSheet.layers[0].authorName").value("Anna"));

        verify(clientDrawingService).createSheet(eq(1L), eq(3L), argThat(ClientDrawingControllerTest::isSampleItems), isNull());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createSheet_blankWithCloseSheetId_shouldPassNullItems() throws Exception {
        when(clientDrawingService.createSheet(eq(1L), eq(3L), isNull(), eq(12L))).thenReturn(drawing());

        mockMvc.perform(postSheet("{\"templateId\":3,\"closeSheetId\":12}"))
                .andExpect(status().isOk());

        verify(clientDrawingService).createSheet(eq(1L), eq(3L), isNull(), eq(12L));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createSheet_withoutItemsAndOpenSheet_shouldReturn400() throws Exception {
        when(clientDrawingService.createSheet(eq(1L), eq(3L), any(), isNull()))
                .thenThrow(new ValidationException("Rysunek jest pusty."));

        mockMvc.perform(postSheet(sheetBody("3", "null", "[]")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Rysunek jest pusty."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createSheet_unknownItemKind_shouldReturn400_withoutCallingService() throws Exception {
        mockMvc.perform(postSheet(sheetBody("3", "null", "[{\"kind\":\"circle\",\"x\":1,\"y\":1}]")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(clientDrawingService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void createSheet_withoutTemplateId_shouldReturn400() throws Exception {
        when(clientDrawingService.createSheet(eq(1L), isNull(), any(), isNull()))
                .thenThrow(new ValidationException("Nie wybrano szablonu rysunku."));

        mockMvc.perform(postSheet("{\"items\":" + ITEMS_JSON + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Nie wybrano szablonu rysunku."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createSheet_unknownTemplate_shouldReturn404() throws Exception {
        when(clientDrawingService.createSheet(eq(1L), eq(99L), any(), isNull()))
                .thenThrow(new ResourceNotFoundException("Drawing template not found with given id: 99"));

        mockMvc.perform(postSheet(sheetBody("99", "null", ITEMS_JSON)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createSheet_archivedTemplate_shouldReturn409() throws Exception {
        when(clientDrawingService.createSheet(eq(1L), eq(5L), any(), isNull()))
                .thenThrow(new ConflictException("Szablon jest zarchiwizowany."));

        mockMvc.perform(postSheet(sheetBody("5", "null", ITEMS_JSON)))
                .andExpect(status().isConflict())
                .andExpect(content().string("Szablon jest zarchiwizowany."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createSheet_invalidTemplateId_shouldReturn400() throws Exception {
        mockMvc.perform(postSheet(sheetBody("\"abc\"", "null", "null")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(clientDrawingService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void createSheet_shouldReturn409_whenChangedElsewhere() throws Exception {
        when(clientDrawingService.createSheet(eq(1L), eq(3L), any(), eq(12L)))
                .thenThrow(new ConflictException("Bieżąca kartka została zmieniona na innym urządzeniu. Odśwież rysunek."));

        mockMvc.perform(postSheet(sheetBody("3", "12", "null")))
                .andExpect(status().isConflict())
                .andExpect(content().string("Bieżąca kartka została zmieniona na innym urządzeniu. Odśwież rysunek."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createSheet_shouldReturn400_whenCurrentEmpty() throws Exception {
        when(clientDrawingService.createSheet(eq(1L), eq(3L), any(), eq(12L)))
                .thenThrow(new ValidationException("Bieżąca kartka jest pusta."));

        mockMvc.perform(postSheet(sheetBody("3", "12", "null")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Bieżąca kartka jest pusta."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateLayerItems_shouldUsePut_andPassAuthorVersionAndItems() throws Exception {
        when(clientDrawingService.updateLayerItems(eq(1L), eq(12L), eq(7L), any(), eq(4L))).thenReturn(drawing());

        mockMvc.perform(putLayer(layerBody(ITEMS_JSON, "4")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].currentSheet.layers[0].version").value(4));

        verify(clientDrawingService).updateLayerItems(eq(1L), eq(12L), eq(7L), argThat(ClientDrawingControllerTest::isSampleItems), eq(4L));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateLayerItems_emptyList_shouldPassEmptyList() throws Exception {
        when(clientDrawingService.updateLayerItems(eq(1L), eq(12L), eq(7L), any(), eq(1L))).thenReturn(drawing());

        mockMvc.perform(putLayer(layerBody("[]", "1")))
                .andExpect(status().isOk());

        verify(clientDrawingService).updateLayerItems(eq(1L), eq(12L), eq(7L), argThat(List::isEmpty), eq(1L));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateLayerItems_shouldReturn400_forInvalidItemsFromService() throws Exception {
        when(clientDrawingService.updateLayerItems(eq(1L), eq(12L), eq(7L), any(), eq(1L)))
                .thenThrow(new ValidationException("Element rysunku wychodzi poza kartkę."));

        mockMvc.perform(putLayer(layerBody(ITEMS_JSON, "1")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Element rysunku wychodzi poza kartkę."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateLayerItems_unparsableItems_shouldReturn400_withoutCallingService() throws Exception {
        for (String items : List.of(
                "[{\"kind\":\"circle\",\"x\":1,\"y\":1}]",
                "[{\"width\":2,\"points\":[[1,1,1]]}]",
                "[{\"kind\":\"stroke\",\"width\":2,\"pressureSensitive\":true,\"points\":[[NaN,1,1]]}]",
                "[{\"kind\":\"marker\",\"shape\":\"STAR\",\"color\":\"#ff0000\",\"filled\":true,"
                        + "\"name\":\"A\",\"x\":1,\"y\":1,\"size\":5}]",
                "{\"kind\":\"text\"}")) {
            mockMvc.perform(putLayer(layerBody(items, "1")))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(clientDrawingService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateLayerItems_shouldReturn409_whenTemplateArchived() throws Exception {
        when(clientDrawingService.updateLayerItems(eq(1L), eq(12L), eq(7L), any(), eq(4L)))
                .thenThrow(new ConflictException("Szablon jest zarchiwizowany. Kartka jest tylko do podglądu."));

        mockMvc.perform(putLayer(layerBody(ITEMS_JSON, "4")))
                .andExpect(status().isConflict())
                .andExpect(content().string("Szablon jest zarchiwizowany. Kartka jest tylko do podglądu."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateLayerItems_shouldReturn403_forOtherAuthorLayer() throws Exception {
        when(clientDrawingService.updateLayerItems(eq(1L), eq(12L), eq(7L), any(), eq(4L)))
                .thenThrow(new AccessDeniedException("Możesz zmieniać tylko własne zaznaczenia."));

        mockMvc.perform(putLayer(layerBody(ITEMS_JSON, "4")))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Możesz zmieniać tylko własne zaznaczenia."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateLayerItems_shouldReturn409_withConflictJson() throws Exception {
        when(clientDrawingService.updateLayerItems(eq(1L), eq(12L), eq(7L), any(), eq(3L)))
                .thenThrow(new DrawingConflictException(4L));

        mockMvc.perform(putLayer(layerBody(ITEMS_JSON, "3")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("DRAWING_CONFLICT"))
                .andExpect(jsonPath("$.currentVersion").value(4));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateLayerItems_shouldReturn409_whenClosed() throws Exception {
        when(clientDrawingService.updateLayerItems(eq(1L), eq(12L), eq(7L), any(), eq(4L)))
                .thenThrow(new ConflictException("Kartka jest zamknięta."));

        mockMvc.perform(putLayer(layerBody(ITEMS_JSON, "4")))
                .andExpect(status().isConflict())
                .andExpect(content().string("Kartka jest zamknięta."));
    }

    @Test
    @WithMockUser(roles = {"USER", "ADMIN"})
    void updateLayerItems_shouldReturn403_whenAdminTargetsOtherAuthorLayer() throws Exception {
        when(clientDrawingService.updateLayerItems(eq(1L), eq(12L), eq(7L), any(), eq(0L)))
                .thenThrow(new AccessDeniedException("Możesz zmieniać tylko własne zaznaczenia."));

        mockMvc.perform(putLayer(layerBody(ITEMS_JSON, "0")))
                .andExpect(status().isForbidden())
                .andExpect(content().string("Możesz zmieniać tylko własne zaznaczenia."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateLayerItems_shouldReturn400_forInvalidVersion() throws Exception {
        mockMvc.perform(putLayer(layerBody(ITEMS_JSON, "\"abc\"")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(clientDrawingService);
    }
}

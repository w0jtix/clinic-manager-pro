package com.clinic.clinicmanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.DrawingMarkerDTO;
import com.clinic.clinicmanager.DTO.request.DrawingMarkerRequest;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.DrawingMarker;
import com.clinic.clinicmanager.model.constants.DrawingMarkerShape;
import com.clinic.clinicmanager.repo.DrawingMarkerRepo;
import com.clinic.clinicmanager.service.impl.DrawingMarkerServiceImpl;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DrawingMarkerServiceImplTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);
    private static final String NAME_MESSAGE = "Nazwa oznaczenia jest wymagana (maksymalnie 40 znaków).";
    private static final String SHAPE_MESSAGE = "Wybierz kształt oznaczenia.";
    private static final String COLOR_MESSAGE = "Nieprawidłowy kolor oznaczenia (format #RRGGBB).";
    private static final String DUPLICATE_MESSAGE = "Oznaczenie o tej nazwie już istnieje.";
    private static final String LIMIT_MESSAGE = "Osiągnięto limit 30 oznaczeń.";
    private static final String ORDER_MESSAGE = "Lista oznaczeń do ustawienia kolejności jest nieaktualna. Odśwież stronę.";
    private static final long USER_ID = 7L;

    @Mock DrawingMarkerRepo markerRepo;
    @Mock AuditLogService auditLogService;

    @InjectMocks DrawingMarkerServiceImpl service;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockedStatic<SessionUtils> session;

    @BeforeEach
    void setUp() {
        session = mockStatic(SessionUtils.class);
        session.when(SessionUtils::getUserIdFromSession).thenReturn(USER_ID);
    }

    @AfterEach
    void tearDown() {
        session.close();
    }

    private static DrawingMarker marker(long id, String name, int displayOrder) {
        return DrawingMarker.builder()
                .id(id).name(name).shape(DrawingMarkerShape.CIRCLE).color("#ff0000").filled(true)
                .displayOrder(displayOrder).createdAt(CREATED_AT).updatedAt(CREATED_AT)
                .build();
    }

    private static DrawingMarkerRequest request(String name, DrawingMarkerShape shape, String color, Boolean filled) {
        return new DrawingMarkerRequest(name, shape, color, filled);
    }

    private static DrawingMarkerRequest modzel() {
        return request("Modzel", DrawingMarkerShape.CIRCLE, "#FF0000", true);
    }

    private void stubSave() {
        when(markerRepo.saveAndFlush(any(DrawingMarker.class))).thenAnswer(invocation -> {
            DrawingMarker marker = invocation.getArgument(0);
            if (marker.getId() == null) {
                marker.setId(9L);
            }
            return marker;
        });
    }

    private void stubCreateContext(long count, int maxOrder) {
        when(markerRepo.count()).thenReturn(count);
        when(markerRepo.existsByNameIgnoreCase(anyString())).thenReturn(false);
        when(markerRepo.findMaxDisplayOrder()).thenReturn(maxOrder);
        stubSave();
    }

    private void stubOrderedList(List<DrawingMarker> markers) {
        when(markerRepo.findAllByOrderByDisplayOrderAscIdAsc()).thenAnswer(invocation -> markers.stream()
                .sorted(Comparator.comparing(DrawingMarker::getDisplayOrder).thenComparing(DrawingMarker::getId))
                .toList());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, Map.class);
    }

    @Test
    void create_normalizesColorToLowercase_putsAtEndOfOrder_andAuditsCreate() {
        stubCreateContext(2, 2);

        DrawingMarkerDTO result = service.createMarker(modzel());

        assertThat(result.getId()).isEqualTo(9L);
        assertThat(result.getName()).isEqualTo("Modzel");
        assertThat(result.getShape()).isEqualTo(DrawingMarkerShape.CIRCLE);
        assertThat(result.getColor()).isEqualTo("#ff0000");
        assertThat(result.getFilled()).isTrue();
        assertThat(result.getDisplayOrder()).isEqualTo(3);

        ArgumentCaptor<DrawingMarker> saved = ArgumentCaptor.forClass(DrawingMarker.class);
        verify(markerRepo).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getColor()).isEqualTo("#ff0000");
        assertThat(saved.getValue().getCreatedByUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getCreatedAt()).isNotNull();
        assertThat(saved.getValue().getUpdatedAt()).isNotNull();

        InOrder inOrder = inOrder(markerRepo);
        inOrder.verify(markerRepo).acquireTransactionLock(anyLong());
        inOrder.verify(markerRepo).count();
        inOrder.verify(markerRepo).saveAndFlush(any());

        ArgumentCaptor<Object> created = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).logCreate(eq("DrawingMarker"), eq(9L), eq("Modzel"), created.capture());
        assertThat(toMap(created.getValue()))
                .containsEntry("name", "Modzel")
                .containsEntry("shape", "CIRCLE")
                .containsEntry("color", "#ff0000")
                .containsEntry("filled", true)
                .containsEntry("displayOrder", 3);
    }

    @Test
    void create_firstMarker_getsOrderOne_andNullFilledMeansFalse() {
        stubCreateContext(0, 0);

        DrawingMarkerDTO result = service.createMarker(request("  Odcisk ", DrawingMarkerShape.HEXAGON, "#00aa00", null));

        assertThat(result.getName()).isEqualTo("Odcisk");
        assertThat(result.getDisplayOrder()).isEqualTo(1);
        assertThat(result.getFilled()).isFalse();
    }

    @Test
    void create_duplicateNameIgnoringCaseAfterTrim_409() {
        when(markerRepo.count()).thenReturn(1L);
        when(markerRepo.existsByNameIgnoreCase("modzel")).thenReturn(true);

        assertThatThrownBy(() -> service.createMarker(request(" modzel ", DrawingMarkerShape.CIRCLE, "#ff0000", true)))
                .isInstanceOf(ConflictException.class)
                .hasMessage(DUPLICATE_MESSAGE);
        verify(markerRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void create_concurrentDuplicate_fromUniqueIndex_409() {
        when(markerRepo.count()).thenReturn(1L);
        when(markerRepo.existsByNameIgnoreCase("Modzel")).thenReturn(false);
        when(markerRepo.saveAndFlush(any(DrawingMarker.class)))
                .thenThrow(new DataIntegrityViolationException("uq_drawing_marker_name"));

        assertThatThrownBy(() -> service.createMarker(modzel()))
                .isInstanceOf(ConflictException.class)
                .hasMessage(DUPLICATE_MESSAGE);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void create_whenThirtyExist_400Limit() {
        when(markerRepo.count()).thenReturn(30L);

        assertThatThrownBy(() -> service.createMarker(modzel()))
                .isInstanceOf(ValidationException.class)
                .hasMessage(LIMIT_MESSAGE);
        verify(markerRepo, never()).existsByNameIgnoreCase(any());
        verify(markerRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void create_whenTwentyNineExist_isAllowed() {
        stubCreateContext(29, 29);

        assertThat(service.createMarker(modzel()).getDisplayOrder()).isEqualTo(30);
    }

    @Test
    void create_invalidInput_400_withoutTouchingRepo() {
        Map<DrawingMarkerRequest, String> cases = Map.of(
                request("Modzel", DrawingMarkerShape.CIRCLE, "red", true), COLOR_MESSAGE,
                request("Modzel", DrawingMarkerShape.CIRCLE, "#ff000", true), COLOR_MESSAGE,
                request("Modzel", DrawingMarkerShape.CIRCLE, "#gg0000", true), COLOR_MESSAGE,
                request("Modzel", DrawingMarkerShape.CIRCLE, null, true), COLOR_MESSAGE,
                request("", DrawingMarkerShape.CIRCLE, "#ff0000", true), NAME_MESSAGE,
                request("   ", DrawingMarkerShape.CIRCLE, "#ff0000", true), NAME_MESSAGE,
                request(null, DrawingMarkerShape.CIRCLE, "#ff0000", true), NAME_MESSAGE,
                request("x".repeat(41), DrawingMarkerShape.CIRCLE, "#ff0000", true), NAME_MESSAGE,
                request("Modzel", null, "#ff0000", true), SHAPE_MESSAGE);

        cases.forEach((request, message) -> assertThatThrownBy(() -> service.createMarker(request))
                .isInstanceOf(ValidationException.class)
                .hasMessage(message));
        assertThatThrownBy(() -> service.createMarker(null))
                .isInstanceOf(ValidationException.class)
                .hasMessage(NAME_MESSAGE);
        verifyNoInteractions(markerRepo, auditLogService);
    }

    @Test
    void create_acceptsFortyCharacterNameAfterTrim() {
        stubCreateContext(0, 0);

        DrawingMarkerDTO result = service.createMarker(request(" " + "x".repeat(40) + " ",
                DrawingMarkerShape.PLUS, "#ABCDEF", false));

        assertThat(result.getName()).hasSize(40);
        assertThat(result.getColor()).isEqualTo("#abcdef");
    }

    @Test
    void update_changesFields_keepsOrder_andAuditsBeforeAfter() {
        DrawingMarker existing = marker(5L, "Modzel", 2);
        when(markerRepo.lockById(5L)).thenReturn(Optional.of(existing));
        when(markerRepo.existsByNameIgnoreCaseAndIdNot("Odcisk", 5L)).thenReturn(false);
        stubSave();

        DrawingMarkerDTO result = service.updateMarker(5L, request(" Odcisk ", DrawingMarkerShape.TRIANGLE, "#00FF00", false));

        assertThat(result.getName()).isEqualTo("Odcisk");
        assertThat(result.getShape()).isEqualTo(DrawingMarkerShape.TRIANGLE);
        assertThat(result.getColor()).isEqualTo("#00ff00");
        assertThat(result.getFilled()).isFalse();
        assertThat(result.getDisplayOrder()).isEqualTo(2);
        assertThat(existing.getUpdatedAt()).isAfter(CREATED_AT);
        verify(markerRepo).saveAndFlush(existing);

        ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).logUpdate(eq("DrawingMarker"), eq(5L), eq("Odcisk"), before.capture(), after.capture());
        assertThat(toMap(before.getValue())).containsEntry("name", "Modzel").containsEntry("shape", "CIRCLE")
                .containsEntry("filled", true);
        assertThat(toMap(after.getValue())).containsEntry("name", "Odcisk").containsEntry("shape", "TRIANGLE")
                .containsEntry("color", "#00ff00").containsEntry("filled", false);
    }

    @Test
    void update_withoutChanges_doesNotSaveOrAudit() {
        DrawingMarker existing = marker(5L, "Modzel", 2);
        when(markerRepo.lockById(5L)).thenReturn(Optional.of(existing));

        DrawingMarkerDTO result = service.updateMarker(5L, request("Modzel", DrawingMarkerShape.CIRCLE, "#FF0000", true));

        assertThat(result.getName()).isEqualTo("Modzel");
        assertThat(existing.getUpdatedAt()).isEqualTo(CREATED_AT);
        verify(markerRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void update_changingOnlyCaseOfOwnName_isSaved() {
        DrawingMarker existing = marker(5L, "Modzel", 2);
        when(markerRepo.lockById(5L)).thenReturn(Optional.of(existing));
        stubSave();

        service.updateMarker(5L, request("modzel", DrawingMarkerShape.CIRCLE, "#ff0000", true));

        assertThat(existing.getName()).isEqualTo("modzel");
        verify(markerRepo).existsByNameIgnoreCaseAndIdNot("modzel", 5L);
        verify(auditLogService).logUpdate(eq("DrawingMarker"), eq(5L), eq("modzel"), any(), any());
    }

    @Test
    void update_nameOfOtherMarker_409() {
        DrawingMarker existing = marker(5L, "Modzel", 2);
        when(markerRepo.lockById(5L)).thenReturn(Optional.of(existing));
        when(markerRepo.existsByNameIgnoreCaseAndIdNot("Odcisk", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateMarker(5L, request("Odcisk", DrawingMarkerShape.CIRCLE, "#ff0000", true)))
                .isInstanceOf(ConflictException.class)
                .hasMessage(DUPLICATE_MESSAGE);
        assertThat(existing.getName()).isEqualTo("Modzel");
        verify(markerRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void update_unknown_404() {
        when(markerRepo.lockById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMarker(99L, modzel()))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void update_invalidColor_400_beforeLocking() {
        assertThatThrownBy(() -> service.updateMarker(5L, request("Modzel", DrawingMarkerShape.CIRCLE, "red", true)))
                .isInstanceOf(ValidationException.class)
                .hasMessage(COLOR_MESSAGE);
        verifyNoInteractions(markerRepo, auditLogService);
    }

    @Test
    void delete_hardDeletes_andAuditsDelete() {
        DrawingMarker existing = marker(5L, "Modzel", 2);
        when(markerRepo.lockById(5L)).thenReturn(Optional.of(existing));

        service.deleteMarker(5L);

        verify(markerRepo).delete(existing);
        verify(markerRepo).flush();
        ArgumentCaptor<Object> deleted = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).logDelete(eq("DrawingMarker"), eq(5L), eq("Modzel"), deleted.capture());
        assertThat(toMap(deleted.getValue())).containsEntry("id", 5L).containsEntry("color", "#ff0000");
    }

    @Test
    void delete_unknown_404() {
        when(markerRepo.lockById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteMarker(99L)).isInstanceOf(ResourceNotFoundException.class);
        verify(markerRepo, never()).delete(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void reorder_setsDisplayOrderOneToN_inGivenOrder_andAuditsChanged() {
        DrawingMarker first = marker(1L, "Modzel", 1);
        DrawingMarker second = marker(2L, "Odcisk", 2);
        DrawingMarker third = marker(3L, "Brodawka", 3);
        stubOrderedList(new ArrayList<>(List.of(first, second, third)));

        List<DrawingMarkerDTO> result = service.reorderMarkers(List.of(3L, 1L, 2L));

        assertThat(third.getDisplayOrder()).isEqualTo(1);
        assertThat(first.getDisplayOrder()).isEqualTo(2);
        assertThat(second.getDisplayOrder()).isEqualTo(3);
        assertThat(result).extracting(DrawingMarkerDTO::getId).containsExactly(3L, 1L, 2L);
        assertThat(result).extracting(DrawingMarkerDTO::getDisplayOrder).containsExactly(1, 2, 3);
        verify(markerRepo).saveAllAndFlush(List.of(third, first, second));
        verify(auditLogService, times(3)).logUpdate(eq("DrawingMarker"), any(), any(), any(), any());
        ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).logUpdate(eq("DrawingMarker"), eq(3L), eq("Brodawka"), before.capture(), after.capture());
        assertThat(toMap(before.getValue())).containsEntry("displayOrder", 3);
        assertThat(toMap(after.getValue())).containsEntry("displayOrder", 1);
    }

    @Test
    void reorder_sameOrder_savesAndAuditsNothing() {
        stubOrderedList(List.of(marker(1L, "Modzel", 1), marker(2L, "Odcisk", 2)));

        List<DrawingMarkerDTO> result = service.reorderMarkers(List.of(1L, 2L));

        assertThat(result).extracting(DrawingMarkerDTO::getId).containsExactly(1L, 2L);
        verify(markerRepo, never()).saveAllAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void reorder_invalidIds_400() {
        List<DrawingMarker> markers = List.of(marker(1L, "Modzel", 1), marker(2L, "Odcisk", 2),
                marker(3L, "Brodawka", 3));
        when(markerRepo.findAllByOrderByDisplayOrderAscIdAsc()).thenReturn(markers);

        for (List<Long> ids : Arrays.asList(List.of(1L, 2L), List.of(1L, 2L, 99L), List.of(1L, 2L, 3L, 99L),
                List.of(1L, 1L, 2L), List.of(1L, 2L, 3L, 3L), Arrays.asList(1L, 2L, null), List.<Long>of(), null)) {
            assertThatThrownBy(() -> service.reorderMarkers(ids))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage(ORDER_MESSAGE);
        }
        assertThat(markers).extracting(DrawingMarker::getDisplayOrder).containsExactly(1, 2, 3);
        verify(markerRepo, never()).saveAllAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void getMarkers_mapsAllFields_inRepoOrder() {
        DrawingMarker cross = DrawingMarker.builder()
                .id(4L).name("Odcisk").shape(DrawingMarkerShape.CROSS).color("#2563eb").filled(false)
                .displayOrder(1).createdAt(CREATED_AT).updatedAt(CREATED_AT)
                .build();
        when(markerRepo.findAllByOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(cross, marker(1L, "Modzel", 2)));

        List<DrawingMarkerDTO> result = service.getMarkers();

        assertThat(result).extracting(DrawingMarkerDTO::getId).containsExactly(4L, 1L);
        DrawingMarkerDTO first = result.get(0);
        assertThat(first.getName()).isEqualTo("Odcisk");
        assertThat(first.getShape()).isEqualTo(DrawingMarkerShape.CROSS);
        assertThat(first.getColor()).isEqualTo("#2563eb");
        assertThat(first.getFilled()).isFalse();
        assertThat(first.getDisplayOrder()).isEqualTo(1);
    }
}

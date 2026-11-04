package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.ClientDrawingDTO;
import com.clinic.clinicmanager.DTO.DrawItem;
import com.clinic.clinicmanager.DTO.DrawMarkerItem;
import com.clinic.clinicmanager.DTO.DrawStrokeItem;
import com.clinic.clinicmanager.DTO.DrawTextItem;
import com.clinic.clinicmanager.DTO.DrawingGroupDTO;
import com.clinic.clinicmanager.DTO.DrawingLayerDTO;
import com.clinic.clinicmanager.DTO.DrawingSheetDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.DrawingConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientCard;
import com.clinic.clinicmanager.model.DrawingLayer;
import com.clinic.clinicmanager.model.DrawingSheet;
import com.clinic.clinicmanager.model.DrawingTemplate;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.CardAccessAction;
import com.clinic.clinicmanager.model.constants.DrawingMarkerShape;
import com.clinic.clinicmanager.model.constants.DrawingSheetStatus;
import com.clinic.clinicmanager.repo.ClientCardRepo;
import com.clinic.clinicmanager.repo.ClientRepo;
import com.clinic.clinicmanager.repo.DrawingLayerRepo;
import com.clinic.clinicmanager.repo.DrawingSheetRepo;
import com.clinic.clinicmanager.repo.DrawingTemplateImageRepo;
import com.clinic.clinicmanager.repo.DrawingTemplateRepo;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.repo.projection.DrawingTemplateImageInfoProjection;
import com.clinic.clinicmanager.service.impl.ClientDrawingServiceImpl;
import com.clinic.clinicmanager.utils.CardEncryption;
import com.clinic.clinicmanager.utils.DrawingItems;
import com.clinic.clinicmanager.utils.ImageUtils;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientDrawingServiceImplTest {

    private static final String CARD_KEY = "0M4x9vvId7mHSSU2GWach6D8+iwTN7Bp2JnvXW2T7Ao=";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);
    private static final long CLIENT_ID = 1L;
    private static final long CARD_ID = 10L;
    private static final long ANNA = 7L;
    private static final long RECEPTION = 8L;
    private static final long ADMIN = 9L;
    private static final long FEET = 3L;
    private static final long HANDS = 4L;
    private static final long HEELS = 5L;
    private static final long NAILS = 6L;
    private static final long ANKLES = 7L;
    private static final long FEET_IMAGE = 30L;
    private static final String ARCHIVED_MESSAGE = "Szablon jest zarchiwizowany.";

    @Mock ClientRepo clientRepo;
    @Mock ClientCardRepo clientCardRepo;
    @Mock ClientCardService clientCardService;
    @Mock DrawingSheetRepo sheetRepo;
    @Mock DrawingLayerRepo layerRepo;
    @Mock DrawingTemplateRepo templateRepo;
    @Mock DrawingTemplateImageRepo templateImageRepo;
    @Mock UserRepo userRepo;
    @Mock FileStorage fileStorage;
    @Mock CardAccessLogService cardAccessLogService;
    @Spy CardEncryption cardEncryption = new CardEncryption(CARD_KEY);

    @InjectMocks ClientDrawingServiceImpl service;

    private MockedStatic<SessionUtils> session;
    private final Map<String, byte[]> files = new HashMap<>();
    private final List<DrawingSheet> sheets = new ArrayList<>();
    private final List<DrawingLayer> layers = new ArrayList<>();
    private final Map<Long, DrawingTemplate> templates = new LinkedHashMap<>();
    private final Map<Long, DrawingTemplateImageInfoProjection> images = new HashMap<>();
    private long nextSheetId = 50L;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        session = mockStatic(SessionUtils.class);
        loginAs(ANNA, false);
        addTemplate(FEET, "Stopy", 1, false, FEET_IMAGE);
        addTemplate(HANDS, "Dłonie", 2, false, null);
        images.put(FEET_IMAGE, imageInfo(FEET_IMAGE, FEET, 100, 200));

        lenient().doAnswer(invocation -> {
            files.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(fileStorage).put(anyString(), any());
        lenient().when(userRepo.findAllWithEmployeeByIdIn(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return users().stream().filter(user -> ids.contains(user.getId())).toList();
        });
        lenient().when(sheetRepo.findByCardIdOrderBySheetNumberDesc(CARD_ID)).thenAnswer(invocation -> sheets.stream()
                .sorted(Comparator.comparing(DrawingSheet::getSheetNumber).reversed())
                .toList());
        lenient().when(sheetRepo.lockByCardIdAndTemplateIdAndStatus(eq(CARD_ID), anyLong(), eq(DrawingSheetStatus.OPEN)))
                .thenAnswer(invocation -> {
                    Long templateId = invocation.getArgument(1);
                    return sheets.stream()
                            .filter(sheet -> sheet.getTemplateId().equals(templateId) && sheet.isOpen())
                            .findFirst();
                });
        lenient().when(sheetRepo.findMaxSheetNumber(eq(CARD_ID), anyLong())).thenAnswer(invocation -> {
            Long templateId = invocation.getArgument(1);
            return sheets.stream()
                    .filter(sheet -> sheet.getTemplateId().equals(templateId))
                    .mapToInt(DrawingSheet::getSheetNumber)
                    .max().orElse(0);
        });
        lenient().when(layerRepo.findBySheetIdInOrderByCreatedAtAscIdAsc(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return layers.stream().filter(layer -> ids.contains(layer.getSheet().getId())).toList();
        });
        lenient().when(layerRepo.existsBySheetId(anyLong())).thenAnswer(invocation -> {
            Long sheetId = invocation.getArgument(0);
            return layers.stream().anyMatch(layer -> layer.getSheet().getId().equals(sheetId));
        });
        lenient().when(sheetRepo.saveAndFlush(any(DrawingSheet.class))).thenAnswer(invocation -> {
            DrawingSheet sheet = invocation.getArgument(0);
            if (sheet.getId() == null) {
                sheet.setId(nextSheetId++);
                sheets.add(sheet);
            }
            return sheet;
        });
        lenient().when(layerRepo.saveAndFlush(any(DrawingLayer.class))).thenAnswer(invocation -> {
            DrawingLayer layer = invocation.getArgument(0);
            if (layer.getId() == null) {
                layer.setId(70L);
                layers.add(layer);
            }
            return layer;
        });
        lenient().doAnswer(invocation -> {
            layers.remove(invocation.<DrawingLayer>getArgument(0));
            return null;
        }).when(layerRepo).delete(any(DrawingLayer.class));
        lenient().when(templateRepo.findByArchivedFalseOrderByDisplayOrderAscIdAsc()).thenAnswer(invocation ->
                templates.values().stream()
                        .filter(template -> !template.isArchived())
                        .sorted(Comparator.comparing(DrawingTemplate::getDisplayOrder))
                        .toList());
        lenient().when(templateRepo.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            List<DrawingTemplate> found = new ArrayList<>();
            ids.forEach(id -> Optional.ofNullable(templates.get(id)).ifPresent(found::add));
            return found;
        });
        lenient().when(templateRepo.lockSharedById(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(templates.get(invocation.<Long>getArgument(0))));
        lenient().when(templateRepo.findById(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(templates.get(invocation.<Long>getArgument(0))));
        lenient().when(templateImageRepo.findInfoById(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(images.get(invocation.<Long>getArgument(0))));
        lenient().when(templateImageRepo.findInfoByIdIn(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return ids.stream().map(images::get).filter(image -> image != null).toList();
        });
    }

    @AfterEach
    void tearDown() {
        session.close();
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void loginAs(long userId, boolean master) {
        session.when(SessionUtils::getUserIdFromSession).thenReturn(userId);
        session.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(master);
    }

    private static List<User> users() {
        Employee anna = Employee.builder().id(1L).name("Anna").lastName("Nowak").drawingColor("#2563eb").build();
        Employee ola = Employee.builder().id(2L).name("Ola").lastName("Lis").drawingColor("#dc2626")
                .isDeleted(true).build();
        return List.of(
                User.builder().id(ANNA).username("anna.nowak").employee(anna).build(),
                User.builder().id(RECEPTION).username("recepcja").build(),
                User.builder().id(ADMIN).username("admin").employee(ola).build());
    }

    private DrawingTemplate addTemplate(long id, String name, int displayOrder, boolean archived, Long imageId) {
        DrawingTemplate template = DrawingTemplate.builder()
                .id(id).name(name).displayOrder(displayOrder).archived(false).currentImageId(imageId)
                .createdAt(CREATED_AT).updatedAt(CREATED_AT)
                .build();
        if (archived) {
            template.archive(CREATED_AT.plusDays(1));
        }
        templates.put(id, template);
        return template;
    }

    private static DrawingTemplateImageInfoProjection imageInfo(long id, long templateId, int width, int height) {
        return new DrawingTemplateImageInfoProjection() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Long getTemplateId() {
                return templateId;
            }

            @Override
            public Integer getWidth() {
                return width;
            }

            @Override
            public Integer getHeight() {
                return height;
            }

            @Override
            public Integer getSizeBytes() {
                return 1000;
            }
        };
    }

    private static Client client(boolean deleted) {
        return Client.builder().id(CLIENT_ID).firstName("Jan").lastName("Kowalski").isDeleted(deleted).build();
    }

    private static ClientCard card() {
        return ClientCard.builder().id(CARD_ID).client(client(false)).version(0L).build();
    }

    private DrawingSheet sheet(long id, int number, DrawingSheetStatus status) {
        return sheet(id, FEET, number, status);
    }

    private DrawingSheet sheet(long id, long templateId, int number, DrawingSheetStatus status) {
        DrawingSheet sheet = DrawingSheet.builder()
                .id(id).card(card()).templateId(templateId).templateImageId(templates.get(templateId).getCurrentImageId())
                .sheetNumber(number).width(100).height(200)
                .status(status).createdAt(CREATED_AT)
                .closedAt(status == DrawingSheetStatus.CLOSED ? CREATED_AT.plusDays(1) : null)
                .build();
        sheets.add(sheet);
        return sheet;
    }

    private DrawingLayer layer(long id, DrawingSheet sheet, long author, long version, String fileKey) {
        DrawingLayer layer = DrawingLayer.builder()
                .id(id).sheet(sheet).authorUserId(author).fileKey(fileKey).sizeBytes(10)
                .contentSha256("old-sha").version(version)
                .createdAt(CREATED_AT.plusMinutes(id)).updatedAt(CREATED_AT.plusHours(1)).updatedByUserId(author)
                .build();
        layers.add(layer);
        return layer;
    }

    private static List<DrawItem> items(int seed) {
        return List.of(new DrawStrokeItem(4.0, true, new double[][]{{10, 20, 0.5}, {10 + seed, 30, 1}}));
    }

    private static List<DrawItem> mixedItems() {
        return List.of(
                new DrawStrokeItem(4.0, false, new double[][]{{10, 20, 0.5}, {60, 120, 1}}),
                new DrawTextItem("Modzel", 30.0, 40.0, 24.0, true),
                new DrawMarkerItem(3L, DrawingMarkerShape.CIRCLE, "#FF0000", true, " Modzel ", 50.0, 60.0, 20.0));
    }

    private static byte[] normalized(List<DrawItem> items) {
        return DrawingItems.normalize(items, 100, 200);
    }

    private static DrawingGroupDTO group(ClientDrawingDTO drawing, long templateId) {
        return drawing.getGroups().stream()
                .filter(group -> group.getTemplate().getId() == templateId)
                .findFirst()
                .orElseThrow();
    }

    private void stubClient(boolean deleted) {
        when(clientRepo.findOneById(CLIENT_ID)).thenReturn(Optional.of(client(deleted)));
    }

    private void stubCreateContext() {
        stubClient(false);
        lenient().when(clientCardService.getOrCreateCard(CLIENT_ID)).thenReturn(card());
    }

    private void stubUpdateContext(DrawingSheet sheet) {
        stubClient(false);
        when(clientCardRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.of(card()));
        when(sheetRepo.lockByIdAndCardId(sheet.getId(), CARD_ID)).thenReturn(Optional.of(sheet));
        lenient().when(layerRepo.findBySheetIdAndAuthorUserId(anyLong(), anyLong())).thenAnswer(invocation -> {
            Long sheetId = invocation.getArgument(0);
            Long author = invocation.getArgument(1);
            return layers.stream()
                    .filter(layer -> layer.getSheet().getId().equals(sheetId) && layer.getAuthorUserId().equals(author))
                    .findFirst();
        });
    }

    private static void completeTransaction(int status) {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        if (status == TransactionSynchronization.STATUS_COMMITTED) {
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        }
        synchronizations.forEach(synchronization -> synchronization.afterCompletion(status));
    }

    private DrawingLayer savedLayer() {
        ArgumentCaptor<DrawingLayer> captor = ArgumentCaptor.forClass(DrawingLayer.class);
        verify(layerRepo).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void getDrawing_withoutCard_returnsActiveTemplateGroupsWithoutSheets_andCurrentUser() {
        stubClient(false);
        when(clientCardRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.empty());

        ClientDrawingDTO result = service.getDrawing(CLIENT_ID);

        assertThat(result.getGroups()).extracting(group -> group.getTemplate().getId()).containsExactly(FEET, HANDS);
        DrawingGroupDTO feet = result.getGroups().get(0);
        assertThat(feet.getTemplate().getName()).isEqualTo("Stopy");
        assertThat(feet.getTemplate().getArchived()).isFalse();
        assertThat(feet.getTemplate().getImage().getId()).isEqualTo(FEET_IMAGE);
        assertThat(feet.getTemplate().getImage().getWidth()).isEqualTo(100);
        assertThat(feet.getTemplate().getImage().getHeight()).isEqualTo(200);
        assertThat(feet.getCurrentSheet()).isNull();
        assertThat(feet.getClosedSheets()).isEmpty();
        assertThat(result.getGroups().get(1).getTemplate().getImage()).isNull();
        assertThat(result.getCurrentUser().getUserId()).isEqualTo(ANNA);
        assertThat(result.getCurrentUser().getName()).isEqualTo("Anna");
        assertThat(result.getCurrentUser().getColor()).isEqualTo("#2563eb");
        verifyNoInteractions(clientCardService, sheetRepo, layerRepo, fileStorage);
        verify(templateRepo, never()).findAllById(any());
        verify(clientCardRepo, never()).insertIfAbsent(anyLong(), any(), any());
        verify(cardAccessLogService).log(CardAccessAction.VIEW_DRAWING, CLIENT_ID, "DRAWING", null);
    }

    @Test
    void getDrawing_mapsSheetsAndLayers_perTemplate_withAuthorsAndColors() {
        stubClient(false);
        when(clientCardRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.of(card()));
        DrawingSheet current = sheet(3L, 3, DrawingSheetStatus.OPEN);
        DrawingSheet closed = sheet(2L, 2, DrawingSheetStatus.CLOSED);
        closed.setCreatedAt(CREATED_AT.minusDays(30));
        DrawingSheet hands = sheet(4L, HANDS, 1, DrawingSheetStatus.CLOSED);
        layer(31L, current, ANNA, 2L, "drawings/a.bin");
        DrawingLayer reception = layer(32L, current, RECEPTION, 1L, "drawings/b.bin");
        reception.setUpdatedByUserId(ADMIN);
        layer(21L, closed, ANNA, 4L, "drawings/c.bin");
        layer(41L, hands, ANNA, 1L, "drawings/d.bin");

        ClientDrawingDTO result = service.getDrawing(CLIENT_ID);

        DrawingGroupDTO feet = group(result, FEET);
        DrawingSheetDTO currentSheet = feet.getCurrentSheet();
        assertThat(currentSheet.getId()).isEqualTo(3L);
        assertThat(currentSheet.getSheetNumber()).isEqualTo(3);
        assertThat(currentSheet.getTemplateId()).isEqualTo(FEET);
        assertThat(currentSheet.getTemplateImageId()).isEqualTo(FEET_IMAGE);
        assertThat(currentSheet.getWidth()).isEqualTo(100);
        assertThat(currentSheet.getHeight()).isEqualTo(200);
        assertThat(currentSheet.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(currentSheet.getClosedAt()).isNull();
        List<DrawingLayerDTO> currentLayers = currentSheet.getLayers();
        assertThat(currentLayers).extracting(DrawingLayerDTO::getId).containsExactly(31L, 32L);
        DrawingLayerDTO annaLayer = currentLayers.get(0);
        assertThat(annaLayer.getAuthorUserId()).isEqualTo(ANNA);
        assertThat(annaLayer.getAuthorName()).isEqualTo("Anna");
        assertThat(annaLayer.getColor()).isEqualTo("#2563eb");
        assertThat(annaLayer.getVersion()).isEqualTo(2L);
        assertThat(annaLayer.getUpdatedAt()).isEqualTo(CREATED_AT.plusHours(1));
        assertThat(annaLayer.getUpdatedByName()).isEqualTo("Anna");
        DrawingLayerDTO receptionLayer = currentLayers.get(1);
        assertThat(receptionLayer.getAuthorName()).isEqualTo("recepcja");
        assertThat(receptionLayer.getColor()).isEqualTo("#6b7280");
        assertThat(receptionLayer.getUpdatedByUserId()).isEqualTo(ADMIN);
        assertThat(receptionLayer.getUpdatedByName()).isEqualTo("Ola");
        assertThat(feet.getClosedSheets()).extracting(DrawingSheetDTO::getId).containsExactly(2L);
        assertThat(feet.getClosedSheets().get(0).getCreatedAt()).isEqualTo(CREATED_AT.minusDays(30));
        assertThat(feet.getClosedSheets().get(0).getClosedAt()).isEqualTo(CREATED_AT.plusDays(1));
        assertThat(feet.getClosedSheets().get(0).getLayers()).extracting(DrawingLayerDTO::getId).containsExactly(21L);

        DrawingGroupDTO handsGroup = group(result, HANDS);
        assertThat(handsGroup.getCurrentSheet()).isNull();
        assertThat(handsGroup.getClosedSheets()).extracting(DrawingSheetDTO::getId).containsExactly(4L);
        assertThat(handsGroup.getClosedSheets().get(0).getTemplateImageId()).isNull();
        assertThat(handsGroup.getClosedSheets().get(0).getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(handsGroup.getClosedSheets().get(0).getClosedAt()).isEqualTo(CREATED_AT.plusDays(1));
        assertThat(handsGroup.getClosedSheets().get(0).getLayers()).extracting(DrawingLayerDTO::getId).containsExactly(41L);

        verify(cardAccessLogService).log(CardAccessAction.VIEW_DRAWING, CLIENT_ID, "DRAWING", CARD_ID);
        verify(userRepo).findAllWithEmployeeByIdIn(anyCollection());
        verify(layerRepo, times(1)).findBySheetIdInOrderByCreatedAtAscIdAsc(anyCollection());
    }

    @Test
    void getDrawing_groups_activeInOrder_thenArchivedWithSheetsByName_withoutArchivedUnused() {
        stubClient(false);
        when(clientCardRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.of(card()));
        templates.get(FEET).setDisplayOrder(2);
        templates.get(HANDS).setDisplayOrder(1);
        addTemplate(HEELS, "Pięty", 3, true, null);
        addTemplate(NAILS, "Paznokcie", 4, true, null);
        addTemplate(ANKLES, "Kostki", 5, true, null);
        sheet(60L, HEELS, 1, DrawingSheetStatus.OPEN);
        sheet(61L, ANKLES, 1, DrawingSheetStatus.CLOSED);

        ClientDrawingDTO result = service.getDrawing(CLIENT_ID);

        assertThat(result.getGroups()).extracting(group -> group.getTemplate().getId())
                .containsExactly(HANDS, FEET, ANKLES, HEELS);
        assertThat(group(result, HANDS).getCurrentSheet()).isNull();
        assertThat(group(result, HANDS).getClosedSheets()).isEmpty();
        assertThat(group(result, FEET).getCurrentSheet()).isNull();
        assertThat(group(result, FEET).getClosedSheets()).isEmpty();
        DrawingGroupDTO heels = group(result, HEELS);
        assertThat(heels.getTemplate().getArchived()).isTrue();
        assertThat(heels.getTemplate().getName()).isEqualTo("Pięty");
        assertThat(heels.getCurrentSheet().getId()).isEqualTo(60L);
        assertThat(group(result, ANKLES).getClosedSheets()).extracting(DrawingSheetDTO::getId).containsExactly(61L);
        verify(templateRepo, times(1)).findByArchivedFalseOrderByDisplayOrderAscIdAsc();
        verify(templateRepo, times(1)).findAllById(any());
        verify(templateImageRepo, times(1)).findInfoByIdIn(anyCollection());
    }

    @Test
    void getDrawing_deletedEmployee_stillShowsNameAndColor() {
        loginAs(ADMIN, true);
        stubClient(false);
        when(clientCardRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.empty());

        ClientDrawingDTO result = service.getDrawing(CLIENT_ID);

        assertThat(result.getCurrentUser().getName()).isEqualTo("Ola");
        assertThat(result.getCurrentUser().getColor()).isEqualTo("#dc2626");
    }

    @Test
    void getDrawing_userWithoutEmployee_usesUsernameAndGray() {
        loginAs(RECEPTION, false);
        stubClient(false);
        when(clientCardRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.empty());

        ClientDrawingDTO result = service.getDrawing(CLIENT_ID);

        assertThat(result.getCurrentUser().getName()).isEqualTo("recepcja");
        assertThat(result.getCurrentUser().getColor()).isEqualTo("#6b7280");
    }

    @Test
    void getDrawing_deletedClient_404ForUser() {
        stubClient(true);

        assertThatThrownBy(() -> service.getDrawing(CLIENT_ID)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(cardAccessLogService);
    }

    @Test
    void getDrawing_deletedClient_visibleForAdmin() {
        loginAs(ADMIN, true);
        stubClient(true);
        when(clientCardRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.empty());

        assertThat(service.getDrawing(CLIENT_ID).getGroups()).hasSize(2);
    }

    @Test
    void getLayerItems_decryptsStoredFile_withoutAccessLog() {
        stubClient(false);
        byte[] json = normalized(items(1));
        byte[] encrypted = new CardEncryption(CARD_KEY).encryptBytes(json);
        DrawingLayer layer = layer(31L, sheet(3L, 1, DrawingSheetStatus.OPEN), ANNA, 1L, "drawings/a.bin");
        when(layerRepo.findByIdAndSheetIdAndSheetCardClientId(31L, 3L, CLIENT_ID)).thenReturn(Optional.of(layer));
        when(fileStorage.get("drawings/a.bin")).thenReturn(encrypted);

        byte[] result = service.getLayerItems(CLIENT_ID, 3L, 31L);

        assertThat(result).isEqualTo(json);
        verify(cardAccessLogService, never()).log(any(), any(), any(), any());
    }

    @Test
    void getLayerItems_layerOfOtherSheetOrClient_404() {
        stubClient(false);
        when(layerRepo.findByIdAndSheetIdAndSheetCardClientId(31L, 4L, CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLayerItems(CLIENT_ID, 4L, 31L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(fileStorage, cardAccessLogService);
    }

    @Test
    void getLayerItems_deletedClient_404ForUser() {
        stubClient(true);

        assertThatThrownBy(() -> service.getLayerItems(CLIENT_ID, 3L, 31L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(layerRepo, fileStorage);
    }

    @Test
    void logSheetView_openSheetOfClient_logsViewWithSheetId() {
        stubClient(false);
        DrawingSheet sheet = sheet(3L, 1, DrawingSheetStatus.OPEN);
        when(sheetRepo.findByIdAndCardClientId(3L, CLIENT_ID)).thenReturn(Optional.of(sheet));

        service.logSheetView(CLIENT_ID, 3L);

        verify(cardAccessLogService).log(CardAccessAction.VIEW_DRAWING_SHEET, CLIENT_ID, "DRAWING_SHEET", 3L);
        verifyNoMoreInteractions(cardAccessLogService);
        verifyNoInteractions(layerRepo, fileStorage);
    }

    @Test
    void logSheetView_closedSheetOfArchivedTemplate_logsViewWithSheetId() {
        addTemplate(HEELS, "Pięty", 3, true, null);
        stubClient(false);
        DrawingSheet sheet = sheet(4L, HEELS, 1, DrawingSheetStatus.CLOSED);
        when(sheetRepo.findByIdAndCardClientId(4L, CLIENT_ID)).thenReturn(Optional.of(sheet));

        service.logSheetView(CLIENT_ID, 4L);

        verify(cardAccessLogService).log(CardAccessAction.VIEW_DRAWING_SHEET, CLIENT_ID, "DRAWING_SHEET", 4L);
        verifyNoMoreInteractions(cardAccessLogService);
    }

    @Test
    void logSheetView_sheetOfOtherCardOrMissing_404_withoutLog() {
        stubClient(false);
        when(sheetRepo.findByIdAndCardClientId(99L, CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.logSheetView(CLIENT_ID, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Drawing sheet not found with given id: 99");
        verify(cardAccessLogService, never()).log(any(), any(), any(), any());
    }

    @Test
    void logSheetView_deletedClient_404ForUser_withoutLog() {
        stubClient(true);

        assertThatThrownBy(() -> service.logSheetView(CLIENT_ID, 3L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Client not found with given id: " + CLIENT_ID);
        verifyNoInteractions(sheetRepo, cardAccessLogService);
    }

    @Test
    void logSheetView_deletedClient_visibleForMaster_logsView() {
        loginAs(ADMIN, true);
        stubClient(true);
        DrawingSheet sheet = sheet(3L, 1, DrawingSheetStatus.CLOSED);
        when(sheetRepo.findByIdAndCardClientId(3L, CLIENT_ID)).thenReturn(Optional.of(sheet));

        service.logSheetView(CLIENT_ID, 3L);

        verify(cardAccessLogService).log(CardAccessAction.VIEW_DRAWING_SHEET, CLIENT_ID, "DRAWING_SHEET", 3L);
    }

    @Test
    void createSheet_first_createsSheetAndAuthorLayer_withTemplateImageDimensions() {
        stubCreateContext();
        byte[] expected = normalized(items(1));

        ClientDrawingDTO result = service.createSheet(CLIENT_ID, FEET, items(1), null);

        DrawingSheet sheet = sheets.get(0);
        assertThat(sheet.getSheetNumber()).isEqualTo(1);
        assertThat(sheet.getTemplateId()).isEqualTo(FEET);
        assertThat(sheet.getTemplateImageId()).isEqualTo(FEET_IMAGE);
        assertThat(sheet.getWidth()).isEqualTo(100);
        assertThat(sheet.getHeight()).isEqualTo(200);
        assertThat(sheet.getStatus()).isEqualTo(DrawingSheetStatus.OPEN);
        assertThat(sheet.getCreatedByUserId()).isEqualTo(ANNA);

        DrawingLayer layer = savedLayer();
        assertThat(layer.getSheet()).isSameAs(sheet);
        assertThat(layer.getAuthorUserId()).isEqualTo(ANNA);
        assertThat(layer.getUpdatedByUserId()).isEqualTo(ANNA);
        assertThat(layer.getVersion()).isEqualTo(1L);
        assertThat(layer.getFileKey()).matches("drawings/[0-9a-f-]{36}\\.bin");
        assertThat(layer.getSizeBytes()).isEqualTo(expected.length);
        assertThat(layer.getContentSha256()).isEqualTo(ImageUtils.sha256Hex(expected));
        assertThat(layer.getCreatedAt()).isNotNull();
        byte[] stored = files.get(layer.getFileKey());
        assertThat(new String(stored, 0, 4)).isEqualTo("PCv1");
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("stroke");
        assertThat(cardEncryption.decryptBytes(stored)).isEqualTo(expected);

        DrawingSheetDTO current = group(result, FEET).getCurrentSheet();
        assertThat(current.getId()).isEqualTo(50L);
        assertThat(current.getTemplateImageId()).isEqualTo(FEET_IMAGE);
        assertThat(current.getLayers()).extracting(DrawingLayerDTO::getAuthorName).containsExactly("Anna");
        assertThat(group(result, HANDS).getCurrentSheet()).isNull();
        verify(templateRepo).lockSharedById(FEET);
        verify(clientCardRepo).lockCard(CARD_ID);
        verify(cardAccessLogService).log(CardAccessAction.CREATE_DRAWING_SHEET, CLIENT_ID, "DRAWING_SHEET", 50L);
    }

    @Test
    void createSheet_templateWithoutImage_usesDefault1536x2048_andNoTemplateImage() {
        stubCreateContext();

        ClientDrawingDTO result = service.createSheet(CLIENT_ID, HANDS, items(1), null);

        DrawingSheet sheet = sheets.get(0);
        assertThat(sheet.getTemplateId()).isEqualTo(HANDS);
        assertThat(sheet.getTemplateImageId()).isNull();
        assertThat(sheet.getWidth()).isEqualTo(1536);
        assertThat(sheet.getHeight()).isEqualTo(2048);
        assertThat(group(result, HANDS).getCurrentSheet().getTemplateImageId()).isNull();
        assertThat(group(result, HANDS).getTemplate().getImage()).isNull();
    }

    @Test
    void createSheet_afterImageRemovedFromTemplate_newSheetIsBlank1536x2048() {
        stubCreateContext();
        DrawingSheet old = sheet(5L, 1, DrawingSheetStatus.CLOSED);
        templates.get(FEET).setCurrentImageId(null);

        service.createSheet(CLIENT_ID, FEET, items(1), null);

        DrawingSheet created = sheets.get(sheets.size() - 1);
        assertThat(created.getSheetNumber()).isEqualTo(2);
        assertThat(created.getTemplateImageId()).isNull();
        assertThat(created.getWidth()).isEqualTo(1536);
        assertThat(created.getHeight()).isEqualTo(2048);
        assertThat(old.getTemplateImageId()).isEqualTo(FEET_IMAGE);
    }

    @Test
    void createSheet_forTwoTemplates_keepsTwoOpenSheets_eachNumberOne() {
        stubCreateContext();

        service.createSheet(CLIENT_ID, FEET, items(1), null);
        ClientDrawingDTO result = service.createSheet(CLIENT_ID, HANDS, items(2), null);

        assertThat(sheets).hasSize(2);
        assertThat(sheets).allMatch(DrawingSheet::isOpen);
        assertThat(sheets).extracting(DrawingSheet::getSheetNumber).containsExactly(1, 1);
        assertThat(sheets).extracting(DrawingSheet::getTemplateId).containsExactly(FEET, HANDS);
        assertThat(group(result, FEET).getCurrentSheet().getSheetNumber()).isEqualTo(1);
        assertThat(group(result, HANDS).getCurrentSheet().getSheetNumber()).isEqualTo(1);
        assertThat(group(result, FEET).getCurrentSheet().getId()).isNotEqualTo(group(result, HANDS).getCurrentSheet().getId());
    }

    @Test
    void createSheet_addBlank_closesCurrentOfSameTemplate_andLeavesOtherTemplateUntouched() {
        DrawingSheet current = sheet(5L, 1, DrawingSheetStatus.OPEN);
        layer(51L, current, ANNA, 1L, "drawings/a.bin");
        DrawingSheet hands = sheet(6L, HANDS, 1, DrawingSheetStatus.OPEN);
        stubCreateContext();

        ClientDrawingDTO result = service.createSheet(CLIENT_ID, FEET, null, 5L);

        assertThat(current.getStatus()).isEqualTo(DrawingSheetStatus.CLOSED);
        assertThat(current.getClosedAt()).isNotNull();
        assertThat(current.getClosedByUserId()).isEqualTo(ANNA);
        DrawingSheet created = sheets.get(sheets.size() - 1);
        assertThat(created.getId()).isEqualTo(50L);
        assertThat(created.getTemplateId()).isEqualTo(FEET);
        assertThat(created.getSheetNumber()).isEqualTo(2);
        assertThat(created.isOpen()).isTrue();
        assertThat(hands.isOpen()).isTrue();
        assertThat(hands.getClosedAt()).isNull();

        InOrder inOrder = inOrder(templateRepo, clientCardRepo, sheetRepo);
        inOrder.verify(templateRepo).lockSharedById(FEET);
        inOrder.verify(clientCardRepo).lockCard(CARD_ID);
        inOrder.verify(sheetRepo).lockByCardIdAndTemplateIdAndStatus(CARD_ID, FEET, DrawingSheetStatus.OPEN);
        inOrder.verify(sheetRepo).saveAndFlush(current);
        inOrder.verify(sheetRepo).saveAndFlush(created);
        verify(sheetRepo, never()).saveAndFlush(hands);
        verify(layerRepo, never()).saveAndFlush(any());
        verifyNoInteractions(fileStorage);

        DrawingGroupDTO feet = group(result, FEET);
        assertThat(feet.getCurrentSheet().getId()).isEqualTo(50L);
        assertThat(feet.getCurrentSheet().getLayers()).isEmpty();
        assertThat(feet.getClosedSheets()).extracting(DrawingSheetDTO::getId).containsExactly(5L);
        assertThat(feet.getClosedSheets().get(0).getLayers()).hasSize(1);
        assertThat(group(result, HANDS).getCurrentSheet().getId()).isEqualTo(6L);
    }

    @Test
    void createSheet_addBlank_withFile_createsLayerOnNewSheet() {
        DrawingSheet current = sheet(5L, 1, DrawingSheetStatus.OPEN);
        layer(51L, current, ANNA, 1L, "drawings/a.bin");
        stubCreateContext();

        service.createSheet(CLIENT_ID, FEET, items(1), 5L);

        assertThat(savedLayer().getSheet().getId()).isEqualTo(50L);
    }

    @Test
    void createSheet_closeSheetIdOfOtherTemplate_409() {
        DrawingSheet feet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        layer(51L, feet, ANNA, 1L, "drawings/a.bin");
        DrawingSheet hands = sheet(6L, HANDS, 1, DrawingSheetStatus.OPEN);
        layer(61L, hands, ANNA, 1L, "drawings/b.bin");
        stubCreateContext();

        assertThatThrownBy(() -> service.createSheet(CLIENT_ID, FEET, null, 6L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Bieżąca kartka została zmieniona na innym urządzeniu. Odśwież rysunek.");
        assertThat(feet.isOpen()).isTrue();
        assertThat(hands.isOpen()).isTrue();
        verify(sheetRepo, never()).saveAndFlush(any());
    }

    @Test
    void createSheet_addBlank_withWrongOrMissingCloseSheetId_409() {
        DrawingSheet current = sheet(5L, 3, DrawingSheetStatus.OPEN);
        stubCreateContext();

        assertThatThrownBy(() -> service.createSheet(CLIENT_ID, FEET, null, 4L)).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.createSheet(CLIENT_ID, FEET, null, null)).isInstanceOf(ConflictException.class);
        assertThat(current.getStatus()).isEqualTo(DrawingSheetStatus.OPEN);
        verify(sheetRepo, never()).saveAndFlush(any());
    }

    @Test
    void createSheet_first_withCloseSheetId_409() {
        stubCreateContext();

        assertThatThrownBy(() -> service.createSheet(CLIENT_ID, FEET, items(1), 5L))
                .isInstanceOf(ConflictException.class);
        verify(sheetRepo, never()).saveAndFlush(any());
        verifyNoInteractions(fileStorage);
    }

    @Test
    void createSheet_first_withNullOrEmptyItems_400() {
        stubCreateContext();

        assertThatThrownBy(() -> service.createSheet(CLIENT_ID, FEET, null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Rysunek jest pusty.");
        assertThatThrownBy(() -> service.createSheet(CLIENT_ID, FEET, List.of(), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Rysunek jest pusty.");
        assertThat(sheets).isEmpty();
        verify(sheetRepo, never()).saveAndFlush(any());
        verify(layerRepo, never()).saveAndFlush(any());
        verifyNoInteractions(fileStorage, cardAccessLogService);
    }

    @Test
    void createSheet_first_withStroke_createsSheetOneWithLayerHoldingItems() throws Exception {
        stubCreateContext();

        ClientDrawingDTO result = service.createSheet(CLIENT_ID, FEET, items(5), null);

        assertThat(sheets).hasSize(1);
        assertThat(sheets.get(0).getSheetNumber()).isEqualTo(1);
        DrawingLayer layer = savedLayer();
        assertThat(layer.getSheet().getId()).isEqualTo(50L);
        assertThat(layer.getVersion()).isEqualTo(1L);
        JsonNode stored = new ObjectMapper().readTree(cardEncryption.decryptBytes(files.get(layer.getFileKey())));
        assertThat(stored.size()).isEqualTo(1);
        assertThat(stored.get(0).get("kind").asText()).isEqualTo("stroke");
        assertThat(stored.get(0).get("points").get(1).get(0).asDouble()).isEqualTo(15.0);
        assertThat(group(result, FEET).getCurrentSheet().getLayers()).hasSize(1);
    }

    @Test
    void createSheet_addBlank_whenCurrentHasNoLayers_400() {
        DrawingSheet current = sheet(5L, 1, DrawingSheetStatus.OPEN);
        stubCreateContext();

        assertThatThrownBy(() -> service.createSheet(CLIENT_ID, FEET, null, 5L))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Bieżąca kartka jest pusta.");
        assertThat(current.getStatus()).isEqualTo(DrawingSheetStatus.OPEN);
        verify(sheetRepo, never()).saveAndFlush(any());
    }

    @Test
    void createSheet_itemOutsideSheet_400_beforeTouchingCard() {
        stubClient(false);
        List<DrawItem> outside = List.of(new DrawTextItem("A", 151.0, 10.0, 12.0, false));

        assertThatThrownBy(() -> service.createSheet(CLIENT_ID, FEET, outside, null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Element rysunku wychodzi poza kartkę.");
        verifyNoInteractions(clientCardService, sheetRepo, layerRepo, fileStorage);
    }

    @Test
    void createSheet_archivedTemplate_409() {
        addTemplate(HEELS, "Pięty", 3, true, null);
        stubClient(false);

        assertThatThrownBy(() -> service.createSheet(CLIENT_ID, HEELS, items(1), null))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ARCHIVED_MESSAGE);
        verifyNoInteractions(clientCardService, sheetRepo, layerRepo, fileStorage, cardAccessLogService);
    }

    @Test
    void createSheet_withoutTemplateId_400() {
        stubClient(false);

        assertThatThrownBy(() -> service.createSheet(CLIENT_ID, null, items(1), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nie wybrano szablonu rysunku.");
        verifyNoInteractions(clientCardService, sheetRepo, fileStorage);
    }

    @Test
    void createSheet_unknownTemplate_404() {
        stubClient(false);

        assertThatThrownBy(() -> service.createSheet(CLIENT_ID, 99L, items(1), null))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(clientCardService, sheetRepo, fileStorage);
    }

    @Test
    void createSheet_concurrentInsert_409_andRollbackRemovesNewFile() {
        stubCreateContext();
        when(layerRepo.saveAndFlush(any(DrawingLayer.class)))
                .thenThrow(new DataIntegrityViolationException("uq_drawing_sheet_open_card_template"));

        assertThatThrownBy(() -> service.createSheet(CLIENT_ID, FEET, items(1), null))
                .isInstanceOf(ConflictException.class);

        String newKey = files.keySet().iterator().next();
        verify(fileStorage, never()).delete(anyString());
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(fileStorage).delete(newKey);
        verifyNoInteractions(cardAccessLogService);
    }

    @Test
    void createSheet_deletedClient_409() {
        stubClient(true);

        assertThatThrownBy(() -> service.createSheet(CLIENT_ID, FEET, null, null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Klient jest usunięty. Przywróć klienta przed edycją karty.");
        verifyNoInteractions(clientCardService, sheetRepo, fileStorage);
    }

    @Test
    void updateLayer_ownMissingLayer_withVersion0_createsLayerVersion1() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        stubUpdateContext(sheet);
        byte[] expected = normalized(items(2));

        ClientDrawingDTO result = service.updateLayerItems(CLIENT_ID, 5L, ANNA, items(2), 0L);

        DrawingLayer layer = savedLayer();
        assertThat(layer.getSheet()).isSameAs(sheet);
        assertThat(layer.getAuthorUserId()).isEqualTo(ANNA);
        assertThat(layer.getVersion()).isEqualTo(1L);
        assertThat(layer.getUpdatedByUserId()).isEqualTo(ANNA);
        assertThat(layer.getContentSha256()).isEqualTo(ImageUtils.sha256Hex(expected));
        assertThat(cardEncryption.decryptBytes(files.get(layer.getFileKey()))).isEqualTo(expected);
        assertThat(group(result, FEET).getCurrentSheet().getLayers())
                .extracting(DrawingLayerDTO::getVersion).containsExactly(1L);
        verify(cardAccessLogService).log(CardAccessAction.UPDATE_DRAWING, CLIENT_ID, "DRAWING_LAYER", 70L);
    }

    @Test
    void updateLayer_ownExistingLayer_bumpsVersion_andDeletesOldFileOnlyAfterCommit() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        DrawingLayer layer = layer(51L, sheet, ANNA, 1L, "drawings/old.bin");
        stubUpdateContext(sheet);
        byte[] expected = normalized(items(2));

        service.updateLayerItems(CLIENT_ID, 5L, ANNA, items(2), 1L);

        assertThat(layer.getVersion()).isEqualTo(2L);
        assertThat(layer.getFileKey()).isNotEqualTo("drawings/old.bin").startsWith("drawings/");
        assertThat(layer.getSizeBytes()).isEqualTo(expected.length);
        assertThat(layer.getUpdatedAt()).isAfter(CREATED_AT.plusHours(1));
        verify(layerRepo).saveAndFlush(layer);
        verify(cardAccessLogService).log(CardAccessAction.UPDATE_DRAWING, CLIENT_ID, "DRAWING_LAYER", 51L);

        verify(fileStorage, never()).delete(anyString());
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(fileStorage).delete("drawings/old.bin");
        verify(fileStorage, never()).delete(layer.getFileKey());
    }

    @Test
    void updateLayer_rollback_removesNewFile_andKeepsOld() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        DrawingLayer layer = layer(51L, sheet, ANNA, 1L, "drawings/old.bin");
        stubUpdateContext(sheet);

        service.updateLayerItems(CLIENT_ID, 5L, ANNA, items(2), 1L);
        String newKey = layer.getFileKey();
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(fileStorage).delete(newKey);
        verify(fileStorage, never()).delete("drawings/old.bin");
    }

    @Test
    void updateLayer_secondUser_createsOwnLayerOnSameSheet() {
        loginAs(RECEPTION, false);
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        DrawingLayer anna = layer(51L, sheet, ANNA, 3L, "drawings/anna.bin");
        stubUpdateContext(sheet);

        ClientDrawingDTO result = service.updateLayerItems(CLIENT_ID, 5L, RECEPTION, items(2), 0L);

        DrawingLayer created = savedLayer();
        assertThat(created).isNotSameAs(anna);
        assertThat(created.getAuthorUserId()).isEqualTo(RECEPTION);
        assertThat(anna.getVersion()).isEqualTo(3L);
        assertThat(anna.getFileKey()).isEqualTo("drawings/anna.bin");
        assertThat(group(result, FEET).getCurrentSheet().getLayers()).extracting(DrawingLayerDTO::getAuthorName)
                .containsExactly("Anna", "recepcja");
    }

    @Test
    void updateLayer_openSheetOfArchivedTemplate_409ReadOnly() {
        addTemplate(HEELS, "Pięty", 3, true, null);
        DrawingSheet sheet = sheet(5L, HEELS, 1, DrawingSheetStatus.OPEN);
        DrawingLayer layer = layer(51L, sheet, ANNA, 1L, "drawings/old.bin");
        stubUpdateContext(sheet);

        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, ANNA, items(2), 1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Szablon jest zarchiwizowany. Kartka jest tylko do podglądu.");
        assertThat(layer.getVersion()).isEqualTo(1L);
        assertThat(layer.getFileKey()).isEqualTo("drawings/old.bin");
        verify(layerRepo, never()).saveAndFlush(any());
        verifyNoInteractions(fileStorage, cardAccessLogService);
    }

    @Test
    void updateLayer_userOnOtherAuthorLayer_403() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        layer(51L, sheet, RECEPTION, 1L, "drawings/old.bin");
        stubClient(false);

        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, RECEPTION, items(2), 1L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Możesz zmieniać tylko własne zaznaczenia.");
        verifyNoInteractions(sheetRepo, layerRepo, fileStorage, cardAccessLogService);
    }

    @Test
    void updateLayer_adminOnOtherExistingLayer_403() {
        loginAs(ADMIN, true);
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        DrawingLayer layer = layer(51L, sheet, ANNA, 2L, "drawings/old.bin");
        stubClient(false);

        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, ANNA, items(2), 2L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Możesz zmieniać tylko własne zaznaczenia.");
        assertThat(layer.getVersion()).isEqualTo(2L);
        assertThat(layer.getFileKey()).isEqualTo("drawings/old.bin");
        verifyNoInteractions(sheetRepo, layerRepo, fileStorage, cardAccessLogService);
    }

    @Test
    void updateLayer_adminOnMissingOtherLayer_403() {
        loginAs(ADMIN, true);
        stubClient(false);

        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, ANNA, items(2), 0L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Możesz zmieniać tylko własne zaznaczenia.");
        verifyNoInteractions(sheetRepo, layerRepo, fileStorage, cardAccessLogService);
    }

    @Test
    void updateLayer_versionMismatch_409WithCurrentVersion() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        layer(51L, sheet, ANNA, 2L, "drawings/old.bin");
        stubUpdateContext(sheet);

        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, ANNA, items(2), 1L))
                .isInstanceOfSatisfying(DrawingConflictException.class,
                        e -> assertThat(e.getCurrentVersion()).isEqualTo(2L));
        verifyNoInteractions(fileStorage, cardAccessLogService);
        verify(layerRepo, never()).saveAndFlush(any());
    }

    @Test
    void updateLayer_ownMissingLayer_withNonZeroVersion_409WithVersion0() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        stubUpdateContext(sheet);

        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, ANNA, items(2), 1L))
                .isInstanceOfSatisfying(DrawingConflictException.class,
                        e -> assertThat(e.getCurrentVersion()).isZero());
    }

    @Test
    void updateLayer_concurrentLayerCreation_409DrawingConflict() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        stubUpdateContext(sheet);
        when(layerRepo.saveAndFlush(any(DrawingLayer.class)))
                .thenThrow(new DataIntegrityViolationException("uq_drawing_layer_sheet_author"));

        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, ANNA, items(2), 0L))
                .isInstanceOfSatisfying(DrawingConflictException.class,
                        e -> assertThat(e.getCurrentVersion()).isEqualTo(1L));
        String newKey = files.keySet().iterator().next();
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(fileStorage).delete(newKey);
        verifyNoInteractions(cardAccessLogService);
    }

    @Test
    void updateLayer_closedSheet_409() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.CLOSED);
        layer(51L, sheet, ANNA, 2L, "drawings/old.bin");
        stubUpdateContext(sheet);

        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, ANNA, items(2), 2L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Kartka jest zamknięta.");
        verifyNoInteractions(fileStorage);
    }

    @Test
    void updateLayer_sameContent_changesNothing() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        DrawingLayer layer = layer(51L, sheet, ANNA, 2L, "drawings/old.bin");
        layer.setContentSha256(ImageUtils.sha256Hex(normalized(items(2))));
        stubUpdateContext(sheet);

        ClientDrawingDTO result = service.updateLayerItems(CLIENT_ID, 5L, ANNA, items(2), 2L);

        assertThat(layer.getVersion()).isEqualTo(2L);
        assertThat(layer.getFileKey()).isEqualTo("drawings/old.bin");
        assertThat(group(result, FEET).getCurrentSheet().getLayers().get(0).getVersion()).isEqualTo(2L);
        verifyNoInteractions(fileStorage, cardAccessLogService);
        verify(layerRepo, never()).saveAndFlush(any());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void updateLayer_missingVersionOrFile_400() {
        stubClient(false);

        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, ANNA, items(2), null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Brak wersji rysunku.");

        stubUpdateContext(sheet(5L, 1, DrawingSheetStatus.OPEN));
        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, ANNA, null, 0L))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nieprawidłowe dane rysunku.");
        verifyNoInteractions(fileStorage);
    }

    @Test
    void updateLayer_strokeTextAndMarker_storesEncryptedNormalizedJson_andReadsItBack() throws Exception {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        stubUpdateContext(sheet);

        service.updateLayerItems(CLIENT_ID, 5L, ANNA, mixedItems(), 0L);

        DrawingLayer layer = savedLayer();
        assertThat(layer.getVersion()).isEqualTo(1L);
        byte[] stored = files.get(layer.getFileKey());
        assertThat(new String(stored, StandardCharsets.ISO_8859_1)).doesNotContain("stroke").doesNotContain("Modzel");
        when(layerRepo.findByIdAndSheetIdAndSheetCardClientId(70L, 5L, CLIENT_ID)).thenReturn(Optional.of(layer));
        when(fileStorage.get(layer.getFileKey())).thenReturn(stored);

        JsonNode read = new ObjectMapper().readTree(service.getLayerItems(CLIENT_ID, 5L, 70L));

        assertThat(read.size()).isEqualTo(3);
        assertThat(read.get(0).get("kind").asText()).isEqualTo("stroke");
        assertThat(read.get(0).get("pressureSensitive").asBoolean()).isFalse();
        assertThat(read.get(0).get("points").size()).isEqualTo(2);
        assertThat(read.get(1).get("kind").asText()).isEqualTo("text");
        assertThat(read.get(1).get("text").asText()).isEqualTo("Modzel");
        assertThat(read.get(1).get("bold").asBoolean()).isTrue();
        JsonNode marker = read.get(2);
        assertThat(marker.get("kind").asText()).isEqualTo("marker");
        assertThat(marker.get("markerId").asLong()).isEqualTo(3L);
        assertThat(marker.get("shape").asText()).isEqualTo("CIRCLE");
        assertThat(marker.get("color").asText()).isEqualTo("#ff0000");
        assertThat(marker.get("name").asText()).isEqualTo("Modzel");
        assertThat(marker.get("filled").asBoolean()).isTrue();
        assertThat(marker.get("size").asDouble()).isEqualTo(20.0);
        verify(cardAccessLogService).log(CardAccessAction.UPDATE_DRAWING, CLIENT_ID, "DRAWING_LAYER", 70L);
        verify(cardAccessLogService, never()).log(eq(CardAccessAction.VIEW_DRAWING_SHEET), any(), any(), any());
    }

    @Test
    void updateLayer_sameItemsAgain_keepsVersionBecauseShaMatches() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        stubUpdateContext(sheet);
        service.updateLayerItems(CLIENT_ID, 5L, ANNA, mixedItems(), 0L);
        DrawingLayer layer = savedLayer();
        String key = layer.getFileKey();

        ClientDrawingDTO result = service.updateLayerItems(CLIENT_ID, 5L, ANNA, mixedItems(), 1L);

        assertThat(layer.getVersion()).isEqualTo(1L);
        assertThat(layer.getFileKey()).isEqualTo(key);
        assertThat(files).hasSize(1);
        assertThat(group(result, FEET).getCurrentSheet().getLayers().get(0).getVersion()).isEqualTo(1L);
        verify(cardAccessLogService, times(1)).log(eq(CardAccessAction.UPDATE_DRAWING), any(), any(), any());
    }

    @Test
    void updateLayer_emptyList_withExistingLayer_deletesLayer_andFileOnlyAfterCommit() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        DrawingLayer layer = layer(51L, sheet, ANNA, 2L, "drawings/old.bin");
        layer(52L, sheet, RECEPTION, 1L, "drawings/reception.bin");
        stubUpdateContext(sheet);

        ClientDrawingDTO result = service.updateLayerItems(CLIENT_ID, 5L, ANNA, List.of(), 2L);

        InOrder inOrder = inOrder(layerRepo);
        inOrder.verify(layerRepo).delete(layer);
        inOrder.verify(layerRepo).flush();
        verify(layerRepo, never()).saveAndFlush(any());
        verify(fileStorage, never()).put(anyString(), any());
        assertThat(files).isEmpty();
        assertThat(layers).extracting(DrawingLayer::getId).containsExactly(52L);
        assertThat(layer.getVersion()).isEqualTo(2L);
        assertThat(layer.getFileKey()).isEqualTo("drawings/old.bin");
        assertThat(group(result, FEET).getCurrentSheet().getLayers())
                .extracting(DrawingLayerDTO::getAuthorUserId).containsExactly(RECEPTION);
        verify(cardAccessLogService).log(CardAccessAction.UPDATE_DRAWING, CLIENT_ID, "DRAWING_LAYER", 51L);
        verifyNoMoreInteractions(cardAccessLogService);

        verify(fileStorage, never()).delete(anyString());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(fileStorage).delete("drawings/old.bin");
        verify(fileStorage, never()).delete("drawings/reception.bin");
    }

    @Test
    void updateLayer_emptyList_rollback_keepsFile() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        layer(51L, sheet, ANNA, 1L, "drawings/old.bin");
        stubUpdateContext(sheet);

        service.updateLayerItems(CLIENT_ID, 5L, ANNA, List.of(), 1L);
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verifyNoInteractions(fileStorage);
    }

    @Test
    void updateLayer_emptyList_withoutLayer_storesDeletesAndLogsNothing() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        layer(52L, sheet, RECEPTION, 1L, "drawings/reception.bin");
        stubUpdateContext(sheet);

        ClientDrawingDTO result = service.updateLayerItems(CLIENT_ID, 5L, ANNA, List.of(), 0L);

        verify(layerRepo, never()).delete(any(DrawingLayer.class));
        verify(layerRepo, never()).flush();
        verify(layerRepo, never()).saveAndFlush(any());
        verifyNoInteractions(fileStorage, cardAccessLogService);
        assertThat(files).isEmpty();
        assertThat(layers).extracting(DrawingLayer::getId).containsExactly(52L);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        assertThat(group(result, FEET).getCurrentSheet().getId()).isEqualTo(5L);
        assertThat(group(result, FEET).getCurrentSheet().getLayers())
                .extracting(DrawingLayerDTO::getAuthorUserId).containsExactly(RECEPTION);
    }

    @Test
    void updateLayer_emptyList_versionMismatch_409_andLayerKept() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        DrawingLayer layer = layer(51L, sheet, ANNA, 2L, "drawings/old.bin");
        stubUpdateContext(sheet);

        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, ANNA, List.of(), 1L))
                .isInstanceOfSatisfying(DrawingConflictException.class,
                        e -> assertThat(e.getCurrentVersion()).isEqualTo(2L));
        verify(layerRepo, never()).delete(any(DrawingLayer.class));
        verify(layerRepo, never()).saveAndFlush(any());
        verifyNoInteractions(fileStorage, cardAccessLogService);
        assertThat(layers).containsExactly(layer);
        assertThat(layer.getVersion()).isEqualTo(2L);
    }

    @Test
    void updateLayer_emptyList_closedSheet_409_andLayerKept() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.CLOSED);
        DrawingLayer layer = layer(51L, sheet, ANNA, 2L, "drawings/old.bin");
        stubUpdateContext(sheet);

        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, ANNA, List.of(), 2L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Kartka jest zamknięta.");
        verify(layerRepo, never()).delete(any(DrawingLayer.class));
        verifyNoInteractions(fileStorage, cardAccessLogService);
        assertThat(layers).containsExactly(layer);
    }

    @Test
    void updateLayer_emptyList_openSheetOfArchivedTemplate_409ReadOnly_andLayerKept() {
        addTemplate(HEELS, "Pięty", 3, true, null);
        DrawingSheet sheet = sheet(5L, HEELS, 1, DrawingSheetStatus.OPEN);
        DrawingLayer layer = layer(51L, sheet, ANNA, 2L, "drawings/old.bin");
        stubUpdateContext(sheet);

        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, ANNA, List.of(), 2L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Szablon jest zarchiwizowany. Kartka jest tylko do podglądu.");
        verify(layerRepo, never()).delete(any(DrawingLayer.class));
        verifyNoInteractions(fileStorage, cardAccessLogService);
        assertThat(layers).containsExactly(layer);
    }

    @Test
    void updateLayer_nullItems_withExistingLayer_400_andLayerKept() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        DrawingLayer layer = layer(51L, sheet, ANNA, 2L, "drawings/old.bin");
        stubUpdateContext(sheet);

        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, ANNA, null, 2L))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nieprawidłowe dane rysunku.");
        verify(layerRepo, never()).delete(any(DrawingLayer.class));
        verify(layerRepo, never()).saveAndFlush(any());
        verifyNoInteractions(fileStorage, cardAccessLogService);
        assertThat(layers).containsExactly(layer);
        assertThat(layer.getFileKey()).isEqualTo("drawings/old.bin");
    }

    @Test
    void updateLayer_afterEmptyListDelete_staleVersionConflicts_andVersion0CreatesNewLayer() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        layer(51L, sheet, ANNA, 3L, "drawings/old.bin");
        stubUpdateContext(sheet);
        service.updateLayerItems(CLIENT_ID, 5L, ANNA, List.of(), 3L);

        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, ANNA, items(2), 3L))
                .isInstanceOfSatisfying(DrawingConflictException.class,
                        e -> assertThat(e.getCurrentVersion()).isZero());

        ClientDrawingDTO result = service.updateLayerItems(CLIENT_ID, 5L, ANNA, items(2), 0L);

        DrawingLayer created = savedLayer();
        assertThat(created.getId()).isEqualTo(70L);
        assertThat(created.getVersion()).isEqualTo(1L);
        assertThat(created.getAuthorUserId()).isEqualTo(ANNA);
        assertThat(created.getFileKey()).isNotEqualTo("drawings/old.bin");
        assertThat(cardEncryption.decryptBytes(files.get(created.getFileKey()))).isEqualTo(normalized(items(2)));
        assertThat(group(result, FEET).getCurrentSheet().getLayers())
                .extracting(DrawingLayerDTO::getId, DrawingLayerDTO::getVersion)
                .containsExactly(tuple(70L, 1L));
        verify(cardAccessLogService).log(CardAccessAction.UPDATE_DRAWING, CLIENT_ID, "DRAWING_LAYER", 51L);
        verify(cardAccessLogService).log(CardAccessAction.UPDATE_DRAWING, CLIENT_ID, "DRAWING_LAYER", 70L);
    }

    @Test
    void updateLayer_invalidItems_400_andNothingStored() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        DrawingLayer layer = layer(51L, sheet, ANNA, 1L, "drawings/old.bin");
        stubUpdateContext(sheet);
        double[][] tooManyPoints = new double[DrawingItems.MAX_POINTS + 1][];
        for (int i = 0; i < tooManyPoints.length; i++) {
            tooManyPoints[i] = new double[]{10, 10, 0.5};
        }
        Map<List<DrawItem>, String> cases = new LinkedHashMap<>();
        cases.put(List.of(new DrawStrokeItem(2.0, true, tooManyPoints)),
                "Kreska ma za dużo punktów (maksymalnie 20000).");
        cases.put(List.of(new DrawTextItem("A", 100.0 + 100, 10.0, 12.0, false)),
                "Element rysunku wychodzi poza kartkę.");
        cases.put(List.of(new DrawStrokeItem(2.0, true, new double[][]{{10, 251, 0.5}})),
                "Element rysunku wychodzi poza kartkę.");
        cases.put(List.of(new DrawStrokeItem(2.0, true, new double[][]{{Double.NaN, 10, 0.5}})),
                "Nieprawidłowe dane rysunku.");
        cases.put(List.of(new DrawTextItem("x".repeat(501), 10.0, 10.0, 12.0, false)),
                "Napis na rysunku musi mieć od 1 do 500 znaków.");
        cases.put(List.of(new DrawMarkerItem(null, DrawingMarkerShape.CIRCLE, "abc", false, "Modzel", 10.0, 10.0, 20.0)),
                "Nieprawidłowy kolor oznaczenia na rysunku.");

        cases.forEach((items, message) -> assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, ANNA, items, 1L))
                .isInstanceOf(ValidationException.class)
                .hasMessage(message));
        assertThat(layer.getVersion()).isEqualTo(1L);
        assertThat(layer.getFileKey()).isEqualTo("drawings/old.bin");
        verify(layerRepo, never()).saveAndFlush(any());
        verifyNoInteractions(fileStorage, cardAccessLogService);
    }

    @Test
    void updateLayer_sheetOfOtherClient_404() {
        stubClient(false);
        when(clientCardRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.of(card()));
        when(sheetRepo.lockByIdAndCardId(99L, CARD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 99L, ANNA, items(2), 0L))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(fileStorage);
    }

    @Test
    void updateLayer_clientWithoutCard_404() {
        stubClient(false);
        when(clientCardRepo.findByClientId(CLIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, ANNA, items(2), 0L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(clientCardService, never()).getOrCreateCard(any());
    }

    @Test
    void updateLayer_deletedClient_409() {
        stubClient(true);

        assertThatThrownBy(() -> service.updateLayerItems(CLIENT_ID, 5L, ANNA, items(2), 0L))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(sheetRepo, layerRepo, fileStorage);
    }

    @Test
    void updateLayer_oldFileDeleteFailureAfterCommit_isNotThrown() {
        DrawingSheet sheet = sheet(5L, 1, DrawingSheetStatus.OPEN);
        layer(51L, sheet, ANNA, 1L, "drawings/old.bin");
        stubUpdateContext(sheet);
        doThrow(new RuntimeException("disk")).when(fileStorage).delete("drawings/old.bin");

        service.updateLayerItems(CLIENT_ID, 5L, ANNA, items(2), 1L);

        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(fileStorage).delete("drawings/old.bin");
    }
}

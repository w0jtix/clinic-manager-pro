package com.clinic.clinicmanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.DrawingTemplateSettingsDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.DrawingTemplate;
import com.clinic.clinicmanager.model.DrawingTemplateImage;
import com.clinic.clinicmanager.model.constants.DrawingTemplateDeleteResult;
import com.clinic.clinicmanager.repo.DrawingSheetRepo;
import com.clinic.clinicmanager.repo.DrawingTemplateImageRepo;
import com.clinic.clinicmanager.repo.DrawingTemplateRepo;
import com.clinic.clinicmanager.repo.projection.DrawingTemplateImageInfoProjection;
import com.clinic.clinicmanager.repo.projection.DrawingTemplateUsageProjection;
import com.clinic.clinicmanager.service.impl.DrawingTemplateServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DrawingTemplateServiceImplTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);
    private static final String NAME_MESSAGE = "Nazwa szablonu jest wymagana (maksymalnie 60 znaków).";
    private static final String DUPLICATE_MESSAGE = "Szablon o tej nazwie już istnieje.";
    private static final String DUPLICATE_ARCHIVED_MESSAGE =
            "Szablon o tej nazwie już istnieje.";
    private static final String LIMIT_MESSAGE = "Można mieć maksymalnie 10 aktywnych szablonów.";
    private static final String ARCHIVED_MESSAGE = "Szablon jest zarchiwizowany.";

    @Mock DrawingTemplateRepo templateRepo;
    @Mock DrawingTemplateImageRepo imageRepo;
    @Mock DrawingSheetRepo sheetRepo;
    @Mock AuditLogService auditLogService;

    @InjectMocks DrawingTemplateServiceImpl service;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static byte[] image(int width, int height, String format) throws IOException {
        int type = "png".equals(format) ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width / 2, height / 2);
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    private static MockMultipartFile file(String contentType, byte[] bytes) {
        return new MockMultipartFile("file", "template", contentType, bytes);
    }

    private static MockMultipartFile png(int width, int height) throws IOException {
        return file("image/png", image(width, height, "png"));
    }

    private static DrawingTemplate template(long id, String name, int displayOrder, Long imageId) {
        return DrawingTemplate.builder()
                .id(id).name(name).displayOrder(displayOrder).archived(false).currentImageId(imageId)
                .createdAt(CREATED_AT).updatedAt(CREATED_AT)
                .build();
    }

    private static DrawingTemplate archived(long id, String name, LocalDateTime archivedAt) {
        DrawingTemplate template = template(id, name, 4, null);
        template.archive(archivedAt);
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
                return 1234;
            }
        };
    }

    private static DrawingTemplateUsageProjection usage(long templateId, long clientCount) {
        return new DrawingTemplateUsageProjection() {
            @Override
            public Long getTemplateId() {
                return templateId;
            }

            @Override
            public Long getClientCount() {
                return clientCount;
            }
        };
    }

    private void stubTemplateSave() {
        when(templateRepo.saveAndFlush(any(DrawingTemplate.class))).thenAnswer(invocation -> {
            DrawingTemplate template = invocation.getArgument(0);
            if (template.getId() == null) {
                template.setId(9L);
            }
            return template;
        });
    }

    private void stubImageSave() {
        when(imageRepo.saveAndFlush(any(DrawingTemplateImage.class))).thenAnswer(invocation -> {
            DrawingTemplateImage image = invocation.getArgument(0);
            image.setId(90L);
            return image;
        });
    }

    private void stubCreateContext(long activeCount, int maxOrder) {
        when(templateRepo.countByArchivedFalse()).thenReturn(activeCount);
        when(templateRepo.existsByArchivedFalseAndNameIgnoreCase(any())).thenReturn(false);
        when(templateRepo.findMaxActiveDisplayOrder()).thenReturn(maxOrder);
        stubTemplateSave();
    }

    private void stubUpdateContext(DrawingTemplate template) {
        when(templateRepo.lockById(template.getId())).thenReturn(Optional.of(template));
        lenient().when(templateRepo.existsByArchivedFalseAndNameIgnoreCaseAndIdNot(any(), eq(template.getId())))
                .thenReturn(false);
        lenient().when(sheetRepo.countClientsByTemplateId(template.getId())).thenReturn(0L);
    }

    private DrawingTemplateImage savedImage() {
        ArgumentCaptor<DrawingTemplateImage> captor = ArgumentCaptor.forClass(DrawingTemplateImage.class);
        verify(imageRepo).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static BufferedImage read(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, Map.class);
    }

    @Test
    void create_withPng_firstTemplate_getsOrderOne_image_andAuditCreate() throws IOException {
        stubCreateContext(0, 0);
        stubImageSave();

        DrawingTemplateSettingsDTO result = service.createTemplate("Stopy", png(1200, 1600));

        assertThat(result.getId()).isEqualTo(9L);
        assertThat(result.getName()).isEqualTo("Stopy");
        assertThat(result.getDisplayOrder()).isEqualTo(1);
        assertThat(result.getArchived()).isFalse();
        assertThat(result.getArchivedAt()).isNull();
        assertThat(result.getClientCount()).isZero();
        assertThat(result.getImage()).isNotNull();
        assertThat(result.getImage().getId()).isEqualTo(90L);
        assertThat(result.getImage().getWidth()).isEqualTo(1200);
        assertThat(result.getImage().getHeight()).isEqualTo(1600);

        DrawingTemplateImage image = savedImage();
        assertThat(image.getTemplateId()).isEqualTo(9L);
        assertThat(image.getImage()).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(image.getSizeBytes()).isEqualTo(image.getImage().length);
        assertThat(result.getImage().getSizeBytes()).isEqualTo(image.getSizeBytes());

        ArgumentCaptor<DrawingTemplate> templates = ArgumentCaptor.forClass(DrawingTemplate.class);
        verify(templateRepo, times(2)).saveAndFlush(templates.capture());
        assertThat(templates.getValue().getCurrentImageId()).isEqualTo(90L);
        assertThat(templates.getValue().getCreatedAt()).isNotNull();
        assertThat(templates.getValue().getUpdatedAt()).isNotNull();

        ArgumentCaptor<Object> created = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).logCreate(eq("DrawingTemplate"), eq(9L), eq("Stopy"), created.capture());
        assertThat(toMap(created.getValue()))
                .containsEntry("name", "Stopy").containsEntry("displayOrder", 1)
                .containsEntry("archived", false).containsEntry("imageId", 90L)
                .containsEntry("imageWidth", 1200).containsEntry("imageHeight", 1600)
                .doesNotContainKey("image");
        verify(templateRepo).acquireTransactionLock(anyLong());
    }

    @Test
    void create_withoutFile_hasNoImage() {
        stubCreateContext(2, 2);

        DrawingTemplateSettingsDTO result = service.createTemplate("Dłonie", null);

        assertThat(result.getImage()).isNull();
        assertThat(result.getDisplayOrder()).isEqualTo(3);
        verify(imageRepo, never()).saveAndFlush(any());
        verify(templateRepo, times(1)).saveAndFlush(any());
        ArgumentCaptor<Object> created = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).logCreate(eq("DrawingTemplate"), eq(9L), eq("Dłonie"), created.capture());
        assertThat(toMap(created.getValue())).containsEntry("imageId", null);
    }

    @Test
    void create_withEmptyFile_isTreatedAsNoImage() {
        stubCreateContext(0, 0);

        assertThat(service.createTemplate("Stopy", file("image/png", new byte[0])).getImage()).isNull();
        verifyNoInteractions(imageRepo);
    }

    @Test
    void create_trimsName_andRejectsDuplicateIgnoringCase() {
        when(templateRepo.countByArchivedFalse()).thenReturn(1L);
        when(templateRepo.existsByArchivedFalseAndNameIgnoreCase("stopy")).thenReturn(true);

        assertThatThrownBy(() -> service.createTemplate(" stopy ", null))
                .isInstanceOf(ConflictException.class)
                .hasMessage(DUPLICATE_MESSAGE);
        verify(templateRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void create_nameOfArchivedIgnoringCaseAndTrim_409WithArchivedMessage() {
        when(templateRepo.countByArchivedFalse()).thenReturn(1L);
        when(templateRepo.existsByArchivedFalseAndNameIgnoreCase("stopy")).thenReturn(false);
        when(templateRepo.existsByArchivedTrueAndNameIgnoreCase("stopy")).thenReturn(true);

        assertThatThrownBy(() -> service.createTemplate(" stopy ", null))
                .isInstanceOf(ConflictException.class)
                .hasMessage(DUPLICATE_ARCHIVED_MESSAGE);
        verify(templateRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void create_nameOfActive_409WithPlainMessage_withoutCheckingArchived() {
        when(templateRepo.countByArchivedFalse()).thenReturn(1L);
        when(templateRepo.existsByArchivedFalseAndNameIgnoreCase("stopy")).thenReturn(true);

        assertThatThrownBy(() -> service.createTemplate("stopy", null))
                .isInstanceOf(ConflictException.class)
                .hasMessage(DUPLICATE_MESSAGE);
        verify(templateRepo, never()).existsByArchivedTrueAndNameIgnoreCase(any());
        verify(templateRepo, never()).saveAndFlush(any());
    }

    @Test
    void create_concurrentDuplicate_fromUniqueIndex_409() {
        when(templateRepo.countByArchivedFalse()).thenReturn(1L);
        when(templateRepo.existsByArchivedFalseAndNameIgnoreCase("Stopy")).thenReturn(false);
        when(templateRepo.saveAndFlush(any(DrawingTemplate.class)))
                .thenThrow(new DataIntegrityViolationException("uq_drawing_template_name_active"));

        assertThatThrownBy(() -> service.createTemplate("Stopy", null))
                .isInstanceOf(ConflictException.class)
                .hasMessage(DUPLICATE_MESSAGE);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void create_whenTenActive_409Limit() {
        when(templateRepo.countByArchivedFalse()).thenReturn(10L);

        assertThatThrownBy(() -> service.createTemplate("Pięty", null))
                .isInstanceOf(ConflictException.class)
                .hasMessage(LIMIT_MESSAGE);
        verify(templateRepo, never()).saveAndFlush(any());
    }

    @Test
    void create_rejectsBlankOrTooLongName() {
        for (String name : Arrays.asList(null, "", "   ", "x".repeat(61), " " + "x".repeat(61) + " ")) {
            assertThatThrownBy(() -> service.createTemplate(name, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage(NAME_MESSAGE);
        }
        verifyNoInteractions(templateRepo, imageRepo, auditLogService);
    }

    @Test
    void create_acceptsSixtyCharacterNameAfterTrim() {
        stubCreateContext(0, 0);

        DrawingTemplateSettingsDTO result = service.createTemplate("  " + "x".repeat(60) + "  ", null);

        assertThat(result.getName()).isEqualTo("x".repeat(60));
    }

    @Test
    void create_jpeg_isConvertedToPng() throws IOException {
        stubCreateContext(0, 0);
        stubImageSave();

        service.createTemplate("Stopy", file("image/jpeg", image(800, 1000, "jpg")));

        DrawingTemplateImage saved = savedImage();
        assertThat(saved.getImage()).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThat(saved.getWidth()).isEqualTo(800);
        assertThat(saved.getHeight()).isEqualTo(1000);
    }

    @Test
    void create_largeImage_isScaledProportionallyTo2048() throws IOException {
        stubCreateContext(0, 0);
        stubImageSave();

        service.createTemplate("Stopy", png(3000, 4000));

        DrawingTemplateImage saved = savedImage();
        assertThat(saved.getWidth()).isEqualTo(1536);
        assertThat(saved.getHeight()).isEqualTo(2048);
        BufferedImage stored = read(saved.getImage());
        assertThat(stored.getWidth()).isEqualTo(1536);
        assertThat(stored.getHeight()).isEqualTo(2048);
    }

    @Test
    void create_landscapeImage_scalesLongerSide() throws IOException {
        stubCreateContext(0, 0);
        stubImageSave();

        service.createTemplate("Stopy", png(5000, 2500));

        DrawingTemplateImage saved = savedImage();
        assertThat(saved.getWidth()).isEqualTo(2048);
        assertThat(saved.getHeight()).isEqualTo(1024);
    }

    @Test
    void create_rejectsUnsupportedContentType() throws IOException {
        byte[] png = image(100, 100, "png");

        assertThatThrownBy(() -> service.createTemplate("Stopy", file("image/gif", png)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Dozwolone formaty szablonu to PNG i JPEG.");
        assertThatThrownBy(() -> service.createTemplate("Stopy", file(null, png)))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(templateRepo, imageRepo);
    }

    @Test
    void create_rejectsFileOver2Mb() {
        byte[] bytes = new byte[2 * 1024 * 1024 + 1];

        assertThatThrownBy(() -> service.createTemplate("Stopy", file("image/png", bytes)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Plik szablonu jest za duży (maksymalnie 2 MB).");
        verifyNoInteractions(templateRepo, imageRepo);
    }

    @Test
    void create_rejectsUnreadableImage() {
        byte[] notAnImage = "to nie jest obraz".getBytes();
        byte[] truncatedPng = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0};

        assertThatThrownBy(() -> service.createTemplate("Stopy", file("image/png", notAnImage)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Plik nie jest poprawnym obrazem PNG ani JPEG.");
        assertThatThrownBy(() -> service.createTemplate("Stopy", file("image/png", truncatedPng)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Plik nie jest poprawnym obrazem PNG ani JPEG.");
        verifyNoInteractions(templateRepo, imageRepo);
    }

    @Test
    void create_rejectsLongerSideOver6000() throws IOException {
        assertThatThrownBy(() -> service.createTemplate("Stopy", png(6001, 10)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Szablon jest za duży (dłuższy bok maksymalnie 6000 px).");
        verifyNoInteractions(templateRepo, imageRepo);
    }

    @Test
    void update_newFile_whenOldImageUsedBySheet_keepsOldImage() throws IOException {
        DrawingTemplate template = template(5L, "Stopy", 1, 50L);
        stubUpdateContext(template);
        when(imageRepo.findInfoById(50L)).thenReturn(Optional.of(imageInfo(50L, 5L, 1000, 1400)));
        stubImageSave();
        stubTemplateSave();
        when(sheetRepo.existsByTemplateImageId(50L)).thenReturn(true);
        when(sheetRepo.countClientsByTemplateId(5L)).thenReturn(3L);

        DrawingTemplateSettingsDTO result = service.updateTemplate(5L, "Stopy", png(800, 600), false);

        assertThat(template.getCurrentImageId()).isEqualTo(90L);
        assertThat(result.getImage().getId()).isEqualTo(90L);
        assertThat(result.getImage().getWidth()).isEqualTo(800);
        assertThat(result.getClientCount()).isEqualTo(3L);
        assertThat(savedImage().getTemplateId()).isEqualTo(5L);
        verify(imageRepo, never()).deleteImageById(anyLong());
        verify(sheetRepo, never()).saveAndFlush(any());
        verify(sheetRepo).rebaseBlankOpenSheets(5L, 90L, 800, 600);

        ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).logUpdate(eq("DrawingTemplate"), eq(5L), eq("Stopy"), before.capture(), after.capture());
        assertThat(toMap(before.getValue())).containsEntry("imageId", 50L).containsEntry("imageWidth", 1000);
        assertThat(toMap(after.getValue())).containsEntry("imageId", 90L).containsEntry("imageWidth", 800);
    }

    @Test
    void update_newFile_whenOldImageUnused_deletesOldImageAfterSwitchingCurrent() throws IOException {
        DrawingTemplate template = template(5L, "Stopy", 1, 50L);
        stubUpdateContext(template);
        when(imageRepo.findInfoById(50L)).thenReturn(Optional.of(imageInfo(50L, 5L, 1000, 1400)));
        stubImageSave();
        stubTemplateSave();
        when(sheetRepo.existsByTemplateImageId(50L)).thenReturn(false);

        service.updateTemplate(5L, "Stopy", png(800, 600), false);

        InOrder inOrder = inOrder(templateRepo, imageRepo, sheetRepo);
        inOrder.verify(imageRepo).saveAndFlush(any(DrawingTemplateImage.class));
        inOrder.verify(templateRepo).saveAndFlush(template);
        inOrder.verify(sheetRepo).lockOpenSheetIdsByTemplateId(5L);
        inOrder.verify(sheetRepo).rebaseBlankOpenSheets(5L, 90L, 800, 600);
        inOrder.verify(sheetRepo).existsByTemplateImageId(50L);
        inOrder.verify(imageRepo).deleteImageById(50L);
        assertThat(template.getCurrentImageId()).isEqualTo(90L);
    }

    @Test
    void update_removeImage_rebasesBlankOpenSheetsToDefaultSize_beforeDeletingOldImage() {
        DrawingTemplate template = template(5L, "Stopy", 1, 50L);
        stubUpdateContext(template);
        when(imageRepo.findInfoById(50L)).thenReturn(Optional.of(imageInfo(50L, 5L, 1000, 1400)));
        stubTemplateSave();
        when(sheetRepo.existsByTemplateImageId(50L)).thenReturn(false);

        service.updateTemplate(5L, "Stopy", null, true);

        InOrder inOrder = inOrder(templateRepo, imageRepo, sheetRepo);
        inOrder.verify(templateRepo).saveAndFlush(template);
        inOrder.verify(sheetRepo).lockOpenSheetIdsByTemplateId(5L);
        inOrder.verify(sheetRepo).rebaseBlankOpenSheets(5L, null, 1536, 2048);
        inOrder.verify(sheetRepo).existsByTemplateImageId(50L);
        inOrder.verify(imageRepo).deleteImageById(50L);
    }

    @Test
    void update_renameOnly_doesNotRebaseSheets() {
        DrawingTemplate template = template(5L, "Stopy", 1, 50L);
        stubUpdateContext(template);
        when(imageRepo.findInfoById(50L)).thenReturn(Optional.of(imageInfo(50L, 5L, 1000, 1400)));
        stubTemplateSave();

        DrawingTemplateSettingsDTO result = service.updateTemplate(5L, "Stopy - grzbiet", null, false);

        assertThat(result.getName()).isEqualTo("Stopy - grzbiet");
        assertThat(template.getCurrentImageId()).isEqualTo(50L);
        verify(sheetRepo, never()).lockOpenSheetIdsByTemplateId(anyLong());
        verify(sheetRepo, never()).rebaseBlankOpenSheets(anyLong(), any(), anyInt(), anyInt());
        verify(imageRepo, never()).deleteImageById(anyLong());
    }

    @Test
    void update_removeImage_clearsCurrentImage_andDeletesUnusedOne() {
        DrawingTemplate template = template(5L, "Stopy", 1, 50L);
        stubUpdateContext(template);
        when(imageRepo.findInfoById(50L)).thenReturn(Optional.of(imageInfo(50L, 5L, 1000, 1400)));
        stubTemplateSave();
        when(sheetRepo.existsByTemplateImageId(50L)).thenReturn(false);

        DrawingTemplateSettingsDTO result = service.updateTemplate(5L, "Stopy", null, true);

        assertThat(result.getImage()).isNull();
        assertThat(template.getCurrentImageId()).isNull();
        verify(imageRepo).deleteImageById(50L);
        verify(imageRepo, never()).saveAndFlush(any());
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).logUpdate(eq("DrawingTemplate"), eq(5L), eq("Stopy"), any(), after.capture());
        assertThat(toMap(after.getValue())).containsEntry("imageId", null);
    }

    @Test
    void update_fileAndRemoveImageTogether_400() throws IOException {
        MockMultipartFile file = png(100, 100);

        assertThatThrownBy(() -> service.updateTemplate(5L, "Stopy", file, true))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nie można jednocześnie wgrać nowego obrazu i usunąć obecnego.");
        verifyNoInteractions(templateRepo, imageRepo, auditLogService);
    }

    @Test
    void update_rename_trimsAndAudits_withoutTouchingImages() {
        DrawingTemplate template = template(5L, "Stopy", 2, null);
        stubUpdateContext(template);
        stubTemplateSave();

        DrawingTemplateSettingsDTO result = service.updateTemplate(5L, "  Stopy - grzbiet  ", null, false);

        assertThat(result.getName()).isEqualTo("Stopy - grzbiet");
        assertThat(template.getName()).isEqualTo("Stopy - grzbiet");
        assertThat(template.getUpdatedAt()).isAfter(CREATED_AT);
        verifyNoInteractions(imageRepo);
        ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).logUpdate(eq("DrawingTemplate"), eq(5L), eq("Stopy - grzbiet"),
                before.capture(), after.capture());
        assertThat(toMap(before.getValue())).containsEntry("name", "Stopy");
        assertThat(toMap(after.getValue())).containsEntry("name", "Stopy - grzbiet");
    }

    @Test
    void update_withoutChanges_doesNotSaveOrAudit() {
        DrawingTemplate template = template(5L, "Stopy", 2, null);
        stubUpdateContext(template);

        DrawingTemplateSettingsDTO result = service.updateTemplate(5L, "Stopy", null, false);

        assertThat(result.getName()).isEqualTo("Stopy");
        verify(templateRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void update_duplicateNameOfOtherActive_409() {
        DrawingTemplate template = template(5L, "Stopy", 1, null);
        when(templateRepo.lockById(5L)).thenReturn(Optional.of(template));
        when(templateRepo.existsByArchivedFalseAndNameIgnoreCaseAndIdNot("Dłonie", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateTemplate(5L, "Dłonie", null, false))
                .isInstanceOf(ConflictException.class)
                .hasMessage(DUPLICATE_MESSAGE);
        assertThat(template.getName()).isEqualTo("Stopy");
    }

    @Test
    void update_renameToNameOfArchived_409WithArchivedMessage() {
        DrawingTemplate template = template(5L, "Dłonie", 1, null);
        when(templateRepo.lockById(5L)).thenReturn(Optional.of(template));
        when(templateRepo.existsByArchivedFalseAndNameIgnoreCaseAndIdNot("STOPY", 5L)).thenReturn(false);
        when(templateRepo.existsByArchivedTrueAndNameIgnoreCaseAndIdNot("STOPY", 5L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateTemplate(5L, "STOPY", null, false))
                .isInstanceOf(ConflictException.class)
                .hasMessage(DUPLICATE_ARCHIVED_MESSAGE);
        assertThat(template.getName()).isEqualTo("Dłonie");
        verify(templateRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void update_changingOnlyCaseOfOwnName_isSaved() {
        DrawingTemplate template = template(5L, "Stopy", 1, null);
        stubUpdateContext(template);
        when(templateRepo.existsByArchivedTrueAndNameIgnoreCaseAndIdNot("stopy", 5L)).thenReturn(false);
        stubTemplateSave();

        DrawingTemplateSettingsDTO result = service.updateTemplate(5L, "stopy", null, false);

        assertThat(result.getName()).isEqualTo("stopy");
        assertThat(template.getName()).isEqualTo("stopy");
        verify(templateRepo).existsByArchivedFalseAndNameIgnoreCaseAndIdNot("stopy", 5L);
        verify(auditLogService).logUpdate(eq("DrawingTemplate"), eq(5L), eq("stopy"), any(), any());
    }

    @Test
    void update_archived_409() {
        when(templateRepo.lockById(5L)).thenReturn(Optional.of(archived(5L, "Stopy", CREATED_AT)));

        assertThatThrownBy(() -> service.updateTemplate(5L, "Nowa", null, false))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ARCHIVED_MESSAGE);
        verify(templateRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void update_unknown_404() {
        when(templateRepo.lockById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTemplate(5L, "Stopy", null, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_blankName_400() {
        assertThatThrownBy(() -> service.updateTemplate(5L, " ", null, false))
                .isInstanceOf(ValidationException.class)
                .hasMessage(NAME_MESSAGE);
        verifyNoInteractions(templateRepo);
    }

    @Test
    void delete_withoutSheets_hardDeletes_andAuditsDelete() {
        DrawingTemplate template = template(5L, "Stopy", 1, 50L);
        when(templateRepo.lockById(5L)).thenReturn(Optional.of(template));
        when(imageRepo.findInfoById(50L)).thenReturn(Optional.of(imageInfo(50L, 5L, 1000, 1400)));
        when(sheetRepo.existsByTemplateId(5L)).thenReturn(false);

        DrawingTemplateDeleteResult result = service.deleteTemplate(5L);

        assertThat(result).isEqualTo(DrawingTemplateDeleteResult.DELETED);
        verify(templateRepo).delete(template);
        verify(templateRepo).flush();
        assertThat(template.isArchived()).isFalse();
        ArgumentCaptor<Object> deleted = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).logDelete(eq("DrawingTemplate"), eq(5L), eq("Stopy"), deleted.capture());
        assertThat(toMap(deleted.getValue())).containsEntry("imageId", 50L);
    }

    @Test
    void delete_withSheets_archives_andAuditsUpdate() {
        DrawingTemplate template = template(5L, "Stopy", 1, null);
        when(templateRepo.lockById(5L)).thenReturn(Optional.of(template));
        when(sheetRepo.existsByTemplateId(5L)).thenReturn(true);
        stubTemplateSave();

        DrawingTemplateDeleteResult result = service.deleteTemplate(5L);

        assertThat(result).isEqualTo(DrawingTemplateDeleteResult.ARCHIVED);
        assertThat(template.isArchived()).isTrue();
        assertThat(template.getArchivedAt()).isNotNull();
        verify(templateRepo, never()).delete(any());
        ArgumentCaptor<Object> before = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).logUpdate(eq("DrawingTemplate"), eq(5L), eq("Stopy"), before.capture(), after.capture());
        assertThat(toMap(before.getValue())).containsEntry("archived", false);
        assertThat(toMap(after.getValue())).containsEntry("archived", true);
        verify(auditLogService, never()).logDelete(any(), any(), any(), any());
    }

    @Test
    void delete_concurrentSheetBlocksHardDelete_409() {
        DrawingTemplate template = template(5L, "Stopy", 1, null);
        when(templateRepo.lockById(5L)).thenReturn(Optional.of(template));
        when(sheetRepo.existsByTemplateId(5L)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("fk_drawing_sheet_template")).when(templateRepo).flush();

        assertThatThrownBy(() -> service.deleteTemplate(5L)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void delete_archived_409_andUnknown_404() {
        when(templateRepo.lockById(5L)).thenReturn(Optional.of(archived(5L, "Stopy", CREATED_AT)));
        when(templateRepo.lockById(6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTemplate(5L))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ARCHIVED_MESSAGE);
        assertThatThrownBy(() -> service.deleteTemplate(6L)).isInstanceOf(ResourceNotFoundException.class);
        verify(templateRepo, never()).delete(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void restore_withFreeSlot_activatesAtEndOfOrder_andAudits() {
        DrawingTemplate template = archived(5L, "Stopy", CREATED_AT);
        when(templateRepo.lockById(5L)).thenReturn(Optional.of(template));
        when(templateRepo.countByArchivedFalse()).thenReturn(9L);
        when(templateRepo.existsByArchivedFalseAndNameIgnoreCase("Stopy")).thenReturn(false);
        when(templateRepo.findMaxActiveDisplayOrder()).thenReturn(9);
        when(sheetRepo.countClientsByTemplateId(5L)).thenReturn(2L);
        stubTemplateSave();

        DrawingTemplateSettingsDTO result = service.restoreTemplate(5L);

        assertThat(result.getArchived()).isFalse();
        assertThat(result.getArchivedAt()).isNull();
        assertThat(result.getDisplayOrder()).isEqualTo(10);
        assertThat(result.getClientCount()).isEqualTo(2L);
        assertThat(template.isArchived()).isFalse();
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).logUpdate(eq("DrawingTemplate"), eq(5L), eq("Stopy"), any(), after.capture());
        assertThat(toMap(after.getValue())).containsEntry("archived", false).containsEntry("displayOrder", 10);
    }

    @Test
    void restore_whenActiveHasSameName_409Duplicate() {
        DrawingTemplate template = archived(5L, "Stopy", CREATED_AT);
        when(templateRepo.lockById(5L)).thenReturn(Optional.of(template));
        when(templateRepo.countByArchivedFalse()).thenReturn(3L);
        when(templateRepo.existsByArchivedFalseAndNameIgnoreCase("Stopy")).thenReturn(true);

        assertThatThrownBy(() -> service.restoreTemplate(5L))
                .isInstanceOf(ConflictException.class)
                .hasMessage(DUPLICATE_MESSAGE);
        assertThat(template.isArchived()).isTrue();
        verifyNoInteractions(auditLogService);
    }

    @Test
    void restore_whenTenActive_409Limit() {
        DrawingTemplate template = archived(5L, "Stopy", CREATED_AT);
        when(templateRepo.lockById(5L)).thenReturn(Optional.of(template));
        when(templateRepo.countByArchivedFalse()).thenReturn(10L);

        assertThatThrownBy(() -> service.restoreTemplate(5L))
                .isInstanceOf(ConflictException.class)
                .hasMessage(LIMIT_MESSAGE);
        assertThat(template.isArchived()).isTrue();
        verify(templateRepo, never()).saveAndFlush(any());
    }

    @Test
    void restore_notArchived_409_andUnknown_404() {
        when(templateRepo.lockById(5L)).thenReturn(Optional.of(template(5L, "Stopy", 1, null)));
        when(templateRepo.lockById(6L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restoreTemplate(5L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Szablon nie jest zarchiwizowany.");
        assertThatThrownBy(() -> service.restoreTemplate(6L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reorder_setsDisplayOrderOneToN_inGivenOrder_andAuditsChanged() {
        DrawingTemplate first = template(1L, "Stopy", 1, null);
        DrawingTemplate second = template(2L, "Dłonie", 2, null);
        DrawingTemplate third = template(3L, "Pięty", 3, null);
        List<DrawingTemplate> active = new ArrayList<>(List.of(first, second, third));
        when(templateRepo.findByArchivedFalseOrderByDisplayOrderAscIdAsc()).thenAnswer(invocation -> active.stream()
                .sorted((a, b) -> Integer.compare(a.getDisplayOrder(), b.getDisplayOrder()))
                .toList());
        when(templateRepo.findByArchivedTrueOrderByArchivedAtDescIdDesc()).thenReturn(List.of());
        when(sheetRepo.countClientsPerTemplate()).thenReturn(List.of());

        List<DrawingTemplateSettingsDTO> result = service.reorderTemplates(List.of(3L, 1L, 2L));

        assertThat(third.getDisplayOrder()).isEqualTo(1);
        assertThat(first.getDisplayOrder()).isEqualTo(2);
        assertThat(second.getDisplayOrder()).isEqualTo(3);
        assertThat(result).extracting(DrawingTemplateSettingsDTO::getId).containsExactly(3L, 1L, 2L);
        assertThat(result).extracting(DrawingTemplateSettingsDTO::getDisplayOrder).containsExactly(1, 2, 3);
        verify(templateRepo).saveAllAndFlush(List.of(third, first, second));
        verify(auditLogService, times(3)).logUpdate(eq("DrawingTemplate"), any(), any(), any(), any());
        ArgumentCaptor<Object> after = ArgumentCaptor.forClass(Object.class);
        verify(auditLogService).logUpdate(eq("DrawingTemplate"), eq(3L), eq("Pięty"), any(), after.capture());
        assertThat(toMap(after.getValue())).containsEntry("displayOrder", 1);
    }

    @Test
    void reorder_sameOrder_savesAndAuditsNothing() {
        List<DrawingTemplate> active = List.of(template(1L, "Stopy", 1, null), template(2L, "Dłonie", 2, null));
        when(templateRepo.findByArchivedFalseOrderByDisplayOrderAscIdAsc()).thenReturn(active);
        when(templateRepo.findByArchivedTrueOrderByArchivedAtDescIdDesc()).thenReturn(List.of());
        when(sheetRepo.countClientsPerTemplate()).thenReturn(List.of());

        service.reorderTemplates(List.of(1L, 2L));

        verify(templateRepo, never()).saveAllAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void reorder_invalidIds_400() {
        List<DrawingTemplate> active = List.of(template(1L, "Stopy", 1, null), template(2L, "Dłonie", 2, null),
                template(3L, "Pięty", 3, null));
        when(templateRepo.findByArchivedFalseOrderByDisplayOrderAscIdAsc()).thenReturn(active);

        for (List<Long> ids : Arrays.asList(List.of(1L, 2L), List.of(1L, 2L, 4L), List.of(1L, 2L, 2L),
                List.of(1L, 2L, 3L, 3L), Arrays.asList(1L, 2L, null), null)) {
            assertThatThrownBy(() -> service.reorderTemplates(ids))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Lista szablonów do ustawienia kolejności jest nieaktualna. Odśwież stronę.");
        }
        assertThat(active).extracting(DrawingTemplate::getDisplayOrder).containsExactly(1, 2, 3);
        verify(templateRepo, never()).saveAllAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void getTemplates_activeByOrderThenArchived_withImagesAndClientCounts_inConstantQueries() {
        DrawingTemplate feet = template(1L, "Stopy", 1, 50L);
        DrawingTemplate hands = template(2L, "Dłonie", 2, null);
        DrawingTemplate newer = archived(3L, "Pięty", CREATED_AT.plusDays(2));
        DrawingTemplate older = archived(4L, "Paznokcie", CREATED_AT.plusDays(1));
        when(templateRepo.findByArchivedFalseOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(feet, hands));
        when(templateRepo.findByArchivedTrueOrderByArchivedAtDescIdDesc()).thenReturn(List.of(newer, older));
        when(imageRepo.findInfoByIdIn(anyCollection())).thenReturn(List.of(imageInfo(50L, 1L, 1000, 1400)));
        when(sheetRepo.countClientsPerTemplate()).thenReturn(List.of(usage(1L, 2L), usage(3L, 1L)));

        List<DrawingTemplateSettingsDTO> result = service.getTemplates();

        assertThat(result).extracting(DrawingTemplateSettingsDTO::getId).containsExactly(1L, 2L, 3L, 4L);
        assertThat(result).extracting(DrawingTemplateSettingsDTO::getClientCount).containsExactly(2L, 0L, 1L, 0L);
        assertThat(result).extracting(DrawingTemplateSettingsDTO::getArchived).containsExactly(false, false, true, true);
        assertThat(result.get(0).getImage().getId()).isEqualTo(50L);
        assertThat(result.get(0).getImage().getSizeBytes()).isEqualTo(1234);
        assertThat(result.get(1).getImage()).isNull();
        assertThat(result.get(2).getArchivedAt()).isEqualTo(CREATED_AT.plusDays(2));
        verify(imageRepo, times(1)).findInfoByIdIn(anyCollection());
        verify(sheetRepo, times(1)).countClientsPerTemplate();
        verify(sheetRepo, never()).countClientsByTemplateId(anyLong());
    }

    @Test
    void getTemplateImage_returnsBytes_or404() {
        when(imageRepo.findImageById(1L)).thenReturn(Optional.of(new byte[]{1, 2}));
        when(imageRepo.findImageById(2L)).thenReturn(Optional.empty());

        assertThat(service.getTemplateImage(1L)).containsExactly(1, 2);
        assertThatThrownBy(() -> service.getTemplateImage(2L)).isInstanceOf(ResourceNotFoundException.class);
    }
}

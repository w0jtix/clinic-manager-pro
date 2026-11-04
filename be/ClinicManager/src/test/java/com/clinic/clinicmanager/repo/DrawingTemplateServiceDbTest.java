package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.DTO.DrawingTemplateSettingsDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientCard;
import com.clinic.clinicmanager.model.DrawingLayer;
import com.clinic.clinicmanager.model.DrawingSheet;
import com.clinic.clinicmanager.model.DrawingTemplate;
import com.clinic.clinicmanager.model.constants.DrawingSheetStatus;
import com.clinic.clinicmanager.model.constants.DrawingTemplateDeleteResult;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.impl.DrawingTemplateServiceImpl;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(DrawingTemplateServiceImpl.class)
class DrawingTemplateServiceDbTest extends AbstractPostgresTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);

    @Autowired
    private DrawingTemplateServiceImpl service;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private DrawingTemplateRepo templateRepo;

    @Autowired
    private DrawingTemplateImageRepo imageRepo;

    @Autowired
    private DrawingSheetRepo sheetRepo;

    @Autowired
    private DrawingLayerRepo layerRepo;

    @Autowired
    private ClientCardRepo clientCardRepo;

    @MockBean
    private AuditLogService auditLogService;

    private static MockMultipartFile png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("file", "template.png", "image/png", output.toByteArray());
    }

    private ClientCard cardFor(String lastName) {
        Client client = em.persistAndFlush(Client.builder().firstName("Jan").lastName(lastName).build());
        clientCardRepo.insertIfAbsent(client.getId(), CREATED_AT, 1L);
        return clientCardRepo.findByClientId(client.getId()).orElseThrow();
    }

    private DrawingSheet sheet(ClientCard card, Long templateId, int number) {
        return sheet(card, templateId, number, DrawingSheetStatus.OPEN);
    }

    private DrawingSheet sheet(ClientCard card, Long templateId, int number, DrawingSheetStatus status) {
        DrawingTemplate template = templateRepo.findById(templateId).orElseThrow();
        return sheetRepo.saveAndFlush(DrawingSheet.builder()
                .card(card).templateId(templateId).templateImageId(template.getCurrentImageId())
                .sheetNumber(number).width(1536).height(2048)
                .status(status)
                .createdAt(CREATED_AT)
                .build());
    }

    private void layer(DrawingSheet sheet) {
        layerRepo.saveAndFlush(DrawingLayer.builder()
                .sheet(sheet).authorUserId(7L)
                .fileKey("drawings/7-" + sheet.getId() + ".bin")
                .sizeBytes(10).contentSha256("a".repeat(64))
                .createdAt(CREATED_AT).updatedAt(CREATED_AT).updatedByUserId(7L)
                .build());
    }

    private DrawingSheet reloadSheet(Long id) {
        em.flush();
        em.clear();
        return sheetRepo.findById(id).orElseThrow();
    }

    private DrawingTemplate reload(Long id) {
        em.flush();
        em.clear();
        return templateRepo.findById(id).orElseThrow();
    }

    @Test
    void replaceImage_rebasesOnlyBlankOpenSheets_andKeepsOldImageUsedByOthers() throws IOException {
        DrawingTemplateSettingsDTO created = service.createTemplate("Stopy", png(100, 200));
        Long imageA = created.getImage().getId();
        DrawingSheet blank = sheet(cardFor("Kowalski"), created.getId(), 1);
        DrawingSheet drawn = sheet(cardFor("Nowak"), created.getId(), 1);
        layer(drawn);
        DrawingSheet closed = sheet(cardFor("Wiśniewski"), created.getId(), 1, DrawingSheetStatus.CLOSED);

        DrawingTemplateSettingsDTO updated = service.updateTemplate(created.getId(), "Stopy", png(300, 400), false);

        Long imageB = updated.getImage().getId();
        assertThat(imageB).isNotEqualTo(imageA);
        assertThat(reload(created.getId()).getCurrentImageId()).isEqualTo(imageB);
        assertThat(imageRepo.findInfoById(imageA)).isPresent();
        assertThat(imageRepo.findInfoById(imageB)).hasValueSatisfying(info -> {
            assertThat(info.getWidth()).isEqualTo(300);
            assertThat(info.getHeight()).isEqualTo(400);
        });
        assertThat(reloadSheet(blank.getId()))
                .extracting(DrawingSheet::getTemplateImageId, DrawingSheet::getWidth, DrawingSheet::getHeight,
                        DrawingSheet::getStatus)
                .containsExactly(imageB, 300, 400, DrawingSheetStatus.OPEN);
        assertThat(reloadSheet(drawn.getId()))
                .extracting(DrawingSheet::getTemplateImageId, DrawingSheet::getWidth, DrawingSheet::getHeight)
                .containsExactly(imageA, 1536, 2048);
        assertThat(reloadSheet(closed.getId()))
                .extracting(DrawingSheet::getTemplateImageId, DrawingSheet::getWidth, DrawingSheet::getHeight,
                        DrawingSheet::getStatus)
                .containsExactly(imageA, 1536, 2048, DrawingSheetStatus.CLOSED);
        assertThat(updated.getClientCount()).isEqualTo(3L);
    }

    @Test
    void replaceImage_whenOnlyBlankSheetUsesOldImage_rebasesSheetAndDeletesOldImage() throws IOException {
        DrawingTemplateSettingsDTO created = service.createTemplate("Stopy", png(100, 200));
        Long imageA = created.getImage().getId();
        DrawingSheet blank = sheet(cardFor("Kowalski"), created.getId(), 1);

        DrawingTemplateSettingsDTO updated = service.updateTemplate(created.getId(), "Stopy", png(300, 400), false);

        Long imageB = updated.getImage().getId();
        assertThat(reloadSheet(blank.getId()))
                .extracting(DrawingSheet::getTemplateImageId, DrawingSheet::getWidth, DrawingSheet::getHeight)
                .containsExactly(imageB, 300, 400);
        assertThat(imageRepo.findById(imageA)).isEmpty();
        assertThat(imageRepo.findById(imageB)).isPresent();
    }

    @Test
    void removeImage_rebasesBlankOpenSheetToNoImageAndDefaultSize() throws IOException {
        DrawingTemplateSettingsDTO created = service.createTemplate("Stopy", png(100, 200));
        Long imageA = created.getImage().getId();
        DrawingSheet blank = sheetRepo.saveAndFlush(DrawingSheet.builder()
                .card(cardFor("Kowalski")).templateId(created.getId()).templateImageId(imageA)
                .sheetNumber(1).width(100).height(200)
                .status(DrawingSheetStatus.OPEN)
                .createdAt(CREATED_AT)
                .build());

        service.updateTemplate(created.getId(), "Stopy", null, true);

        assertThat(reloadSheet(blank.getId()))
                .extracting(DrawingSheet::getTemplateImageId, DrawingSheet::getWidth, DrawingSheet::getHeight)
                .containsExactly(null, 1536, 2048);
        assertThat(imageRepo.findById(imageA)).isEmpty();
    }

    @Test
    void renameOnly_leavesBlankOpenSheetUntouched() throws IOException {
        DrawingTemplateSettingsDTO created = service.createTemplate("Stopy", png(100, 200));
        Long imageA = created.getImage().getId();
        DrawingSheet blank = sheet(cardFor("Kowalski"), created.getId(), 1);

        service.updateTemplate(created.getId(), "Stopy - grzbiet", null, false);

        assertThat(reloadSheet(blank.getId()))
                .extracting(DrawingSheet::getTemplateImageId, DrawingSheet::getWidth, DrawingSheet::getHeight)
                .containsExactly(imageA, 1536, 2048);
        assertThat(imageRepo.findById(imageA)).isPresent();
    }

    @Test
    void replaceImage_leavesBlankOpenSheetOfOtherTemplateUntouched() throws IOException {
        DrawingTemplateSettingsDTO feet = service.createTemplate("Stopy", png(100, 200));
        DrawingTemplateSettingsDTO hands = service.createTemplate("Dłonie", png(120, 220));
        Long handsImage = hands.getImage().getId();
        DrawingSheet otherBlank = sheet(cardFor("Kowalski"), hands.getId(), 1);

        service.updateTemplate(feet.getId(), "Stopy", png(300, 400), false);

        assertThat(reloadSheet(otherBlank.getId()))
                .extracting(DrawingSheet::getTemplateId, DrawingSheet::getTemplateImageId,
                        DrawingSheet::getWidth, DrawingSheet::getHeight)
                .containsExactly(hands.getId(), handsImage, 1536, 2048);
        assertThat(imageRepo.findById(handsImage)).isPresent();
    }

    @Test
    void replaceImage_withoutSheets_deletesOldImage() throws IOException {
        DrawingTemplateSettingsDTO created = service.createTemplate("Stopy", png(100, 200));
        Long imageA = created.getImage().getId();

        DrawingTemplateSettingsDTO updated = service.updateTemplate(created.getId(), "Stopy", png(300, 400), false);
        em.flush();
        em.clear();

        assertThat(imageRepo.findById(imageA)).isEmpty();
        assertThat(imageRepo.findById(updated.getImage().getId())).isPresent();
    }

    @Test
    void removeImage_clearsCurrentImage_andDeletesUnusedImage() throws IOException {
        DrawingTemplateSettingsDTO created = service.createTemplate("Stopy", png(100, 200));
        Long imageA = created.getImage().getId();

        DrawingTemplateSettingsDTO updated = service.updateTemplate(created.getId(), "Stopy", null, true);

        assertThat(updated.getImage()).isNull();
        assertThat(reload(created.getId()).getCurrentImageId()).isNull();
        assertThat(imageRepo.findById(imageA)).isEmpty();
    }

    @Test
    void delete_withoutSheets_removesTemplateRowAndAllImages() throws IOException {
        DrawingTemplateSettingsDTO created = service.createTemplate("Stopy", png(100, 200));
        Long imageA = created.getImage().getId();
        Long imageB = service.updateTemplate(created.getId(), "Stopy", png(300, 400), false).getImage().getId();

        DrawingTemplateDeleteResult result = service.deleteTemplate(created.getId());
        em.flush();
        em.clear();

        assertThat(result).isEqualTo(DrawingTemplateDeleteResult.DELETED);
        assertThat(templateRepo.findById(created.getId())).isEmpty();
        assertThat(imageRepo.findById(imageA)).isEmpty();
        assertThat(imageRepo.findById(imageB)).isEmpty();
    }

    @Test
    void delete_withSheet_archives_thenRestoreGoesToEndOfOrder() throws IOException {
        DrawingTemplateSettingsDTO feet = service.createTemplate("Stopy", png(100, 200));
        DrawingTemplateSettingsDTO hands = service.createTemplate("Dłonie", null);
        sheet(cardFor("Kowalski"), feet.getId(), 1);

        DrawingTemplateDeleteResult result = service.deleteTemplate(feet.getId());

        assertThat(result).isEqualTo(DrawingTemplateDeleteResult.ARCHIVED);
        DrawingTemplate archived = reload(feet.getId());
        assertThat(archived.isArchived()).isTrue();
        assertThat(archived.getArchivedAt()).isNotNull();
        assertThat(templateRepo.findByArchivedFalseOrderByDisplayOrderAscIdAsc())
                .extracting(DrawingTemplate::getId).containsExactly(hands.getId());
        assertThat(imageRepo.findById(feet.getImage().getId())).isPresent();

        DrawingTemplateSettingsDTO restored = service.restoreTemplate(feet.getId());

        assertThat(restored.getArchived()).isFalse();
        assertThat(restored.getDisplayOrder()).isEqualTo(3);
        DrawingTemplate reloaded = reload(feet.getId());
        assertThat(reloaded.isArchived()).isFalse();
        assertThat(reloaded.getArchivedAt()).isNull();
        assertThat(templateRepo.findByArchivedFalseOrderByDisplayOrderAscIdAsc())
                .extracting(DrawingTemplate::getId).containsExactly(hands.getId(), feet.getId());
    }

    @Test
    void create_withNameOfArchived_409_andNothingIsSaved() {
        Long feet = service.createTemplate("Stopy", null).getId();
        sheet(cardFor("Kowalski"), feet, 1);
        service.deleteTemplate(feet);

        assertThatThrownBy(() -> service.createTemplate(" stopy ", null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Szablon o tej nazwie już istnieje.");
        em.flush();
        em.clear();
        assertThat(templateRepo.findAll()).extracting(DrawingTemplate::getId).containsExactly(feet);
    }

    @Test
    void rename_toNameOfArchived_409_butOwnNameCaseChangeIsSaved() {
        Long feet = service.createTemplate("Stopy", null).getId();
        Long hands = service.createTemplate("Dłonie", null).getId();
        sheet(cardFor("Kowalski"), feet, 1);
        service.deleteTemplate(feet);

        assertThatThrownBy(() -> service.updateTemplate(hands, "STOPY", null, false))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Szablon o tej nazwie już istnieje.");
        assertThat(reload(hands).getName()).isEqualTo("Dłonie");

        DrawingTemplateSettingsDTO renamed = service.updateTemplate(hands, "DŁONIE", null, false);

        assertThat(renamed.getName()).isEqualTo("DŁONIE");
        assertThat(reload(hands).getName()).isEqualTo("DŁONIE");
    }

    @Test
    void create_withNameOfActive_409PlainMessage() {
        service.createTemplate("Stopy", null);

        assertThatThrownBy(() -> service.createTemplate("stopy", null))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Szablon o tej nazwie już istnieje.");
        assertThat(templateRepo.count()).isEqualTo(1);
    }

    @Test
    void reorder_persistsDisplayOrderOneToN() {
        Long first = service.createTemplate("Stopy", null).getId();
        Long second = service.createTemplate("Dłonie", null).getId();
        Long third = service.createTemplate("Pięty", null).getId();

        List<DrawingTemplateSettingsDTO> result = service.reorderTemplates(List.of(third, first, second));
        em.flush();
        em.clear();

        assertThat(result).extracting(DrawingTemplateSettingsDTO::getId).containsExactly(third, first, second);
        assertThat(templateRepo.findByArchivedFalseOrderByDisplayOrderAscIdAsc())
                .extracting(DrawingTemplate::getId, DrawingTemplate::getDisplayOrder)
                .containsExactly(
                        tuple(third, 1),
                        tuple(first, 2),
                        tuple(second, 3));
    }

    @Test
    void getTemplates_countsDistinctClients_andListsActiveBeforeArchived() throws IOException {
        Long feet = service.createTemplate("Stopy", png(100, 200)).getId();
        Long hands = service.createTemplate("Dłonie", null).getId();
        Long heels = service.createTemplate("Pięty", null).getId();
        ClientCard kowalski = cardFor("Kowalski");
        ClientCard nowak = cardFor("Nowak");
        sheet(kowalski, feet, 1);
        sheet(nowak, feet, 1);
        sheet(nowak, heels, 1);
        service.deleteTemplate(heels);
        service.reorderTemplates(List.of(hands, feet));

        List<DrawingTemplateSettingsDTO> result = service.getTemplates();

        assertThat(result).extracting(DrawingTemplateSettingsDTO::getId).containsExactly(hands, feet, heels);
        assertThat(result).extracting(DrawingTemplateSettingsDTO::getClientCount).containsExactly(0L, 2L, 1L);
        assertThat(result).extracting(DrawingTemplateSettingsDTO::getArchived).containsExactly(false, false, true);
        assertThat(result.get(1).getImage().getWidth()).isEqualTo(100);
        assertThat(result.get(0).getImage()).isNull();
    }
}

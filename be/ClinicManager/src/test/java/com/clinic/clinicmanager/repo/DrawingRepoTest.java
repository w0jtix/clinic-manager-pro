package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientCard;
import com.clinic.clinicmanager.model.DrawingLayer;
import com.clinic.clinicmanager.model.DrawingSheet;
import com.clinic.clinicmanager.model.DrawingTemplate;
import com.clinic.clinicmanager.model.DrawingTemplateImage;
import com.clinic.clinicmanager.model.constants.DrawingSheetStatus;
import com.clinic.clinicmanager.repo.projection.DrawingTemplateImageInfoProjection;
import com.clinic.clinicmanager.repo.projection.DrawingTemplateUsageProjection;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DrawingRepoTest extends AbstractPostgresTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);

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

    @Autowired
    private ClientRepo clientRepo;

    private ClientCard cardFor(String lastName) {
        Client client = em.persistAndFlush(Client.builder().firstName("Jan").lastName(lastName).build());
        clientCardRepo.insertIfAbsent(client.getId(), CREATED_AT, 1L);
        return clientCardRepo.findByClientId(client.getId()).orElseThrow();
    }

    private DrawingTemplate template(String name, int displayOrder) {
        return templateRepo.saveAndFlush(DrawingTemplate.builder()
                .name(name).displayOrder(displayOrder)
                .createdAt(CREATED_AT).createdByUserId(1L).updatedAt(CREATED_AT)
                .build());
    }

    private DrawingTemplate archivedTemplate(String name, LocalDateTime archivedAt) {
        DrawingTemplate template = template(name, 99);
        template.archive(archivedAt);
        return templateRepo.saveAndFlush(template);
    }

    private DrawingTemplateImage image(DrawingTemplate template) {
        DrawingTemplateImage image = imageRepo.saveAndFlush(DrawingTemplateImage.builder()
                .templateId(template.getId())
                .image(new byte[]{1, 2, 3})
                .width(1000).height(1400).sizeBytes(3)
                .createdAt(CREATED_AT).createdByUserId(1L)
                .build());
        template.setCurrentImageId(image.getId());
        templateRepo.saveAndFlush(template);
        return image;
    }

    private DrawingTemplateImage extraImage(DrawingTemplate template) {
        return imageRepo.saveAndFlush(DrawingTemplateImage.builder()
                .templateId(template.getId()).image(new byte[]{9}).width(1).height(1).sizeBytes(1)
                .createdAt(CREATED_AT).build());
    }

    private DrawingSheet sheet(ClientCard card, DrawingTemplate template, int number, DrawingSheetStatus status) {
        return sheetRepo.saveAndFlush(DrawingSheet.builder()
                .card(card).templateId(template.getId()).templateImageId(template.getCurrentImageId())
                .sheetNumber(number)
                .width(1536).height(2048)
                .status(status)
                .createdAt(CREATED_AT).createdByUserId(1L)
                .build());
    }

    private DrawingLayer layer(DrawingSheet sheet, long author, LocalDateTime createdAt) {
        return layerRepo.saveAndFlush(DrawingLayer.builder()
                .sheet(sheet).authorUserId(author)
                .fileKey("drawings/" + author + "-" + sheet.getId() + ".bin")
                .sizeBytes(10).contentSha256("a".repeat(64))
                .createdAt(createdAt).updatedAt(createdAt).updatedByUserId(author)
                .build());
    }

    private int executeNative(String sql, Object... params) {
        var query = em.getEntityManager().createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            query.setParameter(i + 1, params[i]);
        }
        return query.executeUpdate();
    }

    @Test
    void template_activeNameUniqueIgnoringCase() {
        template("Stopy", 1);

        assertThatThrownBy(() -> template("stopy", 2)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void template_archivedNameBlocksActiveDuplicateIgnoringCase() {
        archivedTemplate("Stopy", CREATED_AT);

        assertThatThrownBy(() -> template("STOPY", 1)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void template_nameExistenceQueries_splitByArchivedFlag_andExcludeOwnId() {
        DrawingTemplate archived = archivedTemplate("Stopy", CREATED_AT);
        DrawingTemplate active = template("Dłonie", 1);

        assertThat(templateRepo.existsByArchivedTrueAndNameIgnoreCase("sTOPY")).isTrue();
        assertThat(templateRepo.existsByArchivedFalseAndNameIgnoreCase("sTOPY")).isFalse();
        assertThat(templateRepo.existsByArchivedTrueAndNameIgnoreCaseAndIdNot("stopy", active.getId())).isTrue();
        assertThat(templateRepo.existsByArchivedTrueAndNameIgnoreCaseAndIdNot("stopy", archived.getId())).isFalse();
        assertThat(templateRepo.existsByArchivedFalseAndNameIgnoreCase("dłonie")).isTrue();
        assertThat(templateRepo.existsByArchivedFalseAndNameIgnoreCaseAndIdNot("DŁONIE", active.getId())).isFalse();
        assertThat(templateRepo.existsByArchivedTrueAndNameIgnoreCase("dłonie")).isFalse();
    }

    @Test
    void template_archivedFlagAndArchivedAtMustAgree() {
        DrawingTemplate template = template("Stopy", 1);

        assertThatThrownBy(() -> executeNative(
                "UPDATE drawing_template SET archived = true WHERE id = ?", template.getId()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void template_listQueries_orderActiveByDisplayOrder_andArchivedByArchivedAtDesc() {
        DrawingTemplate second = template("Dłonie", 2);
        DrawingTemplate first = template("Stopy", 1);
        DrawingTemplate oldArchived = archivedTemplate("Paznokcie", CREATED_AT);
        DrawingTemplate newArchived = archivedTemplate("Pięty", CREATED_AT.plusDays(1));
        em.clear();

        assertThat(templateRepo.findByArchivedFalseOrderByDisplayOrderAscIdAsc())
                .extracting(DrawingTemplate::getId).containsExactly(first.getId(), second.getId());
        assertThat(templateRepo.findByArchivedTrueOrderByArchivedAtDescIdDesc())
                .extracting(DrawingTemplate::getId).containsExactly(newArchived.getId(), oldArchived.getId());
        assertThat(templateRepo.countByArchivedFalse()).isEqualTo(2);
        assertThat(templateRepo.findMaxActiveDisplayOrder()).isEqualTo(2);
        assertThat(templateRepo.lockById(first.getId())).isPresent();
        assertThat(templateRepo.lockSharedById(-1L)).isEmpty();
        assertThat(templateRepo.acquireTransactionLock(42L)).isEqualTo(1L);
    }

    @Test
    void template_maxActiveDisplayOrder_ignoresArchived_andIsZeroWhenEmpty() {
        assertThat(templateRepo.findMaxActiveDisplayOrder()).isZero();

        archivedTemplate("Stopy", CREATED_AT);

        assertThat(templateRepo.findMaxActiveDisplayOrder()).isZero();
    }

    @Test
    void templateImage_infoQueries_andImageBytes() {
        DrawingTemplate template = template("Stopy", 1);
        DrawingTemplateImage image = image(template);
        em.clear();

        assertThat(imageRepo.findInfoById(image.getId())).hasValueSatisfying(info -> {
            assertThat(info.getTemplateId()).isEqualTo(template.getId());
            assertThat(info.getWidth()).isEqualTo(1000);
            assertThat(info.getHeight()).isEqualTo(1400);
            assertThat(info.getSizeBytes()).isEqualTo(3);
        });
        assertThat(imageRepo.findInfoByIdIn(List.of(image.getId(), -1L)))
                .extracting(DrawingTemplateImageInfoProjection::getId).containsExactly(image.getId());
        assertThat(imageRepo.findImageById(image.getId()))
                .hasValueSatisfying(bytes -> assertThat(bytes).containsExactly(1, 2, 3));
        assertThat(imageRepo.findImageById(-1L)).isEmpty();
    }

    @Test
    void deletingTemplate_cascadesToAllItsImages() {
        DrawingTemplate template = template("Stopy", 1);
        DrawingTemplateImage current = image(template);
        DrawingTemplateImage previous = extraImage(template);
        em.clear();

        assertThat(executeNative("DELETE FROM drawing_template WHERE id = ?", template.getId())).isEqualTo(1);
        em.clear();

        assertThat(imageRepo.findById(current.getId())).isEmpty();
        assertThat(imageRepo.findById(previous.getId())).isEmpty();
    }

    @Test
    void deletingCurrentImage_isBlockedByTemplate() {
        DrawingTemplate template = template("Stopy", 1);
        DrawingTemplateImage image = image(template);
        em.clear();

        assertThatThrownBy(() -> executeNative("DELETE FROM drawing_template_image WHERE id = ?", image.getId()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void deleteImageById_removesUnusedImage_butImageUsedBySheetIsRestricted() {
        DrawingTemplate template = template("Stopy", 1);
        DrawingTemplateImage used = image(template);
        sheet(cardFor("Kowalski"), template, 1, DrawingSheetStatus.OPEN);
        DrawingTemplateImage unused = extraImage(template);
        DrawingTemplateImage replacement = extraImage(template);
        template.setCurrentImageId(replacement.getId());
        templateRepo.saveAndFlush(template);
        em.clear();

        assertThat(sheetRepo.existsByTemplateImageId(used.getId())).isTrue();
        assertThat(sheetRepo.existsByTemplateImageId(unused.getId())).isFalse();
        assertThat(imageRepo.deleteImageById(unused.getId())).isEqualTo(1);
        assertThat(imageRepo.findById(unused.getId())).isEmpty();
        assertThatThrownBy(() -> executeNative("DELETE FROM drawing_template_image WHERE id = ?", used.getId()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void sheet_onlyOneOpenPerCardAndTemplate_butDifferentTemplatesAllowed() {
        ClientCard card = cardFor("Kowalski");
        ClientCard other = cardFor("Nowak");
        DrawingTemplate feet = template("Stopy", 1);
        DrawingTemplate hands = template("Dłonie", 2);
        sheet(card, feet, 1, DrawingSheetStatus.CLOSED);
        sheet(card, feet, 2, DrawingSheetStatus.OPEN);
        DrawingSheet otherTemplate = sheet(card, hands, 1, DrawingSheetStatus.OPEN);
        sheet(other, feet, 1, DrawingSheetStatus.OPEN);

        assertThat(otherTemplate.getId()).isNotNull();
        assertThatThrownBy(() -> sheet(card, feet, 3, DrawingSheetStatus.OPEN))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sheet_numberUniquePerCardAndTemplate() {
        ClientCard card = cardFor("Kowalski");
        ClientCard other = cardFor("Nowak");
        DrawingTemplate feet = template("Stopy", 1);
        DrawingTemplate hands = template("Dłonie", 2);
        sheet(card, feet, 1, DrawingSheetStatus.CLOSED);
        sheet(card, hands, 1, DrawingSheetStatus.CLOSED);
        sheet(other, feet, 1, DrawingSheetStatus.CLOSED);

        assertThatThrownBy(() -> sheet(card, feet, 1, DrawingSheetStatus.OPEN))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sheet_checkConstraintRejectsUnknownStatus() {
        ClientCard card = cardFor("Kowalski");
        DrawingTemplate template = template("Stopy", 1);

        assertThatThrownBy(() -> executeNative("""
                INSERT INTO drawing_sheet (card_id, template_id, sheet_number, width, height, status, created_at)
                VALUES (?, ?, 1, 10, 10, 'DRAFT', now())
                """, card.getId(), template.getId()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void sheet_templateIsRequired_andMustExist() {
        ClientCard card = cardFor("Kowalski");

        assertThatThrownBy(() -> executeNative("""
                INSERT INTO drawing_sheet (card_id, sheet_number, width, height, status, created_at)
                VALUES (?, 1, 10, 10, 'OPEN', now())
                """, card.getId()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void sheet_templateMustExist() {
        ClientCard card = cardFor("Kowalski");

        assertThatThrownBy(() -> executeNative("""
                INSERT INTO drawing_sheet (card_id, template_id, sheet_number, width, height, status, created_at)
                VALUES (?, 999999, 1, 10, 10, 'OPEN', now())
                """, card.getId()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void sheet_templateDeletionRestricted_whileReferenced() {
        DrawingTemplate used = template("Stopy", 1);
        DrawingTemplate unused = template("Dłonie", 2);
        sheet(cardFor("Kowalski"), used, 1, DrawingSheetStatus.OPEN);
        em.clear();

        assertThat(sheetRepo.existsByTemplateId(used.getId())).isTrue();
        assertThat(sheetRepo.existsByTemplateId(unused.getId())).isFalse();
        assertThat(executeNative("DELETE FROM drawing_template WHERE id = ?", unused.getId())).isEqualTo(1);
        assertThatThrownBy(() -> executeNative("DELETE FROM drawing_template WHERE id = ?", used.getId()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void sheet_deletingClient_cascadesThroughCardToSheets() {
        ClientCard card = cardFor("Kowalski");
        Long clientId = card.getClient().getId();
        DrawingSheet sheet = sheet(card, template("Stopy", 1), 1, DrawingSheetStatus.OPEN);
        em.clear();

        clientRepo.deleteById(clientId);
        em.flush();
        em.clear();

        assertThat(clientCardRepo.existsByClientId(clientId)).isFalse();
        assertThat(sheetRepo.findById(sheet.getId())).isEmpty();
    }

    @Test
    void sheetQueries_scopeToCardAndTemplate() {
        ClientCard card = cardFor("Kowalski");
        ClientCard other = cardFor("Nowak");
        DrawingTemplate feet = template("Stopy", 1);
        DrawingTemplate hands = template("Dłonie", 2);
        DrawingSheet first = sheet(card, feet, 1, DrawingSheetStatus.CLOSED);
        DrawingSheet second = sheet(card, feet, 2, DrawingSheetStatus.OPEN);
        DrawingSheet handsSheet = sheet(card, hands, 1, DrawingSheetStatus.OPEN);
        DrawingSheet foreign = sheet(other, feet, 1, DrawingSheetStatus.OPEN);
        em.clear();

        assertThat(sheetRepo.findByCardIdOrderBySheetNumberDesc(card.getId()))
                .extracting(DrawingSheet::getId)
                .containsExactlyInAnyOrder(second.getId(), first.getId(), handsSheet.getId());
        assertThat(sheetRepo.findByCardIdOrderBySheetNumberDesc(card.getId()).get(0).getSheetNumber()).isEqualTo(2);

        assertThat(sheetRepo.lockByIdAndCardId(second.getId(), card.getId())).isPresent();
        assertThat(sheetRepo.lockByIdAndCardId(foreign.getId(), card.getId())).isEmpty();
        assertThat(sheetRepo.lockByCardIdAndTemplateIdAndStatus(card.getId(), feet.getId(), DrawingSheetStatus.OPEN))
                .hasValueSatisfying(s -> assertThat(s.getId()).isEqualTo(second.getId()));
        assertThat(sheetRepo.lockByCardIdAndTemplateIdAndStatus(card.getId(), hands.getId(), DrawingSheetStatus.OPEN))
                .hasValueSatisfying(s -> assertThat(s.getId()).isEqualTo(handsSheet.getId()));
        assertThat(sheetRepo.lockByCardIdAndTemplateIdAndStatus(other.getId(), hands.getId(), DrawingSheetStatus.OPEN))
                .isEmpty();
        assertThat(sheetRepo.findMaxSheetNumber(card.getId(), feet.getId())).isEqualTo(2);
        assertThat(sheetRepo.findMaxSheetNumber(card.getId(), hands.getId())).isEqualTo(1);
        assertThat(sheetRepo.findMaxSheetNumber(other.getId(), hands.getId())).isZero();
        assertThat(clientCardRepo.lockCard(card.getId())).isEqualTo(card.getId());
    }

    @Test
    void sheet_findByIdAndCardClientId_scopesToClient_forOpenClosedAndArchivedTemplateSheets() {
        ClientCard card = cardFor("Kowalski");
        ClientCard other = cardFor("Nowak");
        DrawingTemplate feet = template("Stopy", 1);
        DrawingTemplate heels = archivedTemplate("Pięty", CREATED_AT);
        DrawingSheet closed = sheet(card, feet, 1, DrawingSheetStatus.CLOSED);
        DrawingSheet open = sheet(card, feet, 2, DrawingSheetStatus.OPEN);
        DrawingSheet archived = sheet(card, heels, 1, DrawingSheetStatus.CLOSED);
        DrawingSheet foreign = sheet(other, feet, 1, DrawingSheetStatus.OPEN);
        em.clear();
        Long clientId = card.getClient().getId();

        assertThat(sheetRepo.findByIdAndCardClientId(open.getId(), clientId))
                .hasValueSatisfying(s -> assertThat(s.getId()).isEqualTo(open.getId()));
        assertThat(sheetRepo.findByIdAndCardClientId(closed.getId(), clientId))
                .hasValueSatisfying(s -> assertThat(s.getStatus()).isEqualTo(DrawingSheetStatus.CLOSED));
        assertThat(sheetRepo.findByIdAndCardClientId(archived.getId(), clientId))
                .hasValueSatisfying(s -> assertThat(s.getTemplateId()).isEqualTo(heels.getId()));
        assertThat(sheetRepo.findByIdAndCardClientId(foreign.getId(), clientId)).isEmpty();
        assertThat(sheetRepo.findByIdAndCardClientId(foreign.getId(), other.getClient().getId())).isPresent();
        assertThat(sheetRepo.findByIdAndCardClientId(-1L, clientId)).isEmpty();
    }

    @Test
    void clientCounts_countDistinctCardsPerTemplate() {
        ClientCard kowalski = cardFor("Kowalski");
        ClientCard nowak = cardFor("Nowak");
        DrawingTemplate feet = template("Stopy", 1);
        DrawingTemplate hands = template("Dłonie", 2);
        DrawingTemplate unused = template("Pięty", 3);
        sheet(kowalski, feet, 1, DrawingSheetStatus.CLOSED);
        sheet(kowalski, feet, 2, DrawingSheetStatus.OPEN);
        sheet(nowak, feet, 1, DrawingSheetStatus.OPEN);
        sheet(nowak, hands, 1, DrawingSheetStatus.OPEN);
        em.clear();

        Map<Long, Long> counts = sheetRepo.countClientsPerTemplate().stream()
                .collect(Collectors.toMap(DrawingTemplateUsageProjection::getTemplateId,
                        DrawingTemplateUsageProjection::getClientCount));

        assertThat(counts).containsOnly(Map.entry(feet.getId(), 2L), Map.entry(hands.getId(), 1L));
        assertThat(sheetRepo.countClientsByTemplateId(feet.getId())).isEqualTo(2L);
        assertThat(sheetRepo.countClientsByTemplateId(unused.getId())).isZero();
    }

    @Test
    void newSheet_defaultsToOpen_andKeepsTemplateImage() {
        DrawingTemplate template = template("Stopy", 1);
        DrawingTemplateImage image = image(template);
        DrawingSheet sheet = sheet(cardFor("Kowalski"), template, 1, DrawingSheetStatus.OPEN);
        em.clear();

        assertThat(sheetRepo.findById(sheet.getId())).hasValueSatisfying(s -> {
            assertThat(s.getStatus()).isEqualTo(DrawingSheetStatus.OPEN);
            assertThat(s.getClosedAt()).isNull();
            assertThat(s.getTemplateId()).isEqualTo(template.getId());
            assertThat(s.getTemplateImageId()).isEqualTo(image.getId());
        });
    }

    @Test
    void layer_uniquePerSheetAndAuthor() {
        ClientCard card = cardFor("Kowalski");
        DrawingTemplate template = template("Stopy", 1);
        DrawingSheet first = sheet(card, template, 1, DrawingSheetStatus.CLOSED);
        DrawingSheet second = sheet(card, template, 2, DrawingSheetStatus.OPEN);
        layer(first, 7L, CREATED_AT);
        layer(first, 8L, CREATED_AT);
        layer(second, 7L, CREATED_AT);

        assertThatThrownBy(() -> layer(first, 7L, CREATED_AT.plusMinutes(1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void layer_defaultsVersionOne_andRequiresFile() {
        DrawingSheet sheet = sheet(cardFor("Kowalski"), template("Stopy", 1), 1, DrawingSheetStatus.OPEN);
        DrawingLayer layer = layer(sheet, 7L, CREATED_AT);
        em.clear();

        assertThat(layerRepo.findById(layer.getId())).hasValueSatisfying(l -> assertThat(l.getVersion()).isEqualTo(1L));
        assertThatThrownBy(() -> executeNative("""
                INSERT INTO drawing_layer (sheet_id, author_user_id, size_bytes, content_sha256, created_at, updated_at, updated_by_user_id)
                VALUES (?, 8, 1, 'x', now(), now(), 8)
                """, sheet.getId()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void deletingSheet_cascadesToLayers() {
        DrawingSheet sheet = sheet(cardFor("Kowalski"), template("Stopy", 1), 1, DrawingSheetStatus.OPEN);
        DrawingLayer layer = layer(sheet, 7L, CREATED_AT);
        em.clear();

        assertThat(executeNative("DELETE FROM drawing_sheet WHERE id = ?", sheet.getId())).isEqualTo(1);
        em.clear();

        assertThat(layerRepo.findById(layer.getId())).isEmpty();
    }

    @Test
    void deletingClient_cascadesThroughCardAndSheetToLayers() {
        ClientCard card = cardFor("Kowalski");
        Long clientId = card.getClient().getId();
        DrawingLayer layer = layer(sheet(card, template("Stopy", 1), 1, DrawingSheetStatus.OPEN), 7L, CREATED_AT);
        em.clear();

        clientRepo.deleteById(clientId);
        em.flush();
        em.clear();

        assertThat(layerRepo.findById(layer.getId())).isEmpty();
        assertThat(layerRepo.existsBySheetCardClientId(clientId)).isFalse();
    }

    @Test
    void layerQueries_scopeToSheetAndClient_andOrderByCreation() {
        ClientCard card = cardFor("Kowalski");
        ClientCard other = cardFor("Nowak");
        DrawingTemplate template = template("Stopy", 1);
        DrawingSheet first = sheet(card, template, 1, DrawingSheetStatus.CLOSED);
        DrawingSheet second = sheet(card, template, 2, DrawingSheetStatus.OPEN);
        DrawingSheet blank = sheet(other, template, 1, DrawingSheetStatus.OPEN);
        DrawingLayer late = layer(second, 8L, CREATED_AT.plusHours(2));
        DrawingLayer early = layer(second, 7L, CREATED_AT.plusHours(1));
        DrawingLayer old = layer(first, 7L, CREATED_AT);
        em.clear();

        assertThat(layerRepo.findBySheetIdInOrderByCreatedAtAscIdAsc(List.of(first.getId(), second.getId())))
                .extracting(DrawingLayer::getId)
                .containsExactly(old.getId(), early.getId(), late.getId());
        assertThat(layerRepo.findBySheetIdAndAuthorUserId(second.getId(), 8L))
                .hasValueSatisfying(l -> assertThat(l.getId()).isEqualTo(late.getId()));
        assertThat(layerRepo.findBySheetIdAndAuthorUserId(blank.getId(), 8L)).isEmpty();

        Long clientId = card.getClient().getId();
        assertThat(layerRepo.findByIdAndSheetIdAndSheetCardClientId(early.getId(), second.getId(), clientId)).isPresent();
        assertThat(layerRepo.findByIdAndSheetIdAndSheetCardClientId(early.getId(), first.getId(), clientId)).isEmpty();
        assertThat(layerRepo.findByIdAndSheetIdAndSheetCardClientId(early.getId(), second.getId(),
                other.getClient().getId())).isEmpty();

        assertThat(layerRepo.existsBySheetId(second.getId())).isTrue();
        assertThat(layerRepo.existsBySheetId(blank.getId())).isFalse();
        assertThat(layerRepo.existsBySheetCardClientId(clientId)).isTrue();
        assertThat(layerRepo.existsBySheetCardClientId(other.getClient().getId())).isFalse();
    }
}

package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientCard;
import com.clinic.clinicmanager.model.QuestionnaireAnswer;
import com.clinic.clinicmanager.model.QuestionnaireField;
import com.clinic.clinicmanager.model.constants.QuestionnaireFieldType;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import com.clinic.clinicmanager.utils.CardEncryption;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(CardEncryption.class)
class QuestionnaireAnswerRepoTest extends AbstractPostgresTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private QuestionnaireAnswerRepo answerRepo;

    @Autowired
    private QuestionnaireFieldRepo fieldRepo;

    @Autowired
    private ClientCardRepo clientCardRepo;

    @Autowired
    private ClientRepo clientRepo;

    @Autowired
    private CardEncryption cardEncryption;

    private ClientCard cardFor(String lastName) {
        Client client = em.persistAndFlush(Client.builder().firstName("Jan").lastName(lastName).build());
        clientCardRepo.insertIfAbsent(client.getId(), LocalDateTime.now(), 1L);
        return clientCardRepo.findByClientId(client.getId()).orElseThrow();
    }

    private QuestionnaireField field(String label, QuestionnaireFieldType type) {
        LocalDateTime now = LocalDateTime.now();
        return em.persistAndFlush(QuestionnaireField.builder()
                .label(label).type(type).displayOrder(0).archived(false)
                .createdAt(now).updatedAt(now)
                .build());
    }

    private QuestionnaireAnswer answer(ClientCard card, QuestionnaireField field, String value) {
        return answerRepo.saveAndFlush(QuestionnaireAnswer.builder().card(card).field(field).value(value).build());
    }

    private String rawValue(Long answerId) {
        return (String) em.getEntityManager()
                .createNativeQuery("SELECT value_enc FROM questionnaire_answer WHERE id = :id")
                .setParameter("id", answerId)
                .getSingleResult();
    }

    private long countAnswers(Long cardId) {
        return ((Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM questionnaire_answer WHERE card_id = :cardId")
                .setParameter("cardId", cardId)
                .getSingleResult()).longValue();
    }

    @Test
    void value_isStoredEncrypted_andReadAsPlaintext() {
        ClientCard card = cardFor("Kowalski");
        QuestionnaireField drugs = field("Leki", QuestionnaireFieldType.TEXT);
        QuestionnaireAnswer saved = answer(card, drugs, "Metformina 500 mg");
        em.clear();

        String raw = rawValue(saved.getId());
        assertThat(raw).startsWith("v1:").doesNotContain("Metformina");
        assertThat(cardEncryption.decrypt(raw)).isEqualTo("Metformina 500 mg");

        assertThat(answerRepo.findByCardId(card.getId()))
                .singleElement()
                .satisfies(answer -> {
                    assertThat(answer.getValue()).isEqualTo("Metformina 500 mg");
                    assertThat(answer.getField().getId()).isEqualTo(drugs.getId());
                });
    }

    @Test
    void unchangedValue_isNotReEncryptedOnFlush() {
        ClientCard card = cardFor("Nowak");
        QuestionnaireAnswer saved = answer(card, field("Cukrzyca", QuestionnaireFieldType.CHECKBOX), "true");
        em.clear();
        String rawBefore = rawValue(saved.getId());

        answerRepo.findByCardId(card.getId());
        em.flush();
        em.clear();

        assertThat(rawValue(saved.getId())).isEqualTo(rawBefore);
    }

    @Test
    void deletingFieldWithAnswers_isRestricted() {
        ClientCard card = cardFor("Wiśniewski");
        QuestionnaireField drugs = field("Leki", QuestionnaireFieldType.TEXT);
        answer(card, drugs, "Insulina");
        em.clear();

        assertThatThrownBy(() -> {
            fieldRepo.deleteById(drugs.getId());
            fieldRepo.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameFieldTwiceOnCard_violatesUniqueConstraint() {
        ClientCard card = cardFor("Zieliński");
        QuestionnaireField drugs = field("Leki", QuestionnaireFieldType.TEXT);
        answer(card, drugs, "A");

        assertThatThrownBy(() -> answer(card, drugs, "B"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingClient_cascadesThroughCardToAnswers() {
        ClientCard card = cardFor("Lewandowski");
        answer(card, field("Leki", QuestionnaireFieldType.TEXT), "Insulina");
        Long clientId = card.getClient().getId();
        em.clear();

        clientRepo.deleteById(clientId);
        em.flush();
        em.clear();

        assertThat(clientCardRepo.existsByClientId(clientId)).isFalse();
        assertThat(countAnswers(card.getId())).isZero();
    }

    @Test
    void existsByCardClientId_andFieldIdsWithAnswers() {
        ClientCard withAnswer = cardFor("Kamiński");
        ClientCard empty = cardFor("Wójcik");
        QuestionnaireField answered = field("Cukrzyca", QuestionnaireFieldType.CHECKBOX);
        QuestionnaireField unanswered = field("Leki", QuestionnaireFieldType.TEXT);
        answer(withAnswer, answered, "true");

        assertThat(answerRepo.existsByCardClientId(withAnswer.getClient().getId())).isTrue();
        assertThat(answerRepo.existsByCardClientId(empty.getClient().getId())).isFalse();
        assertThat(answerRepo.findFieldIdsWithAnswers())
                .contains(answered.getId())
                .doesNotContain(unanswered.getId());
    }

    @Test
    void fieldRepo_ordersActiveFirst_thenByDisplayOrder() {
        LocalDateTime now = LocalDateTime.now();
        QuestionnaireField archived = em.persist(QuestionnaireField.builder().label("Z").type(QuestionnaireFieldType.TEXT)
                .displayOrder(0).archived(true).createdAt(now).updatedAt(now).build());
        QuestionnaireField second = em.persist(QuestionnaireField.builder().label("B").type(QuestionnaireFieldType.TEXT)
                .displayOrder(1).archived(false).createdAt(now).updatedAt(now).build());
        QuestionnaireField first = em.persist(QuestionnaireField.builder().label("A").type(QuestionnaireFieldType.TEXT)
                .displayOrder(0).archived(false).createdAt(now).updatedAt(now).build());
        em.flush();

        assertThat(fieldRepo.findAllByOrderByArchivedAscDisplayOrderAscIdAsc())
                .extracting(QuestionnaireField::getId)
                .containsSubsequence(first.getId(), second.getId(), archived.getId());
    }

    @Test
    void sectionType_andLabelEn_arePersisted() {
        LocalDateTime now = LocalDateTime.now();
        QuestionnaireField section = em.persistAndFlush(QuestionnaireField.builder()
                .label("Choroby").labelEn("Diseases").type(QuestionnaireFieldType.SECTION)
                .displayOrder(0).archived(false).createdAt(now).updatedAt(now)
                .build());
        em.clear();

        QuestionnaireField loaded = fieldRepo.findById(section.getId()).orElseThrow();
        assertThat(loaded.getType()).isEqualTo(QuestionnaireFieldType.SECTION);
        assertThat(loaded.getLabelEn()).isEqualTo("Diseases");
    }

    @Test
    void typeCheckConstraint_rejectsUnknownType() {
        assertThatThrownBy(() -> em.getEntityManager()
                .createNativeQuery("INSERT INTO questionnaire_field (label, type, display_order, archived, created_at, updated_at) "
                        + "VALUES ('X', 'RADIO', 0, false, now(), now())")
                .executeUpdate())
                .isInstanceOf(PersistenceException.class)
                .hasStackTraceContaining("ck_questionnaire_field_type");
    }
}

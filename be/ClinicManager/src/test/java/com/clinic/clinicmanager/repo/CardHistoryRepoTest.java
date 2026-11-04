package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.CardAccessLog;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientCard;
import com.clinic.clinicmanager.model.ClientCardDataRevision;
import com.clinic.clinicmanager.model.ClientQuestionnaireRevision;
import com.clinic.clinicmanager.model.TherapyEntry;
import com.clinic.clinicmanager.model.TherapyEntryRevision;
import com.clinic.clinicmanager.model.constants.CardAccessAction;
import com.clinic.clinicmanager.repo.projection.CardAccessLogResourceProjection;
import com.clinic.clinicmanager.repo.projection.TherapyEntryDateProjection;
import com.clinic.clinicmanager.repo.projection.TherapyEntryRevisionRefProjection;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import com.clinic.clinicmanager.utils.CardEncryption;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(CardEncryption.class)
class CardHistoryRepoTest extends AbstractPostgresTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 10, 0);
    private static final Set<CardAccessAction> EXCLUDED = Set.of(CardAccessAction.VIEW_QUESTIONNAIRE,
            CardAccessAction.VIEW_DRAWING, CardAccessAction.VIEW_DRAWING_SHEET, CardAccessAction.CREATE_DRAWING_SHEET,
            CardAccessAction.VIEW_THERAPY_ENTRIES, CardAccessAction.VIEW_THERAPY_ENTRY_HISTORY);

    @Autowired private TestEntityManager em;
    @Autowired private CardAccessLogRepo cardAccessLogRepo;
    @Autowired private ClientQuestionnaireRevisionRepo questionnaireRevisionRepo;
    @Autowired private ClientCardDataRevisionRepo dataRevisionRepo;
    @Autowired private TherapyEntryRepo entryRepo;
    @Autowired private TherapyEntryRevisionRepo entryRevisionRepo;
    @Autowired private ClientCardRepo clientCardRepo;
    @Autowired private CardEncryption cardEncryption;

    private Client client(String lastName) {
        return em.persistAndFlush(Client.builder().firstName("Jan").lastName(lastName).build());
    }

    private CardAccessLog log(Long clientId, CardAccessAction action, LocalDateTime at) {
        return cardAccessLogRepo.saveAndFlush(CardAccessLog.builder()
                .at(at).userId(1L).action(action).clientId(clientId)
                .build());
    }

    private String rawColumn(String sql, Long id) {
        return (String) em.getEntityManager().createNativeQuery(sql).setParameter("id", id).getSingleResult();
    }

    @Test
    void findHistory_excludesViewActionsExceptViewCard_sortsByAtDescIdDesc_andCounts() {
        Long clientId = client("Kowalski").getId();
        Long otherClientId = client("Nowak").getId();
        CardAccessLog oldest = log(clientId, CardAccessAction.VIEW_CARD, NOW.minusDays(2));
        CardAccessLog sameTimeFirst = log(clientId, CardAccessAction.UPDATE_QUESTIONNAIRE, NOW);
        CardAccessLog sameTimeSecond = log(clientId, CardAccessAction.UPDATE_DRAWING, NOW);
        CardAccessLog middle = log(clientId, CardAccessAction.CREATE_THERAPY_ENTRY, NOW.minusDays(1));
        log(clientId, CardAccessAction.VIEW_DRAWING, NOW.plusMinutes(1));
        log(clientId, CardAccessAction.VIEW_DRAWING_SHEET, NOW.plusMinutes(2));
        log(clientId, CardAccessAction.CREATE_DRAWING_SHEET, NOW.plusMinutes(3));
        log(clientId, CardAccessAction.VIEW_QUESTIONNAIRE, NOW.plusMinutes(5));
        log(clientId, CardAccessAction.VIEW_THERAPY_ENTRIES, NOW.plusMinutes(6));
        log(clientId, CardAccessAction.VIEW_THERAPY_ENTRY_HISTORY, NOW.plusMinutes(7));
        log(otherClientId, CardAccessAction.VIEW_CARD, NOW.plusMinutes(4));

        Page<CardAccessLog> firstPage = cardAccessLogRepo.findHistory(clientId, EXCLUDED, PageRequest.of(0, 3));
        Page<CardAccessLog> secondPage = cardAccessLogRepo.findHistory(clientId, EXCLUDED, PageRequest.of(1, 3));

        assertThat(firstPage.getContent()).extracting(CardAccessLog::getId)
                .containsExactly(sameTimeSecond.getId(), sameTimeFirst.getId(), middle.getId());
        assertThat(secondPage.getContent()).extracting(CardAccessLog::getId).containsExactly(oldest.getId());
        assertThat(firstPage.getTotalElements()).isEqualTo(4);
    }

    @Test
    void questionnaireRevision_changesStoredEncrypted_andFilteredByClient() {
        Client client = client("Wiśniewski");
        Client other = client("Inny");
        ClientQuestionnaireRevision revision = questionnaireRevisionRepo.saveAndFlush(ClientQuestionnaireRevision.builder()
                .client(client).version(4L).changedAt(NOW).changedByUserId(7L)
                .changes("[{\"label\":\"Cukrzyca\"}]")
                .build());
        ClientQuestionnaireRevision otherRevision = questionnaireRevisionRepo.saveAndFlush(ClientQuestionnaireRevision.builder()
                .client(other).version(1L).changedAt(NOW).changedByUserId(null)
                .changes("[]")
                .build());
        em.clear();

        String raw = rawColumn("SELECT changes_enc FROM client_questionnaire_revision WHERE id = :id", revision.getId());
        assertThat(raw).startsWith("v1:").doesNotContain("Cukrzyca");
        assertThat(cardEncryption.decrypt(raw)).isEqualTo("[{\"label\":\"Cukrzyca\"}]");
        assertThat(questionnaireRevisionRepo.findByClientIdAndIdIn(client.getId(),
                List.of(revision.getId(), otherRevision.getId())))
                .extracting(ClientQuestionnaireRevision::getId, ClientQuestionnaireRevision::getVersion,
                        ClientQuestionnaireRevision::getChanges)
                .containsExactly(tuple(revision.getId(), 4L, "[{\"label\":\"Cukrzyca\"}]"));
    }

    @Test
    void clientDataRevision_changesStoredEncrypted_andFilteredByClient() {
        Client client = client("Zieliński");
        Client other = client("Inny");
        ClientCardDataRevision revision = dataRevisionRepo.saveAndFlush(ClientCardDataRevision.builder()
                .client(client).changedAt(NOW).changedByUserId(7L)
                .changes("[{\"field\":\"phoneNumber\",\"before\":\"600100200\",\"after\":null}]")
                .build());
        ClientCardDataRevision otherRevision = dataRevisionRepo.saveAndFlush(ClientCardDataRevision.builder()
                .client(other).changedAt(NOW).changes("[]")
                .build());
        em.clear();

        String raw = rawColumn("SELECT changes_enc FROM client_card_data_revision WHERE id = :id", revision.getId());
        assertThat(raw).startsWith("v1:").doesNotContain("600100200");
        assertThat(dataRevisionRepo.findByClientIdAndIdIn(client.getId(), List.of(revision.getId(), otherRevision.getId())))
                .extracting(ClientCardDataRevision::getId)
                .containsExactly(revision.getId());
    }

    @Test
    void findEntryDatesByClientIdAndIdIn_returnsDatesOnlyForClientEntries() {
        Client client = client("Kamiński");
        Client other = client("Inny");
        clientCardRepo.insertIfAbsent(client.getId(), NOW, 1L);
        clientCardRepo.insertIfAbsent(other.getId(), NOW, 1L);
        ClientCard card = clientCardRepo.findByClientId(client.getId()).orElseThrow();
        ClientCard otherCard = clientCardRepo.findByClientId(other.getId()).orElseThrow();
        TherapyEntry entry = entryRepo.saveAndFlush(TherapyEntry.builder()
                .card(card).entryDate(LocalDate.of(2026, 9, 15)).content("Treść").authorUserId(1L).createdAt(NOW)
                .build());
        TherapyEntry otherEntry = entryRepo.saveAndFlush(TherapyEntry.builder()
                .card(otherCard).entryDate(LocalDate.of(2026, 9, 1)).content("Inna").authorUserId(1L).createdAt(NOW)
                .build());
        em.clear();

        assertThat(entryRepo.findEntryDatesByClientIdAndIdIn(client.getId(),
                List.of(entry.getId(), otherEntry.getId(), -1L)))
                .extracting(TherapyEntryDateProjection::getId, TherapyEntryDateProjection::getEntryDate)
                .containsExactly(tuple(entry.getId(), LocalDate.of(2026, 9, 15)));
    }

    private TherapyEntry entry(ClientCard card, LocalDate date, String content) {
        return entryRepo.saveAndFlush(TherapyEntry.builder()
                .card(card).entryDate(date).content(content).authorUserId(1L).createdAt(NOW)
                .build());
    }

    private TherapyEntryRevision revision(TherapyEntry entry, LocalDate previousDate, String previousContent,
                                          LocalDateTime changedAt) {
        return entryRevisionRepo.saveAndFlush(TherapyEntryRevision.builder()
                .entry(entry).previousEntryDate(previousDate).previousContent(previousContent)
                .changedAt(changedAt).changedByUserId(1L)
                .build());
    }

    private ClientCard cardFor(Client client) {
        clientCardRepo.insertIfAbsent(client.getId(), NOW, 1L);
        return clientCardRepo.findByClientId(client.getId()).orElseThrow();
    }

    @Test
    void findOldestByClientIdAndEntryIdIn_returnsFirstRevisionPerEntry_onlyForClientEntries() {
        Client client = client("Lewandowski");
        Client other = client("Inny");
        ClientCard card = cardFor(client);
        ClientCard otherCard = cardFor(other);
        TherapyEntry edited = entry(card, LocalDate.of(2026, 9, 20), "Treść po edycjach");
        TherapyEntry tied = entry(card, LocalDate.of(2026, 9, 21), "Treść po edycji");
        TherapyEntry unedited = entry(card, LocalDate.of(2026, 9, 22), "Bez edycji");
        TherapyEntry otherEntry = entry(otherCard, LocalDate.of(2026, 9, 23), "Inna");
        revision(edited, LocalDate.of(2026, 9, 18), "Druga wersja", NOW.plusHours(2));
        revision(edited, LocalDate.of(2026, 9, 15), "Oryginał", NOW.plusHours(1));
        revision(edited, LocalDate.of(2026, 9, 19), "Trzecia wersja", NOW.plusHours(3));
        revision(tied, LocalDate.of(2026, 9, 11), "Pierwsza przy remisie", NOW.plusHours(1));
        revision(tied, LocalDate.of(2026, 9, 12), "Druga przy remisie", NOW.plusHours(1));
        revision(otherEntry, LocalDate.of(2026, 9, 1), "Obca", NOW.plusHours(1));
        em.clear();

        assertThat(entryRevisionRepo.findOldestByClientIdAndEntryIdIn(client.getId(),
                List.of(edited.getId(), tied.getId(), unedited.getId(), otherEntry.getId())))
                .extracting(revision -> revision.getEntry().getId(), TherapyEntryRevision::getPreviousEntryDate,
                        TherapyEntryRevision::getPreviousContent)
                .containsExactlyInAnyOrder(
                        tuple(edited.getId(), LocalDate.of(2026, 9, 15), "Oryginał"),
                        tuple(tied.getId(), LocalDate.of(2026, 9, 11), "Pierwsza przy remisie"));
    }

    @Test
    void findByCardClientIdAndIdIn_returnsDecryptedEntriesOnlyForClient() {
        Client client = client("Dąbrowski");
        Client other = client("Inny");
        TherapyEntry entry = entry(cardFor(client), LocalDate.of(2026, 9, 15), "Treść wpisu");
        TherapyEntry otherEntry = entry(cardFor(other), LocalDate.of(2026, 9, 1), "Inna");
        em.clear();

        assertThat(entryRepo.findByCardClientIdAndIdIn(client.getId(), List.of(entry.getId(), otherEntry.getId(), -1L)))
                .extracting(TherapyEntry::getId, TherapyEntry::getEntryDate, TherapyEntry::getContent)
                .containsExactly(tuple(entry.getId(), LocalDate.of(2026, 9, 15), "Treść wpisu"));
    }

    @Test
    void entryRevisionRefs_filteredByClient_andOrderedByChangedAtThenId() {
        Client client = client("Wójcik");
        Client other = client("Inny");
        TherapyEntry entry = entry(cardFor(client), LocalDate.of(2026, 9, 20), "Bieżąca");
        TherapyEntry otherEntry = entry(cardFor(other), LocalDate.of(2026, 9, 21), "Inna");
        TherapyEntryRevision later = revision(entry, LocalDate.of(2026, 9, 18), "Druga", NOW.plusHours(2));
        TherapyEntryRevision tiedFirst = revision(entry, LocalDate.of(2026, 9, 15), "Pierwsza", NOW.plusHours(1));
        TherapyEntryRevision tiedSecond = revision(entry, LocalDate.of(2026, 9, 16), "Pierwsza bis", NOW.plusHours(1));
        TherapyEntryRevision foreign = revision(otherEntry, LocalDate.of(2026, 9, 1), "Obca", NOW);
        em.clear();

        assertThat(entryRevisionRepo.findRefsByClientIdAndIdIn(client.getId(),
                List.of(later.getId(), foreign.getId(), -1L)))
                .extracting(TherapyEntryRevisionRefProjection::getId, TherapyEntryRevisionRefProjection::getEntryId)
                .containsExactly(tuple(later.getId(), entry.getId()));
        assertThat(entryRevisionRepo.findOrderedRefsByClientIdAndEntryIdIn(client.getId(),
                List.of(entry.getId(), otherEntry.getId())))
                .extracting(TherapyEntryRevisionRefProjection::getId)
                .containsExactly(tiedFirst.getId(), tiedSecond.getId(), later.getId());
    }

    @Test
    void findByEntryCardClientIdAndIdIn_returnsDecryptedRevisionsOnlyForClient() {
        Client client = client("Kowalczyk");
        Client other = client("Inny");
        TherapyEntry entry = entry(cardFor(client), LocalDate.of(2026, 9, 20), "Bieżąca");
        TherapyEntry otherEntry = entry(cardFor(other), LocalDate.of(2026, 9, 21), "Inna");
        TherapyEntryRevision revision = revision(entry, LocalDate.of(2026, 9, 18), "Treść przed edycją", NOW);
        TherapyEntryRevision foreign = revision(otherEntry, LocalDate.of(2026, 9, 1), "Obca", NOW);
        em.clear();

        String raw = rawColumn("SELECT previous_content_enc FROM therapy_entry_revision WHERE id = :id",
                revision.getId());
        assertThat(raw).doesNotContain("Treść przed edycją");
        assertThat(entryRevisionRepo.findByEntryCardClientIdAndIdIn(client.getId(),
                List.of(revision.getId(), foreign.getId())))
                .extracting(TherapyEntryRevision::getId, TherapyEntryRevision::getPreviousEntryDate,
                        TherapyEntryRevision::getPreviousContent)
                .containsExactly(tuple(revision.getId(), LocalDate.of(2026, 9, 18), "Treść przed edycją"));
    }

    @Test
    void findOrderedResourceRefs_filtersByClientActionTypeAndIds_andSortsByAtThenId() {
        Long clientId = client("Mazur").getId();
        Long otherClientId = client("Inny").getId();
        CardAccessLog later = saveLog(clientId, CardAccessAction.UPDATE_THERAPY_ENTRY, "THERAPY_ENTRY", 20L,
                NOW.plusMinutes(5));
        CardAccessLog tiedFirst = saveLog(clientId, CardAccessAction.UPDATE_THERAPY_ENTRY, "THERAPY_ENTRY", 20L, NOW);
        CardAccessLog tiedSecond = saveLog(clientId, CardAccessAction.UPDATE_THERAPY_ENTRY, "THERAPY_ENTRY", 21L, NOW);
        saveLog(clientId, CardAccessAction.UPDATE_THERAPY_ENTRY, "THERAPY_ENTRY_REVISION", 20L, NOW);
        saveLog(clientId, CardAccessAction.INVALIDATE_THERAPY_ENTRY, "THERAPY_ENTRY", 20L, NOW);
        saveLog(clientId, CardAccessAction.UPDATE_THERAPY_ENTRY, "THERAPY_ENTRY", 22L, NOW);
        saveLog(otherClientId, CardAccessAction.UPDATE_THERAPY_ENTRY, "THERAPY_ENTRY", 20L, NOW);

        assertThat(cardAccessLogRepo.findOrderedResourceRefs(clientId, CardAccessAction.UPDATE_THERAPY_ENTRY,
                "THERAPY_ENTRY", List.of(20L, 21L)))
                .extracting(CardAccessLogResourceProjection::getId, CardAccessLogResourceProjection::getResourceId)
                .containsExactly(
                        tuple(tiedFirst.getId(), 20L),
                        tuple(tiedSecond.getId(), 21L),
                        tuple(later.getId(), 20L));
    }

    private CardAccessLog saveLog(Long clientId, CardAccessAction action, String resourceType, Long resourceId,
                                  LocalDateTime at) {
        return cardAccessLogRepo.saveAndFlush(CardAccessLog.builder()
                .at(at).userId(1L).action(action).clientId(clientId)
                .resourceType(resourceType).resourceId(resourceId)
                .build());
    }
}

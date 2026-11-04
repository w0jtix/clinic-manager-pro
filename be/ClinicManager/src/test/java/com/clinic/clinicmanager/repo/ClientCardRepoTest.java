package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientCard;
import com.clinic.clinicmanager.repo.projection.ClientCardCreatedAtProjection;
import com.clinic.clinicmanager.repo.projection.QuestionnaireStateProjection;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ClientCardRepoTest extends AbstractPostgresTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ClientCardRepo clientCardRepo;

    @Autowired
    private ClientRepo clientRepo;

    private Client client() {
        return em.persistAndFlush(Client.builder().firstName("Jan").lastName("Kowalski").build());
    }

    @Test
    void insertIfAbsent_createsCardOnce() {
        Client jan = client();
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 1, 10, 0);

        int first = clientCardRepo.insertIfAbsent(jan.getId(), createdAt, 7L);
        int second = clientCardRepo.insertIfAbsent(jan.getId(), createdAt.plusDays(1), 8L);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        ClientCard card = clientCardRepo.findByClientId(jan.getId()).orElseThrow();
        assertThat(card.getCreatedAt()).isEqualTo(createdAt);
        assertThat(card.getCreatedByUserId()).isEqualTo(7L);
        assertThat(card.getVersion()).isZero();
        assertThat(clientCardRepo.existsByClientId(jan.getId())).isTrue();
    }

    @Test
    void findCreatedAtByClientIdIn_returnsCardsOfGivenClientsOnly() {
        Client jan = client();
        Client ewa = em.persistAndFlush(Client.builder().firstName("Ewa").lastName("Nowak").build());
        Client noCard = em.persistAndFlush(Client.builder().firstName("Ola").lastName("Lis").build());
        Client other = em.persistAndFlush(Client.builder().firstName("Adam").lastName("Obcy").build());
        LocalDateTime janCreated = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime ewaCreated = LocalDateTime.of(2026, 9, 5, 18, 30);
        clientCardRepo.insertIfAbsent(jan.getId(), janCreated, 1L);
        clientCardRepo.insertIfAbsent(ewa.getId(), ewaCreated, 1L);
        clientCardRepo.insertIfAbsent(other.getId(), janCreated, 1L);
        Long janCardId = clientCardRepo.findByClientId(jan.getId()).orElseThrow().getId();
        Long ewaCardId = clientCardRepo.findByClientId(ewa.getId()).orElseThrow().getId();

        assertThat(clientCardRepo.findCreatedAtByClientIdIn(List.of(jan.getId(), ewa.getId(), noCard.getId())))
                .extracting(ClientCardCreatedAtProjection::getId, ClientCardCreatedAtProjection::getClientId,
                        ClientCardCreatedAtProjection::getCreatedAt)
                .containsExactlyInAnyOrder(
                        tuple(janCardId, jan.getId(), janCreated),
                        tuple(ewaCardId, ewa.getId(), ewaCreated));
    }

    @Test
    void insertIfAbsent_acceptsMissingUser() {
        Client jan = client();

        clientCardRepo.insertIfAbsent(jan.getId(), LocalDateTime.now(), null);

        assertThat(clientCardRepo.findByClientId(jan.getId()))
                .hasValueSatisfying(card -> assertThat(card.getCreatedByUserId()).isNull());
    }

    @Test
    void deletingClient_cascadesToEmptyCard() {
        Client jan = client();
        clientCardRepo.insertIfAbsent(jan.getId(), LocalDateTime.now(), 1L);
        em.clear();

        clientRepo.deleteById(jan.getId());
        em.flush();
        em.clear();

        assertThat(clientRepo.findById(jan.getId())).isEmpty();
        assertThat(clientCardRepo.existsByClientId(jan.getId())).isFalse();
    }

    @Test
    void newCard_startsWithEmptyQuestionnaireState() {
        Client jan = client();
        clientCardRepo.insertIfAbsent(jan.getId(), LocalDateTime.now(), 1L);
        ClientCard card = clientCardRepo.findByClientId(jan.getId()).orElseThrow();

        QuestionnaireStateProjection state = clientCardRepo.lockQuestionnaireState(card.getId());

        assertThat(card.getQuestionnaireVersion()).isZero();
        assertThat(card.getQuestionnaireUpdatedAt()).isNull();
        assertThat(state.getVersion()).isZero();
        assertThat(state.getUpdatedAt()).isNull();
    }

    @Test
    void bumpQuestionnaireVersion_incrementsVersion_andStoresAuthor() {
        Client jan = client();
        clientCardRepo.insertIfAbsent(jan.getId(), LocalDateTime.now(), 1L);
        Long cardId = clientCardRepo.findByClientId(jan.getId()).orElseThrow().getId();
        LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 2, 10, 15);

        clientCardRepo.bumpQuestionnaireVersion(cardId, updatedAt, 7L);
        clientCardRepo.bumpQuestionnaireVersion(cardId, updatedAt.plusMinutes(1), 8L);
        em.clear();

        QuestionnaireStateProjection state = clientCardRepo.lockQuestionnaireState(cardId);
        assertThat(state.getVersion()).isEqualTo(2L);
        assertThat(state.getUpdatedAt()).isEqualTo(updatedAt.plusMinutes(1));
        ClientCard card = clientCardRepo.findById(cardId).orElseThrow();
        assertThat(card.getQuestionnaireVersion()).isEqualTo(2L);
        assertThat(card.getQuestionnaireUpdatedByUserId()).isEqualTo(8L);
        assertThat(card.getVersion()).isZero();
    }
}

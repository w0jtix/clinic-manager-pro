package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.Visit;
import com.clinic.clinicmanager.repo.projection.ClientCardListProjection;
import com.clinic.clinicmanager.repo.projection.ClientMatchProjection;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.clinic.clinicmanager.repo.ClientRepo.CARD_SEARCH_MODE_ALL;
import static com.clinic.clinicmanager.repo.ClientRepo.CARD_SEARCH_MODE_NAME;
import static com.clinic.clinicmanager.repo.ClientRepo.CARD_SEARCH_MODE_PHONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ClientCardSearchRepoTest extends AbstractPostgresTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 25);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ClientRepo clientRepo;

    private Client client(String firstName, String lastName, String phoneNumber) {
        return em.persistAndFlush(Client.builder()
                .firstName(firstName).lastName(lastName).phoneNumber(phoneNumber)
                .build());
    }

    private Client deletedClient(String firstName, String lastName, String phoneNumber) {
        return em.persistAndFlush(Client.builder()
                .firstName(firstName).lastName(lastName).phoneNumber(phoneNumber)
                .isDeleted(true)
                .build());
    }

    private List<String> searchLastNames(String mode, String keyword) {
        return clientRepo.searchCards(mode, keyword, false, FIRST_PAGE).getContent().stream()
                .map(ClientCardListProjection::getLastName)
                .toList();
    }

    @Test
    void polishIcuCollation_isAvailable() {
        Object result = em.getEntityManager()
                .createNativeQuery("SELECT 'a' COLLATE \"pl-x-icu\"")
                .getSingleResult();

        assertThat(result).isEqualTo("a");
    }

    @Test
    void searchCards_nameMode_ignoresPolishCharsAndCase() {
        client("Anna", "Cieślak", null);
        client("Łukasz", "Nowak", null);
        client("Lukasz", "Zieliński", null);

        assertThat(searchLastNames(CARD_SEARCH_MODE_NAME, "ciesl")).containsExactly("Cieślak");
        assertThat(searchLastNames(CARD_SEARCH_MODE_NAME, "CIEŚL")).containsExactly("Cieślak");
        assertThat(searchLastNames(CARD_SEARCH_MODE_NAME, "lukasz")).containsExactly("Nowak", "Zieliński");
        assertThat(searchLastNames(CARD_SEARCH_MODE_NAME, "Łukasz")).containsExactly("Nowak", "Zieliński");
        assertThat(searchLastNames(CARD_SEARCH_MODE_NAME, "zielinski")).containsExactly("Zieliński");
    }

    @Test
    void searchCards_nameMode_tokensInAnyOrderAndHyphenatedParts() {
        client("Anna", "Kowalska", null);
        client("Andrzej", "Nowak", null);
        client("Maria", "Nowak-Kowalska", null);

        assertThat(searchLastNames(CARD_SEARCH_MODE_NAME, "kow an")).containsExactly("Kowalska");
        assertThat(searchLastNames(CARD_SEARCH_MODE_NAME, "an kow")).containsExactly("Kowalska");
        assertThat(searchLastNames(CARD_SEARCH_MODE_NAME, "kowalska")).containsExactly("Kowalska", "Nowak-Kowalska");
        assertThat(searchLastNames(CARD_SEARCH_MODE_NAME, "nowak-kow")).containsExactly("Nowak-Kowalska");
        assertThat(searchLastNames(CARD_SEARCH_MODE_NAME, "owal")).isEmpty();
    }

    @Test
    void searchCards_phoneMode_normalizesSpacesDashesAndPrefix() {
        client("Jan", "Kowalski", "+48 600-100-200");
        client("Adam", "Nowak", "0048 511 222 333");
        client("Ewa", "Bez", null);

        assertThat(searchLastNames(CARD_SEARCH_MODE_PHONE, "600 100")).containsExactly("Kowalski");
        assertThat(searchLastNames(CARD_SEARCH_MODE_PHONE, "600-100-200")).containsExactly("Kowalski");
        assertThat(searchLastNames(CARD_SEARCH_MODE_PHONE, "+48600100200")).containsExactly("Kowalski");
        assertThat(searchLastNames(CARD_SEARCH_MODE_PHONE, "(511) 222")).containsExactly("Nowak");
        assertThat(searchLastNames(CARD_SEARCH_MODE_PHONE, "999")).isEmpty();
    }

    @Test
    void searchCards_allMode_sortsByPolishCollation() {
        client("Jan", "Zając", null);
        client("Jan", "Śliwa", null);
        client("Jan", "Łukasiewicz", null);
        client("Jan", "Lis", null);

        assertThat(searchLastNames(CARD_SEARCH_MODE_ALL, ""))
                .containsExactly("Lis", "Łukasiewicz", "Śliwa", "Zając");
    }

    @Test
    void searchCards_includeDeleted_controlsDeletedClients() {
        client("Jan", "Aktywny", null);
        deletedClient("Jan", "Usunięty", null);

        assertThat(clientRepo.searchCards(CARD_SEARCH_MODE_ALL, "", false, FIRST_PAGE).getContent())
                .extracting(ClientCardListProjection::getLastName)
                .containsExactly("Aktywny");
        assertThat(clientRepo.searchCards(CARD_SEARCH_MODE_ALL, "", true, FIRST_PAGE).getContent())
                .extracting(ClientCardListProjection::getLastName, ClientCardListProjection::getIsDeleted)
                .containsExactly(
                        tuple("Aktywny", false),
                        tuple("Usunięty", true));
    }

    @Test
    void searchCards_paginatesAndCounts() {
        for (int i = 0; i < 5; i++) {
            client("Jan", "Nazwisko" + i, null);
        }

        Page<ClientCardListProjection> page = clientRepo.searchCards(CARD_SEARCH_MODE_NAME, "nazw", false, PageRequest.of(1, 2));

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).extracting(ClientCardListProjection::getLastName)
                .containsExactly("Nazwisko2", "Nazwisko3");
    }

    @Test
    void searchCards_escapedWildcards_matchLiterally() {
        client("Jan", "Kowalski", null);
        client("Jan", "Ab", null);
        client("Jan", "A_b", null);

        assertThat(searchLastNames(CARD_SEARCH_MODE_NAME, "\\%")).isEmpty();
        assertThat(searchLastNames(CARD_SEARCH_MODE_NAME, "a\\_")).containsExactly("A_b");
    }

    private Employee employee() {
        return em.persistAndFlush(Employee.builder().name("Anna").lastName("Nowak").drawingColor("#2563eb").build());
    }

    private Visit visit(Client client, Employee employee, LocalDate date, boolean absence) {
        return em.persistAndFlush(Visit.builder().client(client).employee(employee)
                .date(date).absence(absence).items(new ArrayList<>()).build());
    }

    private ClientCardListProjection searchSingle(String lastName) {
        return clientRepo.searchCards(CARD_SEARCH_MODE_NAME, lastName, false, FIRST_PAGE).getContent().get(0);
    }

    @Test
    void searchCards_returnsLatestVisitIncludingAbsencesAndBirthDate() {
        Client jan = em.persistAndFlush(Client.builder()
                .firstName("Jan").lastName("Kowalski").birthDate(LocalDate.of(1980, 5, 1))
                .build());
        Employee employee = employee();
        visit(jan, employee, LocalDate.of(2026, 1, 10), false);
        visit(jan, employee, LocalDate.of(2026, 3, 5), false);
        visit(jan, employee, LocalDate.of(2026, 4, 20), true);
        client("Adam", "Nowak", null);

        List<ClientCardListProjection> rows = clientRepo.searchCards(CARD_SEARCH_MODE_ALL, "", false, FIRST_PAGE).getContent();

        assertThat(rows).extracting(ClientCardListProjection::getLastName).containsExactly("Kowalski", "Nowak");
        assertThat(rows.get(0).getLastVisitDate()).isEqualTo(LocalDate.of(2026, 4, 20));
        assertThat(rows.get(0).getLastVisitAbsence()).isTrue();
        assertThat(rows.get(0).getBirthDate()).isEqualTo(LocalDate.of(1980, 5, 1));
        assertThat(rows.get(1).getLastVisitDate()).isNull();
        assertThat(rows.get(1).getLastVisitAbsence()).isNull();
    }

    @Test
    void searchCards_latestVisitIsAbsence_returnsAbsenceDateAndTrue() {
        Client jan = client("Jan", "Kowalski", null);
        Employee employee = employee();
        visit(jan, employee, LocalDate.of(2026, 10, 10), false);
        visit(jan, employee, LocalDate.of(2026, 10, 20), true);

        ClientCardListProjection row = searchSingle("kowalski");

        assertThat(row.getLastVisitDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(row.getLastVisitAbsence()).isTrue();
    }

    @Test
    void searchCards_latestVisitAttended_returnsVisitDateAndFalse() {
        Client jan = client("Jan", "Kowalski", null);
        Employee employee = employee();
        visit(jan, employee, LocalDate.of(2026, 10, 20), false);
        visit(jan, employee, LocalDate.of(2026, 10, 10), true);

        ClientCardListProjection row = searchSingle("kowalski");

        assertThat(row.getLastVisitDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(row.getLastVisitAbsence()).isFalse();
    }

    @Test
    void searchCards_clientWithoutVisits_returnsNullDateAndAbsence() {
        client("Jan", "Kowalski", null);

        ClientCardListProjection row = searchSingle("kowalski");

        assertThat(row.getLastVisitDate()).isNull();
        assertThat(row.getLastVisitAbsence()).isNull();
    }

    @Test
    void searchCards_twoVisitsSameDay_higherIdDecides() {
        Client jan = client("Jan", "Kowalski", null);
        Client adam = client("Adam", "Nowak", null);
        Employee employee = employee();
        LocalDate day = LocalDate.of(2026, 10, 20);
        visit(jan, employee, LocalDate.of(2026, 10, 1), false);
        visit(jan, employee, day, false);
        visit(jan, employee, day, true);
        visit(adam, employee, day, true);
        visit(adam, employee, day, false);

        assertThat(searchSingle("kowalski").getLastVisitDate()).isEqualTo(day);
        assertThat(searchSingle("kowalski").getLastVisitAbsence()).isTrue();
        assertThat(searchSingle("nowak").getLastVisitDate()).isEqualTo(day);
        assertThat(searchSingle("nowak").getLastVisitAbsence()).isFalse();
    }

    @Test
    void findExactMatch_lastVisitDateStillSkipsAbsences() {
        Client jan = client("Jan", "Kowalski", "600100200");
        Employee employee = employee();
        visit(jan, employee, LocalDate.of(2026, 10, 10), false);
        visit(jan, employee, LocalDate.of(2026, 10, 20), true);

        Optional<ClientMatchProjection> match = clientRepo.findExactMatch("Jan", "Kowalski", "600100200", null, false);

        assertThat(match).isPresent();
        assertThat(match.get().getLastVisitDate()).isEqualTo(LocalDate.of(2026, 10, 10));
    }

    @Test
    void findExactMatch_withPhone_normalizesNameAndPhone() {
        Client jan = client("Jan", "Kowalski", "600 100 200");

        Optional<ClientMatchProjection> match = clientRepo.findExactMatch(" jan ", "KOWALSKI", "+48 600-100-200", null, false);

        assertThat(match).isPresent();
        assertThat(match.get().getId()).isEqualTo(jan.getId());
        assertThat(match.get().getMatchReason()).isEqualTo("EXACT");
        assertThat(clientRepo.findExactMatch("Jan", "Kowalski", "600100201", null, false)).isEmpty();
    }

    @Test
    void findExactMatch_withoutPhone_treatsEmptyPhonesAsEqual() {
        Client jan = client("Jan", "Kowalski", null);
        client("Jan", "Nowak", "600100200");

        assertThat(clientRepo.findExactMatch("Jan", "Kowalski", "", null, false))
                .map(ClientMatchProjection::getId).contains(jan.getId());
        assertThat(clientRepo.findExactMatch("Jan", "Kowalski", null, null, false))
                .map(ClientMatchProjection::getId).contains(jan.getId());
        assertThat(clientRepo.findExactMatch("Jan", "Nowak", null, null, false)).isEmpty();
    }

    @Test
    void findExactMatch_includesDeletedUnlessActiveOnly() {
        Client ghost = deletedClient("Jan", "Kowalski", "600100200");

        Optional<ClientMatchProjection> match = clientRepo.findExactMatch("Jan", "Kowalski", "600100200", null, false);

        assertThat(match).isPresent();
        assertThat(match.get().getId()).isEqualTo(ghost.getId());
        assertThat(match.get().getIsDeleted()).isTrue();
        assertThat(clientRepo.findExactMatch("Jan", "Kowalski", "600100200", null, true)).isEmpty();
    }

    @Test
    void findExactMatch_respectsExcludeId() {
        Client jan = client("Jan", "Kowalski", "600100200");

        assertThat(clientRepo.findExactMatch("Jan", "Kowalski", "600100200", jan.getId(), false)).isEmpty();
    }

    @Test
    void findSimilar_returnsSameNameAndSamePhoneWithoutExactOrDeleted() {
        client("Jan", "Kowalski", "600100200");
        Client sameName = client("Jan", "Kowalski", "511222333");
        Client samePhone = client("Adam", "Nowak", "+48 600 100 200");
        deletedClient("Jan", "Kowalski", "700700700");

        List<ClientMatchProjection> similar = clientRepo.findSimilar("jan", "kowalski", "600100200", null);

        assertThat(similar).extracting(ClientMatchProjection::getId, ClientMatchProjection::getMatchReason)
                .containsExactlyInAnyOrder(
                        tuple(sameName.getId(), "SAME_NAME"),
                        tuple(samePhone.getId(), "SAME_PHONE"));
    }

    @Test
    void findSimilar_samePhoneRequiresAtLeastNineDigits() {
        client("Adam", "Nowak", "12345678");

        assertThat(clientRepo.findSimilar("Jan", "Kowalski", "12345678", null)).isEmpty();
    }

    @Test
    void findSimilar_isLimitedToFive() {
        for (int i = 0; i < 7; i++) {
            client("Jan", "Kowalski", "60010020" + i);
        }

        assertThat(clientRepo.findSimilar("Jan", "Kowalski", "", null)).hasSize(5);
    }

    @Test
    void matchesIdentity_comparesNormalizedData() {
        Client ghost = deletedClient("Łucja", "Wiśniewska", "600100200");

        assertThat(clientRepo.matchesIdentity(ghost.getId(), "lucja", "WISNIEWSKA", "+48 600 100 200")).isTrue();
        assertThat(clientRepo.matchesIdentity(ghost.getId(), "Łucja", "Wiśniewska", "600100201")).isFalse();
        assertThat(clientRepo.matchesIdentity(ghost.getId(), "Łucja", "Wiśniewska", null)).isFalse();
    }
}

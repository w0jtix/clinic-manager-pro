package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.repo.projection.TherapyEntryCardDateProjection;
import com.clinic.clinicmanager.repo.projection.VisitProductQuantityProjection;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import com.clinic.clinicmanager.utils.CardEncryption;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(CardEncryption.class)
class TherapyEntryRepoTest extends AbstractPostgresTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 15);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 15, 10, 0);

    @Autowired private TestEntityManager em;
    @Autowired private TherapyEntryRepo entryRepo;
    @Autowired private TherapyEntryRevisionRepo revisionRepo;
    @Autowired private ClientCardRepo clientCardRepo;
    @Autowired private ClientRepo clientRepo;
    @Autowired private VisitRepo visitRepo;
    @Autowired private CardEncryption cardEncryption;

    private ClientCard cardFor(String lastName) {
        Client client = em.persistAndFlush(Client.builder().firstName("Jan").lastName(lastName).build());
        clientCardRepo.insertIfAbsent(client.getId(), NOW, 1L);
        return clientCardRepo.findByClientId(client.getId()).orElseThrow();
    }

    private TherapyEntry entry(ClientCard card, LocalDate date, String content, boolean invalid) {
        return entryRepo.saveAndFlush(TherapyEntry.builder()
                .card(card).entryDate(date).content(content).authorUserId(1L).createdAt(NOW)
                .invalidatedAt(invalid ? NOW : null).invalidatedByUserId(invalid ? 1L : null)
                .build());
    }

    private String rawColumn(String sql, Long id) {
        return (String) em.getEntityManager().createNativeQuery(sql).setParameter("id", id).getSingleResult();
    }

    private long count(String sql, Long id) {
        return ((Number) em.getEntityManager().createNativeQuery(sql).setParameter("id", id)
                .getSingleResult()).longValue();
    }

    @Test
    void content_isStoredEncrypted_andReadAsPlaintext() {
        TherapyEntry saved = entry(cardFor("Kowalski"), DAY, "Wrastający paznokieć, opatrunek.", false);
        em.clear();

        String raw = rawColumn("SELECT content_enc FROM therapy_entry WHERE id = :id", saved.getId());
        assertThat(raw).startsWith("v1:").doesNotContain("paznokieć");
        assertThat(cardEncryption.decrypt(raw)).isEqualTo("Wrastający paznokieć, opatrunek.");
        assertThat(entryRepo.findById(saved.getId()).orElseThrow().getContent())
                .isEqualTo("Wrastający paznokieć, opatrunek.");
    }

    @Test
    void revisionContent_isStoredEncrypted() {
        TherapyEntry entry = entry(cardFor("Nowak"), DAY, "Nowa treść", false);
        TherapyEntryRevision revision = revisionRepo.saveAndFlush(TherapyEntryRevision.builder()
                .entry(entry).previousEntryDate(DAY.minusDays(1)).previousContent("Stara treść")
                .changedAt(NOW).changedByUserId(1L)
                .build());
        em.clear();

        String raw = rawColumn("SELECT previous_content_enc FROM therapy_entry_revision WHERE id = :id",
                revision.getId());
        assertThat(raw).startsWith("v1:").doesNotContain("Stara");
        assertThat(revisionRepo.findByEntryIdOrderByChangedAtDescIdDesc(entry.getId()))
                .extracting(TherapyEntryRevision::getPreviousContent, TherapyEntryRevision::getPreviousEntryDate)
                .containsExactly(tuple("Stara treść", DAY.minusDays(1)));
    }

    @Test
    void twoValidEntriesOnSameDay_violatePartialUniqueIndex() {
        ClientCard card = cardFor("Wiśniewski");
        entry(card, DAY, "Pierwszy", false);

        assertThatThrownBy(() -> entry(card, DAY, "Drugi", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void invalidEntries_doNotOccupyDay() {
        ClientCard card = cardFor("Zieliński");
        entry(card, DAY, "Błędny 1", true);
        entry(card, DAY, "Błędny 2", true);
        TherapyEntry valid = entry(card, DAY, "Ważny", false);
        ClientCard other = cardFor("Inny");
        entry(other, DAY, "Inny klient", false);

        assertThat(entryRepo.findValidByCardIdAndEntryDate(card.getId(), DAY))
                .hasValueSatisfying(e -> assertThat(e.getId()).isEqualTo(valid.getId()));
        assertThat(entryRepo.findByCardIdAndEntryDateIn(card.getId(), List.of(DAY))).hasSize(3);
    }

    @Test
    void findEntryDatesBefore_returnsDistinctDaysDescending_strictlyBefore_limited() {
        ClientCard card = cardFor("Kamiński");
        entry(card, DAY, "A", true);
        entry(card, DAY, "B", false);
        entry(card, DAY.minusDays(3), "C", false);
        entry(card, DAY.minusDays(7), "D", false);
        entry(card, DAY.plusDays(1), "E", false);

        assertThat(entryRepo.findEntryDatesBefore(card.getId(), DAY.plusDays(1), PageRequest.of(0, 2)))
                .containsExactly(DAY, DAY.minusDays(3));
        assertThat(entryRepo.findEntryDatesBefore(card.getId(), DAY.minusDays(3), PageRequest.of(0, 5)))
                .containsExactly(DAY.minusDays(7));
    }

    @Test
    void findValidEntryDatesByCardIdInAndDateBetween_returnsOnlyValidEntriesOfGivenCardsInRange() {
        ClientCard card = cardFor("Mazur");
        ClientCard second = cardFor("Krawczyk");
        ClientCard outside = cardFor("Obcy");
        entry(card, DAY, "Ważny", false);
        entry(card, DAY.minusDays(2), "Unieważniony", true);
        entry(card, DAY.plusDays(5), "Poza zakresem", false);
        entry(second, DAY.minusDays(1), "Błędny", true);
        entry(second, DAY.minusDays(1), "Poprawiony", false);
        entry(outside, DAY, "Inna karta", false);

        assertThat(entryRepo.findValidEntryDatesByCardIdInAndDateBetween(
                List.of(card.getId(), second.getId()), DAY.minusDays(3), DAY.plusDays(1)))
                .extracting(TherapyEntryCardDateProjection::getCardId, TherapyEntryCardDateProjection::getEntryDate)
                .containsExactlyInAnyOrder(tuple(card.getId(), DAY), tuple(second.getId(), DAY.minusDays(1)));
        assertThat(entryRepo.findValidEntryDatesByCardIdInAndDateBetween(
                List.of(card.getId()), DAY.minusDays(2), DAY.minusDays(2)))
                .isEmpty();
    }

    @Test
    void existsByCardClientId_detectsValidAndInvalidEntries() {
        ClientCard withInvalid = cardFor("Wójcik");
        ClientCard empty = cardFor("Pusty");
        entry(withInvalid, DAY, "Błędny", true);

        assertThat(entryRepo.existsByCardClientId(withInvalid.getClient().getId())).isTrue();
        assertThat(entryRepo.existsByCardClientId(empty.getClient().getId())).isFalse();
    }

    @Test
    void deletingClient_cascadesThroughCardToEntriesAndRevisions() {
        ClientCard card = cardFor("Lewandowski");
        TherapyEntry entry = entry(card, DAY, "Treść", false);
        revisionRepo.saveAndFlush(TherapyEntryRevision.builder()
                .entry(entry).previousEntryDate(DAY).previousContent("Poprzednia")
                .changedAt(NOW).changedByUserId(1L).build());
        Long clientId = card.getClient().getId();
        em.clear();

        clientRepo.deleteById(clientId);
        em.flush();
        em.clear();

        assertThat(count("SELECT COUNT(*) FROM therapy_entry WHERE card_id = :id", card.getId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM therapy_entry_revision WHERE entry_id = :id", entry.getId())).isZero();
    }

    @Test
    void visitQueries_returnDaysAndFetchSummariesInBatch() {
        Client client = em.persistAndFlush(Client.builder().firstName("Ewa").lastName("Wizytowa").build());
        Client otherClient = em.persistAndFlush(Client.builder().firstName("Ola").lastName("Obca").build());
        Employee employee = em.persistAndFlush(
                Employee.builder().name("Anna").lastName("Nowak").drawingColor("#2563eb").build());
        BaseServiceCategory category = em.persistAndFlush(
                BaseServiceCategory.builder().name("Pedicure").color("#111111").build());
        BaseService service = em.persistAndFlush(BaseService.builder().name("Pedicure")
                .price(BigDecimal.valueOf(80)).duration(60).category(category).build());
        ProductCategory productCategory = em.persistAndFlush(
                ProductCategory.builder().name("Kremy").color("#222222").build());
        Brand brand = em.persistAndFlush(Brand.builder().name("Marka").build());
        Product cream = em.persistAndFlush(Product.builder().name("Krem").category(productCategory)
                .brand(brand).supply(10).build());
        Product paste = em.persistAndFlush(Product.builder().name("Pasta").category(productCategory)
                .brand(brand).supply(10).build());

        Sale sale = em.persistAndFlush(Sale.builder().items(new ArrayList<>(List.of(
                SaleItem.builder().product(cream).name("Krem").price(BigDecimal.TEN).build(),
                SaleItem.builder().product(paste).name("Pasta").price(BigDecimal.TEN).build(),
                SaleItem.builder().product(cream).name("Krem").price(BigDecimal.TEN).build()))).build());
        Visit withSale = em.persistAndFlush(Visit.builder().client(client).employee(employee).date(DAY)
                .items(new ArrayList<>(List.of(
                        VisitItem.builder().service(service).name("Pedicure").duration(60)
                                .price(BigDecimal.valueOf(80)).finalPrice(BigDecimal.valueOf(80)).boostItem(false).build(),
                        VisitItem.builder().service(service).name("Klamra").duration(30)
                                .price(BigDecimal.valueOf(50)).finalPrice(BigDecimal.valueOf(50)).boostItem(false).build())))
                .sale(sale).build());
        Visit absence = em.persistAndFlush(Visit.builder().client(client).employee(employee).date(DAY)
                .absence(true).items(new ArrayList<>()).build());
        em.persistAndFlush(Visit.builder().client(client).employee(employee).date(DAY.minusDays(5))
                .items(new ArrayList<>()).build());
        em.persistAndFlush(Visit.builder().client(otherClient).employee(employee).date(DAY.minusDays(1))
                .items(new ArrayList<>()).build());
        em.clear();

        assertThat(visitRepo.findVisitDatesByClientIdBefore(client.getId(), DAY.plusDays(1), PageRequest.of(0, 5)))
                .containsExactly(DAY, DAY.minusDays(5));
        assertThat(visitRepo.findVisitDatesByClientIdBefore(client.getId(), DAY, PageRequest.of(0, 5)))
                .containsExactly(DAY.minusDays(5));

        List<Visit> visits = visitRepo.findWithEmployeeAndItemsByClientIdAndDateIn(client.getId(), List.of(DAY));
        assertThat(visits).extracting(Visit::getId).containsExactlyInAnyOrder(withSale.getId(), absence.getId());
        Visit loaded = visits.stream().filter(v -> v.getId().equals(withSale.getId())).findFirst().orElseThrow();
        assertThat(Hibernate.isInitialized(loaded.getEmployee())).isTrue();
        assertThat(Hibernate.isInitialized(loaded.getItems())).isTrue();
        assertThat(loaded.getItems()).extracting(VisitItem::getName).containsExactlyInAnyOrder("Pedicure", "Klamra");
        assertThat(loaded.getItems()).allSatisfy(item -> {
            assertThat(Hibernate.isInitialized(item.getService())).isTrue();
            assertThat(Hibernate.isInitialized(item.getService().getCategory())).isTrue();
            assertThat(item.getService().getCategory().getColor()).isEqualTo("#111111");
        });

        assertThat(visitRepo.findProductQuantitiesByVisitIdIn(List.of(withSale.getId(), absence.getId())))
                .extracting(VisitProductQuantityProjection::getVisitId, VisitProductQuantityProjection::getName,
                        VisitProductQuantityProjection::getQuantity, VisitProductQuantityProjection::getColor)
                .containsExactly(tuple(withSale.getId(), "Krem", 2L, "#222222"),
                        tuple(withSale.getId(), "Pasta", 1L, "#222222"));
    }
}

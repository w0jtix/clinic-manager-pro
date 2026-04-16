package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.DebtType;
import com.clinic.clinicmanager.model.constants.ReviewSource;
import com.clinic.clinicmanager.model.constants.VoucherStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ClientRepoTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ClientRepo clientRepo;

    private Client jan;
    private Client anna;

    @BeforeEach
    void setUp() {
        jan = em.persistAndFlush(Client.builder()
                .firstName("Jan").lastName("Kowalski")
                .signedRegulations(true).boostClient(false)
                .build());

        anna = em.persistAndFlush(Client.builder()
                .firstName("Anna").lastName("Nowak")
                .signedRegulations(false).boostClient(true)
                .build());
    }

    @Test
    void findAllWithFilters_allNullFilters_returnsAllActiveClients() {
        em.persistAndFlush(Client.builder()
                .firstName("Ghost").lastName("Client").isDeleted(true).build());

        List<ClientDTO> result = clientRepo.findAllWithFilters(null, null, null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ClientDTO::getFirstName)
                .containsExactlyInAnyOrder("Jan", "Anna");
    }

    @Test
    void findAllWithFilters_keywordMatchesFirstName() {
        List<ClientDTO> result = clientRepo.findAllWithFilters("Jan", null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getFirstName()).isEqualTo("Jan");
    }

    @Test
    void findAllWithFilters_keywordMatchesLastName() {
        List<ClientDTO> result = clientRepo.findAllWithFilters("Nowak", null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getLastName()).isEqualTo("Nowak");
    }

    @Test
    void findAllWithFilters_keywordMatchesMultipleClients_returnsAll() {
        List<ClientDTO> result = clientRepo.findAllWithFilters("an", null, null, null, null);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ClientDTO::getFirstName).containsExactlyInAnyOrder("Jan", "Anna");
    }


    @Test
    void findAllWithFilters_keywordCaseInsensitive_returnsMatch() {
        List<ClientDTO> result = clientRepo.findAllWithFilters("jan", null, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getFirstName()).isEqualTo("Jan");
    }

    @Test
    void findAllWithFilters_keywordNoMatch_returnsEmpty() {
        List<ClientDTO> result = clientRepo.findAllWithFilters("xyz", null, null, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void findAllWithFilters_boostClientTrue_returnsOnlyBoostClients() {
        List<ClientDTO> result = clientRepo.findAllWithFilters(null, true, null, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getFirstName()).isEqualTo("Anna");
    }

    @Test
    void findAllWithFilters_signedRegulationsTrue_returnsOnlySignedClients() {
        List<ClientDTO> result = clientRepo.findAllWithFilters(null, null, true, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getFirstName()).isEqualTo("Jan");
    }

    @Test
    void findAllWithFilters_hasDebtsTrue_returnsOnlyClientsWithDebts() {
        em.persistAndFlush(ClientDebt.builder()
                .client(jan).type(DebtType.ABSENCE_FEE).value(100.0).build());

        List<ClientDTO> result = clientRepo.findAllWithFilters(null, null, null, true, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getFirstName()).isEqualTo("Jan");
    }

    @Test
    void findAllWithFilters_hasDebtsFalse_returnsOnlyClientsWithoutDebts() {
        em.persistAndFlush(ClientDebt.builder()
                .client(jan).type(DebtType.ABSENCE_FEE).value(100.0).build());

        List<ClientDTO> result = clientRepo.findAllWithFilters(null, null, null, false, null);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getFirstName()).isEqualTo("Anna");
    }

    @Test
    void findAllWithFilters_discountId_returnsOnlyClientsWithThatDiscount() {
        Discount discount = em.persistAndFlush(Discount.builder()
                .name("VIP10").percentageValue(10).build());
        jan.setDiscount(discount);
        em.persistAndFlush(jan);

        List<ClientDTO> result = clientRepo.findAllWithFilters(null, null, null, null, discount.getId());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getFirstName()).isEqualTo("Jan");
    }

    //subqueries

    @Test
    void findAllWithFilters_hasDebts_isTrueWhenDebtExists() {
        em.persistAndFlush(ClientDebt.builder()
                .client(jan).type(DebtType.ABSENCE_FEE).value(50.0).build());

        List<ClientDTO> result = clientRepo.findAllWithFilters(null, null, null, null, null);

        ClientDTO janDTO = findByFirstName(result, "Jan");
        ClientDTO annaDTO = findByFirstName(result, "Anna");

        assertThat(janDTO.getHasDebts()).isTrue();
        assertThat(annaDTO.getHasDebts()).isFalse();
    }

    @Test
    void findAllWithFilters_hasActiveVoucher_isTrueWhenActiveVoucherExists() {
        em.persistAndFlush(Voucher.builder()
                .client(jan).value(100.0).status(VoucherStatus.ACTIVE)
                .issueDate(LocalDate.now()).expiryDate(LocalDate.now().plusMonths(6))
                .build());

        List<ClientDTO> result = clientRepo.findAllWithFilters(null, null, null, null, null);

        assertThat(findByFirstName(result, "Jan").getHasActiveVoucher()).isTrue();
        assertThat(findByFirstName(result, "Anna").getHasActiveVoucher()).isFalse();
    }

    @Test
    void findAllWithFilters_hasActiveVoucher_isFalseWhenVoucherIsUsed() {
        em.persistAndFlush(Voucher.builder()
                .client(jan).value(100.0).status(VoucherStatus.USED)
                .issueDate(LocalDate.now()).expiryDate(LocalDate.now().plusMonths(6))
                .build());

        List<ClientDTO> result = clientRepo.findAllWithFilters(null, null, null, null, null);

        assertThat(findByFirstName(result, "Jan").getHasActiveVoucher()).isFalse();
    }

    @Test
    void findAllWithFilters_hasBooksyReview_isTrueWhenBooksyReviewExists() {
        em.persistAndFlush(Review.builder()
                .client(jan).source(ReviewSource.BOOKSY).issueDate(LocalDate.now()).build());

        List<ClientDTO> result = clientRepo.findAllWithFilters(null, null, null, null, null);

        assertThat(findByFirstName(result, "Jan").getHasBooksyReview()).isTrue();
        assertThat(findByFirstName(result, "Anna").getHasBooksyReview()).isFalse();
    }

    @Test
    void findAllWithFilters_hasGoogleReview_isTrueWhenGoogleReviewExists() {
        em.persistAndFlush(Review.builder()
                .client(jan).source(ReviewSource.GOOGLE).issueDate(LocalDate.now()).build());

        List<ClientDTO> result = clientRepo.findAllWithFilters(null, null, null, null, null);

        assertThat(findByFirstName(result, "Jan").getHasGoogleReview()).isTrue();
        assertThat(findByFirstName(result, "Anna").getHasGoogleReview()).isFalse();
    }

    @Test
    void findAllWithFilters_hasActiveGoogleReview_isTrueWhenUnusedGoogleReviewExists() {
        em.persistAndFlush(Review.builder()
                .client(jan).source(ReviewSource.GOOGLE).isUsed(false).issueDate(LocalDate.now()).build());

        List<ClientDTO> result = clientRepo.findAllWithFilters(null, null, null, null, null);

        assertThat(findByFirstName(result, "Jan").getHasActiveGoogleReview()).isTrue();
    }

    @Test
    void findAllWithFilters_hasActiveGoogleReview_isFalseWhenGoogleReviewIsUsed() {
        em.persistAndFlush(Review.builder()
                .client(jan).source(ReviewSource.GOOGLE).isUsed(true).issueDate(LocalDate.now()).build());

        List<ClientDTO> result = clientRepo.findAllWithFilters(null, null, null, null, null);

        assertThat(findByFirstName(result, "Jan").getHasActiveGoogleReview()).isFalse();
    }

    @Test
    void findAllWithFilters_visitsCount_countsOnlyVisitsWithItems() {
        Employee employee = em.persistAndFlush(Employee.builder()
                .name("Test").lastName("Employee").build());
        BaseServiceCategory cat = em.persistAndFlush(BaseServiceCategory.builder()
                .name("Category").color("#FFFFFF").build());
        BaseService service = em.persistAndFlush(BaseService.builder()
                .name("Service").price(100.0).duration(30).category(cat).build());

        VisitItem item = VisitItem.builder()
                .service(service).name("Service test")
                .duration(30).price(100.0).finalPrice(100.0).boostItem(false)
                .build();
        em.persistAndFlush(Visit.builder()
                .client(jan).employee(employee).date(LocalDate.now())
                .items(new ArrayList<>(List.of(item)))
                .build());

        em.persistAndFlush(Visit.builder()
                .client(jan).employee(employee).date(LocalDate.now())
                .items(new ArrayList<>())
                .build());

        List<ClientDTO> result = clientRepo.findAllWithFilters(null, null, null, null, null);

        assertThat(findByFirstName(result, "Jan").getVisitsCount()).isEqualTo(1L);
        assertThat(findByFirstName(result, "Anna").getVisitsCount()).isEqualTo(0L);
    }

    // helper method
    private ClientDTO findByFirstName(List<ClientDTO> list, String firstName) {
        return list.stream()
                .filter(c -> c.getFirstName().equals(firstName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Client not found: " + firstName));
    }
}

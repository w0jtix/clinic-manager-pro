package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.support.AbstractPostgresTest;

import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.DebtType;
import com.clinic.clinicmanager.model.constants.PaymentStatus;
import com.clinic.clinicmanager.repo.projection.CompanyRevenueProjection;
import com.clinic.clinicmanager.repo.projection.NameQuantityProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class VisitRepoTest extends AbstractPostgresTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private VisitRepo visitRepo;

    private Employee employee;
    private Client client;
    private BaseService service;
    private Product product;

    @BeforeEach
    void setUp() {
        BaseServiceCategory serviceCategory = em.persistAndFlush(
                BaseServiceCategory.builder().name("Pedicure").color("#111111").build());
        service = em.persistAndFlush(
                BaseService.builder().name("Pedicure Classic").price(BigDecimal.valueOf(80.0)).duration(60).category(serviceCategory).build());

        ProductCategory productCategory = em.persistAndFlush(
                ProductCategory.builder().name("Creams").color("#222222").build());
        Brand brand = em.persistAndFlush(Brand.builder().name("BrandName").build());
        product = em.persistAndFlush(
                Product.builder().name("Foot Cream").category(productCategory).brand(brand).supply(10).build());

        employee = em.persistAndFlush(Employee.builder().name("Anna").lastName("Nowak").drawingColor("#2563eb").build());
        client = em.persistAndFlush(Client.builder().firstName("Jan").lastName("Kowalski").build());
    }

    // helpers

    private Visit createVisitWithService(String serviceName, LocalDate date) {
        VisitItem item = VisitItem.builder()
                .service(service).name(serviceName)
                .duration(60).price(BigDecimal.valueOf(80.0)).finalPrice(BigDecimal.valueOf(80.0)).boostItem(false)
                .build();

        return em.persistAndFlush(Visit.builder()
                .client(client).employee(employee).date(date)
                .items(new ArrayList<>(List.of(item)))
                .build());
    }

    private Visit createVisitWithProduct(String productName, LocalDate date) {
        SaleItem saleItem = SaleItem.builder()
                .product(product).name(productName).price(BigDecimal.valueOf(50.0))
                .build();

        Sale sale = em.persistAndFlush(Sale.builder()
                .items(new ArrayList<>(List.of(saleItem)))
                .build());

        return em.persistAndFlush(Visit.builder()
                .client(client).employee(employee).date(date)
                .sale(sale)
                .build());
    }

    @Test
    void findTopSellingServiceName_returnsMostFrequentService() {
        createVisitWithService("Manicure", LocalDate.of(2024, 1, 1));
        createVisitWithService("Manicure", LocalDate.of(2024, 2, 1));
        createVisitWithService("Pedicure", LocalDate.of(2024, 3, 1));

        String result = visitRepo.findTopSellingServiceName(
                employee.getId(),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31));

        assertThat(result).isEqualTo("Manicure");
    }

    @Test
    void findTopSellingServiceName_excludesAbsenceVisits() {
        VisitItem item = VisitItem.builder()
                .service(service).name("Pedicure")
                .duration(60).price(BigDecimal.valueOf(80.0)).finalPrice(BigDecimal.valueOf(80.0)).boostItem(false)
                .build();
        em.persistAndFlush(Visit.builder()
                .client(client).employee(employee).date(LocalDate.of(2024, 1, 1))
                .absence(true)
                .items(new ArrayList<>(List.of(item)))
                .build());

        String result = visitRepo.findTopSellingServiceName(
                employee.getId(),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31));

        assertThat(result).isNull();
    }

    @Test
    void findTopSellingServiceName_excludesVisitsOutsideDateRange() {
        createVisitWithService("Pedicure", LocalDate.of(2023, 6, 1));

        String result = visitRepo.findTopSellingServiceName(
                employee.getId(),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31));

        assertThat(result).isNull();
    }

    @Test
    void findTopSellingServiceName_excludesOtherEmployees() {
        Employee other = em.persistAndFlush(Employee.builder().name("Ewa").lastName("TestEmp2").drawingColor("#2563eb").build());

        VisitItem item = VisitItem.builder()
                .service(service).name("Pedicure")
                .duration(60).price(BigDecimal.valueOf(80.0)).finalPrice(BigDecimal.valueOf(80.0)).boostItem(false)
                .build();
        em.persistAndFlush(Visit.builder()
                .client(client).employee(other).date(LocalDate.of(2024, 1, 10))
                .items(new ArrayList<>(List.of(item)))
                .build());

        String result = visitRepo.findTopSellingServiceName(
                employee.getId(),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31));

        assertThat(result).isNull();
    }

    @Test
    void findTopSellingProductName_returnsMostFrequentProduct() {
        createVisitWithProduct("Foot Cream", LocalDate.of(2024, 1, 1));
        createVisitWithProduct("Foot Cream", LocalDate.of(2024, 2, 1));
        createVisitWithProduct("Hand Cream", LocalDate.of(2024, 3, 1));

        String result = visitRepo.findTopSellingProductName(
                employee.getId(),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31));

        assertThat(result).isEqualTo("Foot Cream");
    }

    @Test
    void findTopSellingProductName_excludesSaleItemsWithoutProduct() {
        SaleItem voucherItem = SaleItem.builder()
                .name("Gift Voucher").price(BigDecimal.valueOf(100.0))
                .build();
        Sale sale = em.persistAndFlush(Sale.builder()
                .items(new ArrayList<>(List.of(voucherItem)))
                .build());
        em.persistAndFlush(Visit.builder()
                .client(client).employee(employee).date(LocalDate.of(2024, 1, 1))
                .sale(sale)
                .build());

        String result = visitRepo.findTopSellingProductName(
                employee.getId(),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31));

        assertThat(result).isNull();
    }

    @Test
    void findTopSellingProductName_excludesAbsenceVisits() {
        SaleItem item = SaleItem.builder().product(product).name("Foot Cream").price(BigDecimal.valueOf(50.0)).build();
        Sale sale = em.persistAndFlush(Sale.builder()
                .items(new ArrayList<>(List.of(item)))
                .build());
        em.persistAndFlush(Visit.builder()
                .client(client).employee(employee).date(LocalDate.of(2024, 1, 1))
                .absence(true).sale(sale)
                .build());

        String result = visitRepo.findTopSellingProductName(
                employee.getId(),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31));

        assertThat(result).isNull();
    }

    @Test
    void findTopSellingProductName_excludesVisitsOutsideDateRange() {
        createVisitWithProduct("Foot Cream", LocalDate.of(2023, 1, 1));

        String result = visitRepo.findTopSellingProductName(
                employee.getId(),
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31));

        assertThat(result).isNull();
    }

    private Visit createVisit(Client visitClient, LocalDate date, String totalValue,
                              boolean absence, Integer delayTime) {
        return em.persistAndFlush(Visit.builder()
                .client(visitClient).employee(employee).date(date)
                .totalValue(new BigDecimal(totalValue))
                .absence(absence)
                .delayTime(delayTime)
                .build());
    }

    private ClientDebt createDebt(Visit sourceVisit, DebtType type, PaymentStatus status) {
        return em.persistAndFlush(ClientDebt.builder()
                .client(sourceVisit.getClient()).sourceVisit(sourceVisit)
                .type(type).value(new BigDecimal("50.00")).paymentStatus(status)
                .build());
    }

    private Visit createVisitWithServiceFor(Client visitClient, String serviceName,
                                            LocalDate date, boolean absence) {
        VisitItem item = VisitItem.builder()
                .service(service).name(serviceName)
                .duration(60).price(BigDecimal.valueOf(80.0)).finalPrice(BigDecimal.valueOf(80.0)).boostItem(false)
                .build();

        return em.persistAndFlush(Visit.builder()
                .client(visitClient).employee(employee).date(date)
                .absence(absence)
                .items(new ArrayList<>(List.of(item)))
                .build());
    }

    private Visit createVisitWithSaleItemsFor(Client visitClient, LocalDate date,
                                              boolean absence, SaleItem... saleItems) {
        Sale sale = em.persistAndFlush(Sale.builder()
                .items(new ArrayList<>(List.of(saleItems)))
                .build());

        return em.persistAndFlush(Visit.builder()
                .client(visitClient).employee(employee).date(date)
                .absence(absence)
                .sale(sale)
                .build());
    }

    private Client otherClient() {
        return em.persistAndFlush(Client.builder().firstName("Ewa").lastName("Nowak").build());
    }

    @Test
    void countByClientIdAndAbsenceFalse_countsOnlyAttendedVisitsOfThatClient() {
        createVisit(client, LocalDate.of(2025, 1, 10), "100.00", false, null);
        createVisit(client, LocalDate.of(2025, 2, 10), "100.00", false, null);
        createVisit(client, LocalDate.of(2025, 3, 10), "0.00", true, null);
        createVisit(otherClient(), LocalDate.of(2025, 4, 10), "100.00", false, null);

        assertThat(visitRepo.countByClientIdAndAbsenceFalse(client.getId())).isEqualTo(2L);
    }

    @Test
    void countByClientIdAndAbsenceTrue_countsOnlyAbsences() {
        createVisit(client, LocalDate.of(2025, 1, 10), "100.00", false, null);
        createVisit(client, LocalDate.of(2025, 2, 10), "0.00", true, null);
        createVisit(client, LocalDate.of(2025, 3, 10), "0.00", true, null);

        assertThat(visitRepo.countByClientIdAndAbsenceTrue(client.getId())).isEqualTo(2L);
    }

    @Test
    void countLatenessByClientId_ignoresNullAndZeroDelay() {
        createVisit(client, LocalDate.of(2025, 1, 10), "100.00", false, null);
        createVisit(client, LocalDate.of(2025, 2, 10), "100.00", false, 0);
        createVisit(client, LocalDate.of(2025, 3, 10), "100.00", false, 15);
        createVisit(client, LocalDate.of(2025, 4, 10), "100.00", false, 5);
        createVisit(otherClient(), LocalDate.of(2025, 5, 10), "100.00", false, 30);

        assertThat(visitRepo.countLatenessByClientId(client.getId())).isEqualTo(2L);
    }

    @Test
    void sumTotalValueByClientId_sumsAttendedVisitsOnly() {
        createVisit(client, LocalDate.of(2025, 1, 10), "100.50", false, null);
        createVisit(client, LocalDate.of(2025, 2, 10), "49.50", false, null);
        createVisit(client, LocalDate.of(2025, 3, 10), "999.99", true, null);
        createVisit(otherClient(), LocalDate.of(2025, 4, 10), "500.00", false, null);

        assertThat(visitRepo.sumTotalValueByClientId(client.getId())).isEqualByComparingTo("150.00");
    }

    @Test
    void sumTotalValueByClientId_returnsZero_whenClientHasNoVisits() {
        assertThat(visitRepo.sumTotalValueByClientId(client.getId())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void findServiceQuantitiesByClientId_groupsByName_andExcludesAbsences() {
        createVisitWithServiceFor(client, "Manicure", LocalDate.of(2025, 1, 10), false);
        createVisitWithServiceFor(client, "Manicure", LocalDate.of(2025, 2, 10), false);
        createVisitWithServiceFor(client, "Pedicure", LocalDate.of(2025, 3, 10), false);
        createVisitWithServiceFor(client, "Pedicure", LocalDate.of(2025, 4, 10), true);
        createVisitWithServiceFor(otherClient(), "Manicure", LocalDate.of(2025, 5, 10), false);

        Map<String, Long> quantities = visitRepo.findServiceQuantitiesByClientId(client.getId()).stream()
                .collect(Collectors.toMap(NameQuantityProjection::getName, NameQuantityProjection::getQuantity));

        assertThat(quantities).containsOnly(entry("Manicure", 2L), entry("Pedicure", 1L));
    }

    @Test
    void findProductQuantitiesByClientId_groupsByName_andSkipsSaleItemsWithoutProduct() {
        createVisitWithSaleItemsFor(client, LocalDate.of(2025, 1, 10), false,
                SaleItem.builder().product(product).name("Foot Cream").price(BigDecimal.valueOf(50.0)).build(),
                SaleItem.builder().name("Gift Voucher").price(BigDecimal.valueOf(100.0)).build());
        createVisitWithSaleItemsFor(client, LocalDate.of(2025, 2, 10), false,
                SaleItem.builder().product(product).name("Foot Cream").price(BigDecimal.valueOf(50.0)).build());
        createVisitWithSaleItemsFor(client, LocalDate.of(2025, 3, 10), true,
                SaleItem.builder().product(product).name("Foot Cream").price(BigDecimal.valueOf(50.0)).build());

        Map<String, Long> quantities = visitRepo.findProductQuantitiesByClientId(client.getId()).stream()
                .collect(Collectors.toMap(NameQuantityProjection::getName, NameQuantityProjection::getQuantity));

        assertThat(quantities).containsOnly(entry("Foot Cream", 2L));
    }

    @Test
    void findMonthlySpendByClientId_groupsAttendedVisitsByMonthOfGivenYear() {
        createVisit(client, LocalDate.of(2025, 1, 10), "100.00", false, null);
        createVisit(client, LocalDate.of(2025, 1, 20), "50.00", false, null);
        createVisit(client, LocalDate.of(2025, 3, 5), "200.00", false, null);
        createVisit(client, LocalDate.of(2025, 4, 5), "999.00", true, null);
        createVisit(client, LocalDate.of(2024, 1, 5), "777.00", false, null);

        List<CompanyRevenueProjection> rows = visitRepo.findMonthlySpendByClientId(client.getId(), 2025);

        Map<Integer, BigDecimal> byMonth = rows.stream()
                .collect(Collectors.toMap(CompanyRevenueProjection::getPeriod, CompanyRevenueProjection::getRevenue));

        assertThat(byMonth).hasSize(2);
        assertThat(byMonth.get(1)).isEqualByComparingTo("150.00");
        assertThat(byMonth.get(3)).isEqualByComparingTo("200.00");

        Map<Integer, Integer> countsByMonth = rows.stream()
                .collect(Collectors.toMap(CompanyRevenueProjection::getPeriod, CompanyRevenueProjection::getVisitCount));

        assertThat(countsByMonth).containsOnly(entry(1, 2), entry(3, 1));
    }

    @Test
    void findFirstAndLastVisitDateByClientId_spanAttendedVisits() {
        createVisit(client, LocalDate.of(2023, 5, 10), "100.00", false, null);
        createVisit(client, LocalDate.of(2025, 8, 1), "100.00", false, null);
        createVisit(client, LocalDate.of(2026, 9, 1), "0.00", true, null);

        assertThat(visitRepo.findFirstVisitDateByClientId(client.getId())).isEqualTo(LocalDate.of(2023, 5, 10));
        assertThat(visitRepo.findLastVisitDateByClientId(client.getId())).isEqualTo(LocalDate.of(2025, 8, 1));
    }

    @Test
    void findFirstVisitDateByClientId_returnsNull_whenClientHasNoVisits() {
        assertThat(visitRepo.findFirstVisitDateByClientId(client.getId())).isNull();
    }

    @Test
    void findLastAbsenceUnpaidDate_returnsAbsenceDate_whenLatestAbsenceHasUnpaidAbsenceFee() {
        createVisit(client, LocalDate.of(2026, 9, 1), "100.00", false, null);
        Visit absence = createVisit(client, LocalDate.of(2026, 9, 22), "0.00", true, null);
        createDebt(absence, DebtType.ABSENCE_FEE, PaymentStatus.UNPAID);

        assertThat(visitRepo.findLastAbsenceUnpaidDateByClientId(client.getId())).isEqualTo(LocalDate.of(2026, 9, 22));
        assertThat(visitRepo.findLastVisitDateByClientId(client.getId())).isEqualTo(LocalDate.of(2026, 9, 1));
    }

    @Test
    void findLastAbsenceUnpaidDate_returnsAbsenceDate_whenAbsenceFeePartiallyPaid() {
        createVisit(client, LocalDate.of(2026, 9, 1), "100.00", false, null);
        Visit absence = createVisit(client, LocalDate.of(2026, 9, 22), "0.00", true, null);
        createDebt(absence, DebtType.ABSENCE_FEE, PaymentStatus.PARTIAL);

        assertThat(visitRepo.findLastAbsenceUnpaidDateByClientId(client.getId())).isEqualTo(LocalDate.of(2026, 9, 22));
    }

    @Test
    void findLastAbsenceUnpaidDate_returnsNull_whenAbsenceFeePaid() {
        createVisit(client, LocalDate.of(2026, 9, 1), "100.00", false, null);
        Visit absence = createVisit(client, LocalDate.of(2026, 9, 22), "0.00", true, null);
        createDebt(absence, DebtType.ABSENCE_FEE, PaymentStatus.PAID);

        assertThat(visitRepo.findLastAbsenceUnpaidDateByClientId(client.getId())).isNull();
    }

    @Test
    void findLastAbsenceUnpaidDate_returnsNull_whenAbsenceHasNoDebt() {
        createVisit(client, LocalDate.of(2026, 9, 22), "0.00", true, null);

        assertThat(visitRepo.findLastAbsenceUnpaidDateByClientId(client.getId())).isNull();
    }

    @Test
    void findLastAbsenceUnpaidDate_returnsNull_whenRegularVisitIsAfterUnpaidAbsence() {
        Visit absence = createVisit(client, LocalDate.of(2026, 9, 10), "0.00", true, null);
        createDebt(absence, DebtType.ABSENCE_FEE, PaymentStatus.UNPAID);
        createVisit(client, LocalDate.of(2026, 9, 22), "100.00", false, null);

        assertThat(visitRepo.findLastAbsenceUnpaidDateByClientId(client.getId())).isNull();
    }

    @Test
    void findLastAbsenceUnpaidDate_returnsNull_whenClientHasNoVisits() {
        assertThat(visitRepo.findLastAbsenceUnpaidDateByClientId(client.getId())).isNull();
        assertThat(visitRepo.findLastVisitDateByClientId(client.getId())).isNull();
    }

    @Test
    void findLastAbsenceUnpaidDate_returnsNull_whenUnpaidDebtOnAbsenceIsNotAbsenceFee() {
        Visit absence = createVisit(client, LocalDate.of(2026, 9, 22), "0.00", true, null);
        createDebt(absence, DebtType.UNPAID, PaymentStatus.UNPAID);

        assertThat(visitRepo.findLastAbsenceUnpaidDateByClientId(client.getId())).isNull();
    }

    @Test
    void findLastAbsenceUnpaidDate_returnsNull_whenUnpaidDebtOnAbsenceIsPartialPayment() {
        Visit absence = createVisit(client, LocalDate.of(2026, 9, 22), "0.00", true, null);
        createDebt(absence, DebtType.PARTIAL_PAYMENT, PaymentStatus.PARTIAL);

        assertThat(visitRepo.findLastAbsenceUnpaidDateByClientId(client.getId())).isNull();
    }

    @Test
    void findLastAbsenceUnpaidDate_sameDay_absenceWithHigherIdWins() {
        createVisit(client, LocalDate.of(2026, 9, 22), "100.00", false, null);
        Visit absence = createVisit(client, LocalDate.of(2026, 9, 22), "0.00", true, null);
        createDebt(absence, DebtType.ABSENCE_FEE, PaymentStatus.UNPAID);

        assertThat(visitRepo.findLastAbsenceUnpaidDateByClientId(client.getId())).isEqualTo(LocalDate.of(2026, 9, 22));
    }

    @Test
    void findLastAbsenceUnpaidDate_sameDay_regularVisitWithHigherIdWins() {
        Visit absence = createVisit(client, LocalDate.of(2026, 9, 22), "0.00", true, null);
        createDebt(absence, DebtType.ABSENCE_FEE, PaymentStatus.UNPAID);
        createVisit(client, LocalDate.of(2026, 9, 22), "100.00", false, null);

        assertThat(visitRepo.findLastAbsenceUnpaidDateByClientId(client.getId())).isNull();
    }

    @Test
    void findLastAbsenceUnpaidDate_ignoresOtherClientsVisits() {
        Client other = em.persistAndFlush(Client.builder().firstName("Ewa").lastName("Lis").build());
        Visit absence = createVisit(client, LocalDate.of(2026, 9, 22), "0.00", true, null);
        createDebt(absence, DebtType.ABSENCE_FEE, PaymentStatus.UNPAID);
        createVisit(other, LocalDate.of(2026, 9, 30), "100.00", false, null);

        assertThat(visitRepo.findLastAbsenceUnpaidDateByClientId(client.getId())).isEqualTo(LocalDate.of(2026, 9, 22));
    }

    @Test
    void findAllByClientId_ordersByDateDescThenIdDesc() {
        Visit oldest = createVisit(client, LocalDate.of(2025, 1, 10), "100.00", false, null);
        Visit sameDayFirst = createVisit(client, LocalDate.of(2025, 3, 5), "100.00", false, null);
        Visit sameDaySecond = createVisit(client, LocalDate.of(2025, 3, 5), "100.00", false, null);

        List<Visit> result = visitRepo.findAllByClientId(client.getId(), PageRequest.of(0, 10)).getContent();

        assertThat(result).extracting(Visit::getId)
                .containsExactly(sameDaySecond.getId(), sameDayFirst.getId(), oldest.getId());
    }

    @Test
    void findAllByClientId_paginatesAndExcludesOtherClients() {
        Client otherClient = em.persistAndFlush(Client.builder().firstName("Ewa").lastName("Nowak").build());
        createVisit(otherClient, LocalDate.of(2025, 6, 1), "100.00", false, null);
        createVisit(client, LocalDate.of(2025, 1, 1), "100.00", false, null);
        createVisit(client, LocalDate.of(2025, 2, 1), "100.00", false, null);
        createVisit(client, LocalDate.of(2025, 3, 1), "100.00", false, null);

        Page<Visit> firstPage = visitRepo.findAllByClientId(client.getId(), PageRequest.of(0, 2));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.isLast()).isFalse();
        assertThat(firstPage.getContent()).extracting(Visit::getDate)
                .containsExactly(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 2, 1));

        Page<Visit> secondPage = visitRepo.findAllByClientId(client.getId(), PageRequest.of(1, 2));

        assertThat(secondPage.isLast()).isTrue();
        assertThat(secondPage.getContent()).extracting(Visit::getDate)
                .containsExactly(LocalDate.of(2025, 1, 1));
    }

    private List<LocalDate> findDatesInRange(LocalDate dateFrom, LocalDate dateTo) {
        return visitRepo.findAllWithFilters(null, null, null, null, null, null, null, null, null,
                        dateFrom, dateTo, null, null, null, PageRequest.of(0, 30))
                .getContent().stream().map(Visit::getDate).toList();
    }

    @Test
    void findAllWithFilters_singleDayRange_returnsOnlyThatDay() {
        createVisit(client, LocalDate.of(2026, 10, 14), "100.00", false, null);
        createVisit(client, LocalDate.of(2026, 10, 15), "100.00", false, null);
        createVisit(client, LocalDate.of(2026, 10, 16), "100.00", false, null);

        List<LocalDate> result = findDatesInRange(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 15));

        assertThat(result).containsExactly(LocalDate.of(2026, 10, 15));
    }

    @Test
    void findAllWithFilters_weekRange_includesBothBounds() {
        createVisit(client, LocalDate.of(2026, 10, 11), "100.00", false, null);
        createVisit(client, LocalDate.of(2026, 10, 12), "100.00", false, null);
        createVisit(client, LocalDate.of(2026, 10, 15), "100.00", false, null);
        createVisit(client, LocalDate.of(2026, 10, 18), "100.00", false, null);
        createVisit(client, LocalDate.of(2026, 10, 19), "100.00", false, null);

        List<LocalDate> result = findDatesInRange(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18));

        assertThat(result).containsExactlyInAnyOrder(
                LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 12));
    }
}

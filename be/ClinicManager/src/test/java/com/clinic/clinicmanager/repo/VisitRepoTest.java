package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.*;
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
class VisitRepoTest {

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
                BaseService.builder().name("Pedicure Classic").price(80.0).duration(60).category(serviceCategory).build());

        ProductCategory productCategory = em.persistAndFlush(
                ProductCategory.builder().name("Creams").color("#222222").build());
        Brand brand = em.persistAndFlush(Brand.builder().name("BrandName").build());
        product = em.persistAndFlush(
                Product.builder().name("Foot Cream").category(productCategory).brand(brand).supply(10).build());

        employee = em.persistAndFlush(Employee.builder().name("Anna").lastName("Nowak").build());
        client = em.persistAndFlush(Client.builder().firstName("Jan").lastName("Kowalski").build());
    }

    // helpers

    private Visit createVisitWithService(String serviceName, LocalDate date) {
        VisitItem item = VisitItem.builder()
                .service(service).name(serviceName)
                .duration(60).price(80.0).finalPrice(80.0).boostItem(false)
                .build();

        return em.persistAndFlush(Visit.builder()
                .client(client).employee(employee).date(date)
                .items(new ArrayList<>(List.of(item)))
                .build());
    }

    private Visit createVisitWithProduct(String productName, LocalDate date) {
        SaleItem saleItem = SaleItem.builder()
                .product(product).name(productName).price(50.0)
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
                .duration(60).price(80.0).finalPrice(80.0).boostItem(false)
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
        Employee other = em.persistAndFlush(Employee.builder().name("Ewa").lastName("TestEmp2").build());

        VisitItem item = VisitItem.builder()
                .service(service).name("Pedicure")
                .duration(60).price(80.0).finalPrice(80.0).boostItem(false)
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
                .name("Gift Voucher").price(100.0)
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
        SaleItem item = SaleItem.builder().product(product).name("Foot Cream").price(50.0).build();
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
}

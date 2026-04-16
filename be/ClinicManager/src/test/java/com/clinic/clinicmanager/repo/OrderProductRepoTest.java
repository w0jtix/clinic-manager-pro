package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.VatRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class OrderProductRepoTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private OrderProductRepo orderProductRepo;

    private Product product;
    private Supplier supplier;

    @BeforeEach
    void setUp() {
        ProductCategory category = em.persistAndFlush(
                ProductCategory.builder().name("TestCat").color("#000000").build());
        Brand brand = em.persistAndFlush(
                Brand.builder().name("TestBrand").build());
        product = em.persistAndFlush(
                Product.builder().name("TestProduct").category(category).brand(brand).supply(10).build());
        supplier = em.persistAndFlush(
                Supplier.builder().name("TestSupplier").build());
    }

    // helper

    private OrderProduct createOrderWithProduct(LocalDate date, double price, long orderNumber) {
        Order order = em.persistAndFlush(Order.builder()
                .supplier(supplier)
                .orderNumber(orderNumber)
                .orderDate(date)
                .build());

        return em.persistAndFlush(OrderProduct.builder()
                .order(order)
                .product(product)
                .name(product.getName())
                .quantity(1)
                .vatRate(VatRate.VAT_23)
                .price(price)
                .build());
    }

    @Test
    void findLatestByProductIdBeforeDate_excludesOrdersAfterCutoff() {
        createOrderWithProduct(LocalDate.of(2024, 1, 1), 10.0, 1L);
        createOrderWithProduct(LocalDate.of(2024, 6, 1), 20.0, 2L);

        LocalDate cutoff = LocalDate.of(2024, 3, 1);

        List<OrderProduct> result = orderProductRepo.findLatestByProductIdBeforeDate(
                product.getId(), cutoff, PageRequest.of(0, 5));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getPrice()).isEqualTo(10.0);
    }

    @Test
    void findLatestByProductIdBeforeDate_includesOrderOnExactCutoffDate() {
        createOrderWithProduct(LocalDate.of(2024, 3, 1), 10.0, 1L);

        LocalDate cutoff = LocalDate.of(2024, 3, 1);

        List<OrderProduct> result = orderProductRepo.findLatestByProductIdBeforeDate(
                product.getId(), cutoff, PageRequest.of(0, 5));

        assertThat(result).hasSize(1);
    }

    @Test
    void findLatestByProductIdBeforeDate_returnsLatestFirst() {
        createOrderWithProduct(LocalDate.of(2024, 1, 1), 10.0, 1L);
        createOrderWithProduct(LocalDate.of(2024, 2, 1), 30.0, 2L);

        LocalDate cutoff = LocalDate.of(2024, 6, 1);

        List<OrderProduct> result = orderProductRepo.findLatestByProductIdBeforeDate(
                product.getId(), cutoff, PageRequest.of(0, 1));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getPrice()).isEqualTo(30.0);
    }

    @Test
    void findLatestByProductIdBeforeDate_allOrdersAfterCutoff_returnsEmpty() {
        createOrderWithProduct(LocalDate.of(2024, 6, 1), 20.0, 1L);
        createOrderWithProduct(LocalDate.of(2024, 9, 1), 30.0, 2L);

        LocalDate cutoff = LocalDate.of(2024, 1, 1);

        List<OrderProduct> result = orderProductRepo.findLatestByProductIdBeforeDate(
                product.getId(), cutoff, PageRequest.of(0, 5));

        assertThat(result).isEmpty();
    }
}

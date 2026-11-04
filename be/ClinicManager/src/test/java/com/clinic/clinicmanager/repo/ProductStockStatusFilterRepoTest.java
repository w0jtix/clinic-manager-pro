package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.support.AbstractPostgresTest;

import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.ProductCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// findAllWithFilters itself cannot run on H2 (COALESCE(:listParam, NULL) IS NULL, see ProductRepoTest),
// so the shared stock status predicate is exercised here on its own.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProductStockStatusFilterRepoTest extends AbstractPostgresTest {

    private static final String QUERY = "SELECT p FROM Product p WHERE p.isDeleted = FALSE"
            + ProductRepo.STOCK_STATUS_FILTER;

    @Autowired
    private TestEntityManager em;

    private Product outOfStock;
    private Product atThreshold;
    private Product aboveThreshold;
    private Product zeroThreshold;

    @BeforeEach
    void setUp() {
        ProductCategory category = em.persistAndFlush(ProductCategory.builder()
                .name("Cosmetics").color("#FF0000").build());
        Brand brand = em.persistAndFlush(Brand.builder()
                .name("Nike").build());

        outOfStock = em.persistAndFlush(Product.builder()
                .name("Out of stock").category(category).brand(brand)
                .supply(0).lowStockThreshold(2).build());
        atThreshold = em.persistAndFlush(Product.builder()
                .name("At threshold").category(category).brand(brand)
                .supply(2).lowStockThreshold(2).build());
        aboveThreshold = em.persistAndFlush(Product.builder()
                .name("Above threshold").category(category).brand(brand)
                .supply(3).lowStockThreshold(2).build());
        zeroThreshold = em.persistAndFlush(Product.builder()
                .name("Zero threshold").category(category).brand(brand)
                .supply(3).lowStockThreshold(0).build());
    }

    private List<Product> filterBy(String stockStatus) {
        return em.getEntityManager()
                .createQuery(QUERY, Product.class)
                .setParameter("stockStatus", stockStatus)
                .getResultList();
    }

    @Test
    void stockStatusFilter_whenNull_returnsAllProducts() {
        List<Product> result = filterBy(null);

        assertThat(result).containsExactlyInAnyOrder(outOfStock, atThreshold, aboveThreshold, zeroThreshold);
    }

    @Test
    void stockStatusFilter_whenOutOfStock_returnsOnlyProductsWithZeroSupply() {
        List<Product> result = filterBy("OUT_OF_STOCK");

        assertThat(result).containsExactly(outOfStock);
    }

    @Test
    void stockStatusFilter_whenLowStock_returnsProductWithSupplyEqualToThreshold() {
        List<Product> result = filterBy("LOW_STOCK");

        assertThat(result).containsExactly(atThreshold);
    }

    @Test
    void stockStatusFilter_whenOk_returnsProductsWithSupplyAboveThreshold() {
        List<Product> result = filterBy("OK");

        assertThat(result).containsExactlyInAnyOrder(aboveThreshold, zeroThreshold);
    }

    @Test
    void stockStatusFilter_whenThresholdIsZeroAndSupplyPositive_isNeverLowStock() {
        assertThat(filterBy("LOW_STOCK")).doesNotContain(zeroThreshold);
        assertThat(filterBy("OK")).contains(zeroThreshold);
    }

    @Test
    void stockStatusFilter_whenSupplyIsZero_isNeitherLowStockNorOk() {
        assertThat(filterBy("LOW_STOCK")).doesNotContain(outOfStock);
        assertThat(filterBy("OK")).doesNotContain(outOfStock);
    }
}

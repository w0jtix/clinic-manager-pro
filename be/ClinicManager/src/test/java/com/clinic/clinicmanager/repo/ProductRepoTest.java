package com.clinic.clinicmanager.repo;

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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

//Cannot test both findAllWithFilters with H2 due to COALESCE(:listParam, NULL) IS NULL as null-check on collection.
// H2 throws "Unknown data type: NULL, ?".
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProductRepoTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ProductRepo productRepo;

    private ProductCategory category;
    private Brand brand;
    private Product produkt;

    @BeforeEach
    void setUp() {
        category = em.persistAndFlush(ProductCategory.builder()
                .name("Cosmetics").color("#FF0000").build());
        brand = em.persistAndFlush(Brand.builder()
                .name("Nike").build());
        produkt = em.persistAndFlush(Product.builder()
                .name("Product A").category(category).brand(brand).supply(10).build());
    }

    @Test
    void findByName_whenExists_returnsProduct() {
        Optional<Product> result = productRepo.findByName("Product A");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Product A");
    }

    @Test
    void findByName_whenNotExists_returnsEmpty() {
        Optional<Product> result = productRepo.findByName("NotFound");

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdIncludingDeleted_returnsDeletedProduct() {
        Product deleted = em.persistAndFlush(Product.builder()
                .name("Deleted").category(category).brand(brand).isDeleted(true).build());

        Optional<Product> result = productRepo.findByIdIncludingDeleted(deleted.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getIsDeleted()).isTrue();
    }

    @Test
    void findByIdNotDeleted_whenDeleted_returnsEmpty() {
        Product deleted = em.persistAndFlush(Product.builder()
                .name("Deleted").category(category).brand(brand).isDeleted(true).build());

        Optional<Product> result = productRepo.findByIdNotDeleted(deleted.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdNotDeleted_whenNotDeleted_returnsProduct() {
        Optional<Product> result = productRepo.findByIdNotDeleted(produkt.getId());

        assertThat(result).isPresent();
    }

    @Test
    void isProductSoftDeleted_whenNotDeleted_returnsFalse() {
        Boolean result = productRepo.isProductSoftDeleted(produkt.getId());

        assertThat(result).isFalse();
    }

    @Test
    void isProductSoftDeleted_whenDeleted_returnsTrue() {
        Product deleted = em.persistAndFlush(Product.builder()
                .name("Deleted").category(category).brand(brand).isDeleted(true).build());

        Boolean result = productRepo.isProductSoftDeleted(deleted.getId());

        assertThat(result).isTrue();
    }
}

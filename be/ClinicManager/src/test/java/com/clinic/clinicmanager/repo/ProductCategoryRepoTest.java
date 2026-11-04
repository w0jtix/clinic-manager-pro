package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.DTO.CategoryUsageDTO;
import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ProductCategoryRepoTest extends AbstractPostgresTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ProductCategoryRepo productCategoryRepo;

    private ProductCategory creams;
    private ProductCategory empty;
    private ProductCategory hidden;
    private Brand brand;

    @BeforeEach
    void setUp() {
        creams = em.persistAndFlush(ProductCategory.builder().name("CatRepo Kremy").color("11,11,11").build());
        empty = em.persistAndFlush(ProductCategory.builder().name("CatRepo Pusta").color("22,22,22").build());
        hidden = em.persistAndFlush(ProductCategory.builder().name("CatRepo Jednorazowe").color("33,33,33").isDeleted(true).build());
        brand = em.persistAndFlush(Brand.builder().name("CatRepo Brand").build());

        persistProduct("CatRepo Krem 1", creams, false);
        persistProduct("CatRepo Krem 2", creams, false);
        persistProduct("CatRepo Krem archiwalny", creams, true);
        persistProduct("CatRepo Jednorazowy archiwalny", hidden, true);
    }

    private void persistProduct(String name, ProductCategory category, boolean deleted) {
        em.persistAndFlush(Product.builder().name(name).category(category).brand(brand).supply(0).isDeleted(deleted).build());
    }

    @Test
    void findActiveCategoryUsage_countsOnlyActiveProductsAndReturnsZeroForEmptyCategory() {
        Map<Long, Long> usage = productCategoryRepo.findActiveCategoryUsage().stream()
                .collect(Collectors.toMap(CategoryUsageDTO::getCategoryId, CategoryUsageDTO::getCount));

        assertThat(usage).containsEntry(creams.getId(), 2L);
        assertThat(usage).containsEntry(empty.getId(), 0L);
        assertThat(usage).doesNotContainKey(hidden.getId());
    }

    @Test
    void findAllByIsDeletedFalse_excludesHiddenCategories() {
        List<ProductCategory> result = productCategoryRepo.findAllByIsDeletedFalse();

        assertThat(result).extracting(ProductCategory::getId).contains(creams.getId(), empty.getId());
        assertThat(result).extracting(ProductCategory::getId).doesNotContain(hidden.getId());
    }

    @Test
    void findAllByCategoryName_findsHiddenCategoryIgnoringCaseAndSpaces() {
        List<ProductCategory> result = productCategoryRepo.findAllByCategoryName(" catrepo JEDNORAZOWE ");

        assertThat(result).extracting(ProductCategory::getId).containsExactly(hidden.getId());
        assertThat(result.getFirst().getIsDeleted()).isTrue();
    }

    @Test
    void nameIndex_rejectsNameDifferingOnlyByCaseAndSpaces() {
        productCategoryRepo.saveAndFlush(ProductCategory.builder().name("abc ").color("44,44,44").build());

        assertThatThrownBy(() -> productCategoryRepo.saveAndFlush(
                ProductCategory.builder().name("Abc").color("55,55,55").build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(((DataIntegrityViolationException) e).getMostSpecificCause().getMessage())
                        .contains("\"uq_product_category_name_ci\""));
    }

    @Test
    void nameIndex_rejectsActiveNameEqualToHiddenCategoryIgnoringCase() {
        assertThatThrownBy(() -> productCategoryRepo.saveAndFlush(
                ProductCategory.builder().name("CATREPO JEDNORAZOWE").color("44,44,44").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void colorIndex_violationMessageContainsConstraintName() {
        assertThatThrownBy(() -> productCategoryRepo.saveAndFlush(
                ProductCategory.builder().name("CatRepo Inna").color("11,11,11").build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(((DataIntegrityViolationException) e).getMostSpecificCause().getMessage())
                        .contains("uq_product_category_color_active\""));
    }

    @Test
    void findActiveColors_returnsOnlyActiveCategoryColors() {
        List<String> colors = productCategoryRepo.findActiveColors();

        assertThat(colors).contains("11,11,11", "22,22,22");
        assertThat(colors).doesNotContain("33,33,33");
    }

    @Test
    void findByIdAndIsDeletedTrue_returnsOnlyHiddenCategory() {
        Optional<ProductCategory> hiddenResult = productCategoryRepo.findByIdAndIsDeletedTrue(hidden.getId());
        Optional<ProductCategory> activeResult = productCategoryRepo.findByIdAndIsDeletedTrue(creams.getId());

        assertThat(hiddenResult).isPresent();
        assertThat(hiddenResult.get().getName()).isEqualTo("CatRepo Jednorazowe");
        assertThat(activeResult).isEmpty();
    }

    @Test
    void existsByColorAndIsDeletedFalse_ignoresHiddenCategories() {
        assertThat(productCategoryRepo.existsByColorAndIsDeletedFalse("11,11,11")).isTrue();
        assertThat(productCategoryRepo.existsByColorAndIsDeletedFalse("33,33,33")).isFalse();
    }

    @Test
    void existsByColorAndIsDeletedFalseAndIdNot_excludesCurrentCategory() {
        assertThat(productCategoryRepo.existsByColorAndIsDeletedFalseAndIdNot("11,11,11", creams.getId())).isFalse();
        assertThat(productCategoryRepo.existsByColorAndIsDeletedFalseAndIdNot("11,11,11", empty.getId())).isTrue();
    }

    @Test
    void colorIndex_allowsColorOfHiddenCategoryForActiveCategory() {
        ProductCategory reused = em.persistAndFlush(ProductCategory.builder().name("CatRepo Nowa").color("33,33,33").build());

        assertThat(reused.getId()).isNotNull();
    }

    @Test
    void colorIndex_rejectsDuplicateColorAmongActiveCategories() {
        assertThatThrownBy(() -> em.persistAndFlush(
                ProductCategory.builder().name("CatRepo Duplikat").color("11,11,11").build()))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void newCategory_defaultsToNonSystemAndActive() {
        ProductCategory reloaded = em.find(ProductCategory.class, empty.getId());

        assertThat(reloaded.getIsSystem()).isFalse();
        assertThat(reloaded.getIsDeleted()).isFalse();
    }
}

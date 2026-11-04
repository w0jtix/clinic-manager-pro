package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.DTO.CategoryUsageDTO;
import com.clinic.clinicmanager.model.BaseService;
import com.clinic.clinicmanager.model.BaseServiceCategory;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class BaseServiceCategoryRepoTest extends AbstractPostgresTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private BaseServiceCategoryRepo serviceCategoryRepo;

    private BaseServiceCategory classic;
    private BaseServiceCategory empty;
    private BaseServiceCategory hidden;

    @BeforeEach
    void setUp() {
        classic = em.persistAndFlush(BaseServiceCategory.builder().name("SvcRepo Klasyczne").color("#a1a1a1").build());
        empty = em.persistAndFlush(BaseServiceCategory.builder().name("SvcRepo Puste").color("#b2b2b2").build());
        hidden = em.persistAndFlush(BaseServiceCategory.builder().name("SvcRepo Masaże").color("#c3c3c3").isDeleted(true).build());

        persistService("SvcRepo Pedicure", classic, false);
        persistService("SvcRepo Manicure", classic, false);
        persistService("SvcRepo Stara", classic, true);
        persistService("SvcRepo Masaż archiwalny", hidden, true);
    }

    private void persistService(String name, BaseServiceCategory category, boolean deleted) {
        em.persistAndFlush(BaseService.builder().name(name).price(BigDecimal.valueOf(100)).duration(30)
                .category(category).isDeleted(deleted).build());
    }

    @Test
    void findActiveCategoryUsage_countsOnlyActiveServicesAndReturnsZeroForEmptyCategory() {
        Map<Long, Long> usage = serviceCategoryRepo.findActiveCategoryUsage().stream()
                .collect(Collectors.toMap(CategoryUsageDTO::getCategoryId, CategoryUsageDTO::getCount));

        assertThat(usage).containsEntry(classic.getId(), 2L);
        assertThat(usage).containsEntry(empty.getId(), 0L);
        assertThat(usage).doesNotContainKey(hidden.getId());
    }

    @Test
    void findAllByCategoryName_findsHiddenCategoryIgnoringCaseAndSpaces() {
        List<BaseServiceCategory> result = serviceCategoryRepo.findAllByCategoryName(" svcrepo MASAŻE ");

        assertThat(result).extracting(BaseServiceCategory::getId).containsExactly(hidden.getId());
    }

    @Test
    void nameIndex_rejectsNameDifferingOnlyByCaseAndSpaces() {
        serviceCategoryRepo.saveAndFlush(BaseServiceCategory.builder().name("abc ").color("#d4d4d4").build());

        assertThatThrownBy(() -> serviceCategoryRepo.saveAndFlush(
                BaseServiceCategory.builder().name("Abc").color("#e5e5e5").build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(((DataIntegrityViolationException) e).getMostSpecificCause().getMessage())
                        .contains("\"uq_service_category_name_ci\""));
    }

    @Test
    void nameIndex_rejectsActiveNameEqualToHiddenCategoryIgnoringCase() {
        assertThatThrownBy(() -> serviceCategoryRepo.saveAndFlush(
                BaseServiceCategory.builder().name("SVCREPO MASAŻE").color("#d4d4d4").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void colorIndex_violationMessageContainsConstraintName() {
        assertThatThrownBy(() -> serviceCategoryRepo.saveAndFlush(
                BaseServiceCategory.builder().name("SvcRepo Inna").color("#a1a1a1").build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(((DataIntegrityViolationException) e).getMostSpecificCause().getMessage())
                        .contains("uq_service_category_color_active\""));
    }

    @Test
    void findByIdAndIsDeletedTrue_returnsOnlyHiddenCategory() {
        Optional<BaseServiceCategory> hiddenResult = serviceCategoryRepo.findByIdAndIsDeletedTrue(hidden.getId());
        Optional<BaseServiceCategory> activeResult = serviceCategoryRepo.findByIdAndIsDeletedTrue(classic.getId());

        assertThat(hiddenResult).isPresent();
        assertThat(activeResult).isEmpty();
    }

    @Test
    void existsByColorAndIsDeletedFalse_ignoresHiddenCategories() {
        assertThat(serviceCategoryRepo.existsByColorAndIsDeletedFalse("#a1a1a1")).isTrue();
        assertThat(serviceCategoryRepo.existsByColorAndIsDeletedFalse("#c3c3c3")).isFalse();
    }

    @Test
    void existsByColorAndIsDeletedFalseAndIdNot_excludesCurrentCategory() {
        assertThat(serviceCategoryRepo.existsByColorAndIsDeletedFalseAndIdNot("#a1a1a1", classic.getId())).isFalse();
        assertThat(serviceCategoryRepo.existsByColorAndIsDeletedFalseAndIdNot("#a1a1a1", empty.getId())).isTrue();
    }

    @Test
    void colorIndex_allowsColorOfHiddenCategoryForActiveCategory() {
        BaseServiceCategory reused = em.persistAndFlush(BaseServiceCategory.builder().name("SvcRepo Nowa").color("#c3c3c3").build());

        assertThat(reused.getId()).isNotNull();
    }

    @Test
    void colorIndex_rejectsDuplicateColorAmongActiveCategories() {
        assertThatThrownBy(() -> em.persistAndFlush(
                BaseServiceCategory.builder().name("SvcRepo Duplikat").color("#a1a1a1").build()))
                .isInstanceOf(PersistenceException.class);
    }
}

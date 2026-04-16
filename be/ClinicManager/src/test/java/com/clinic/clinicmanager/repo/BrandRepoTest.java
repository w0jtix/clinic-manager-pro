package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.Brand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class BrandRepoTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private BrandRepo brandRepo;

    @BeforeEach
    void setUp() {
        em.persistAndFlush(Brand.builder().name("Nike").build());
        em.persistAndFlush(Brand.builder().name("Adidas").build());
        em.persistAndFlush(Brand.builder().name("Puma").build());
    }

    @Test
    void findAllWithFilters_whenKeywordMatches_returnsMatchingBrands() {
        List<Brand> result = brandRepo.findAllWithFilters("Ni");

        assertThat(result).hasSize(1);
        assertThat(result).extracting(Brand::getName).containsExactlyInAnyOrder("Nike");
    }

    @Test
    void findAllWithFilters_whenKeywordEmpty_returnsAllBrands() {
        List<Brand> result = brandRepo.findAllWithFilters("");

        assertThat(result).hasSize(3);
    }

    @Test
    void findAllWithFilters_whenKeywordNull_returnsAllBrands() {
        List<Brand> result = brandRepo.findAllWithFilters(null);

        assertThat(result).hasSize(3);
    }

    @Test
    void findAllWithFilters_whenKeywordCaseInsensitive_returnsMatch() {
        List<Brand> result = brandRepo.findAllWithFilters("ni");

        assertThat(result).hasSize(1);
        assertThat(result).extracting(Brand::getName).containsExactly("Nike");
    }

    @Test
    void findAllWithFilters_whenNoMatch_returnsEmptyList() {
        List<Brand> result = brandRepo.findAllWithFilters("xyz");

        assertThat(result).isEmpty();
    }

    @Test
    void findByBrandName_whenNameExists_returnsOptionalWithBrand() {
        Optional<Brand> result = brandRepo.findByBrandName("Nike");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Nike");
    }

    @Test
    void findByBrandName_whenNameExistsWithDifferentCase_returnsOptionalWithBrand() {
        Optional<Brand> result = brandRepo.findByBrandName("nike");

        assertThat(result).isPresent();
    }

    @Test
    void findByBrandName_whenNameExistsWithWhitespace_returnsOptionalWithBrand() {
        Optional<Brand> result = brandRepo.findByBrandName("  Nike  ");

        assertThat(result).isPresent();
    }

    @Test
    void findByBrandName_whenNameNotFound_returnsEmptyOptional() {
        Optional<Brand> result = brandRepo.findByBrandName("NewBalance");

        assertThat(result).isEmpty();
    }
}

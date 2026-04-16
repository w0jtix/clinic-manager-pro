package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
public class SupplierRepoTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private SupplierRepo supplierRepo;

    @BeforeEach
    void setUp() {
        em.persistAndFlush(Supplier.builder().name("Supp1").build());
        em.persistAndFlush(Supplier.builder().name("Supp2").build());
        em.persistAndFlush(Supplier.builder().name("Supp3").build());
    }

    @Test
    void findBySupplierName_whenNameExists_returnsOptionalWithSupplier() {
        Optional<Supplier> result = supplierRepo.findBySupplierName("Supp1");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Supp1");
    }

    @Test
    void findBySupplierName_whenNameExistsWithDifferentCase_returnsOptionalWithSupplier() {
        Optional<Supplier> result = supplierRepo.findBySupplierName("SUPp1");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Supp1");
    }

    @Test
    void findBySupplierName_whenNameExistsWithWhitespace_returnsOptionalWithSupplier() {
        Optional<Supplier> result = supplierRepo.findBySupplierName("    Supp1     ");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Supp1");
    }

    @Test
    void findBySupplierName_whenNameNotFound_returnsEmptyOptional() {
        Optional<Supplier> result = supplierRepo.findBySupplierName("NotFound");

        assertThat(result).isEmpty();
    }
}

package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.DTO.DiscountDTO;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.Discount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DiscountRepoTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private DiscountRepo discountRepo;

    private Discount vip;
    private Discount standard;

    @BeforeEach
    void setUp() {
        vip      = em.persistAndFlush(Discount.builder().name("VIP10").percentageValue(10).build());
        standard = em.persistAndFlush(Discount.builder().name("STD05").percentageValue(5).build());
    }

    @Test
    void findAllWithClientCount_noClients_returnsZeroForAllDiscounts() {
        List<DiscountDTO> result = discountRepo.findAllWithClientCount();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DiscountDTO::getClientCount)
                .containsOnly(0L);
    }

    @Test
    void findAllWithClientCount_withClients_countsCorrectly() {
        em.persistAndFlush(Client.builder().firstName("A").lastName("A").discount(vip).build());
        em.persistAndFlush(Client.builder().firstName("B").lastName("B").discount(vip).build());
        em.persistAndFlush(Client.builder().firstName("C").lastName("C").discount(standard).build());

        List<DiscountDTO> result = discountRepo.findAllWithClientCount();

        assertThat(result)
                .extracting(DiscountDTO::getName, DiscountDTO::getClientCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("VIP10", 2L),
                        org.assertj.core.groups.Tuple.tuple("STD05", 1L)
                );
    }

    @Test
    void findAllWithClientCount_oneDiscountHasNoClients_returnsZeroForThat() {
        em.persistAndFlush(Client.builder().firstName("A").lastName("A").discount(vip).build());

        List<DiscountDTO> result = discountRepo.findAllWithClientCount();

        DiscountDTO vipDTO      = findByName(result, "VIP10");
        DiscountDTO standardDTO = findByName(result, "STD05");

        assertThat(vipDTO.getClientCount()).isEqualTo(1L);
        assertThat(standardDTO.getClientCount()).isEqualTo(0L);
    }

    private DiscountDTO findByName(List<DiscountDTO> list, String name) {
        return list.stream()
                .filter(d -> d.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Discount not found: " + name));
    }
}

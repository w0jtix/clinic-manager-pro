package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.support.AbstractPostgresTest;

import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.Voucher;
import com.clinic.clinicmanager.model.constants.VoucherStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class VoucherRepoTest extends AbstractPostgresTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private VoucherRepo voucherRepo;

    private Client client;

    @BeforeEach
    void setUp() {
        client = em.persistAndFlush(Client.builder().firstName("Jan").lastName("Kowalski").build());
    }

    private Voucher createVoucher(Client owner, String value, VoucherStatus status, LocalDate expiryDate) {
        return em.persistAndFlush(Voucher.builder()
                .client(owner)
                .value(new BigDecimal(value))
                .issueDate(LocalDate.of(2024, 1, 1))
                .expiryDate(expiryDate)
                .status(status)
                .build());
    }

    @Test
    void findAllByClientIdOrderByExpiryDateDesc_returnsClientVouchersNewestExpiryFirst() {
        createVoucher(client, "100.00", VoucherStatus.EXPIRED, LocalDate.of(2024, 6, 30));
        createVoucher(client, "300.00", VoucherStatus.ACTIVE, LocalDate.of(2026, 12, 31));
        createVoucher(client, "200.00", VoucherStatus.USED, LocalDate.of(2025, 3, 31));

        List<Voucher> result = voucherRepo.findAllByClientIdOrderByExpiryDateDesc(client.getId());

        assertThat(result).extracting(Voucher::getExpiryDate).containsExactly(
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2025, 3, 31),
                LocalDate.of(2024, 6, 30));
    }

    @Test
    void findAllByClientIdOrderByExpiryDateDesc_excludesOtherClientsVouchers() {
        Client other = em.persistAndFlush(Client.builder().firstName("Ewa").lastName("Nowak").build());
        createVoucher(client, "100.00", VoucherStatus.ACTIVE, LocalDate.of(2026, 1, 31));
        createVoucher(other, "500.00", VoucherStatus.ACTIVE, LocalDate.of(2026, 2, 28));

        List<Voucher> result = voucherRepo.findAllByClientIdOrderByExpiryDateDesc(client.getId());

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getValue()).isEqualByComparingTo("100.00");
    }

    @Test
    void findAllByClientIdOrderByExpiryDateDesc_returnsEmptyList_whenClientHasNoVouchers() {
        assertThat(voucherRepo.findAllByClientIdOrderByExpiryDateDesc(client.getId())).isEmpty();
    }
}

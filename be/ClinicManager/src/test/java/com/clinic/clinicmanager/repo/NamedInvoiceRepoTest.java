package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.NamedInvoice;
import com.clinic.clinicmanager.model.constants.PaymentMethod;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class NamedInvoiceRepoTest extends AbstractPostgresTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private NamedInvoiceRepo namedInvoiceRepo;

    private Client client;

    @BeforeEach
    void setUp() {
        client = em.persistAndFlush(Client.builder().firstName("Anna").lastName("Kowalska").build());
    }

    private NamedInvoice invoice(int sequence, PaymentMethod... methods) {
        return NamedInvoice.builder()
                .invoiceNumber(sequence + "/09/2026")
                .sequenceInMonth(sequence)
                .year(2026)
                .month(9)
                .issueDate(LocalDate.of(2026, 9, 9))
                .saleDate(LocalDate.of(2026, 9, 8))
                .paymentMethods(new LinkedHashSet<>(List.of(methods)))
                .client(client)
                .sourceVisitId(7L)
                .buyerName("Anna Kowalska")
                .netAmount(new BigDecimal("100.00"))
                .vatAmount(new BigDecimal("8.00"))
                .grossAmount(new BigDecimal("108.00"))
                .createdByUserId(1L)
                .build();
    }

    private long paymentMethodRows(Long invoiceId) {
        return ((Number) entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM named_invoice_payment_method WHERE named_invoice_id = :id")
                .setParameter("id", invoiceId)
                .getSingleResult()).longValue();
    }

    @Test
    void persistsAllPaymentMethods() {
        NamedInvoice saved = em.persistAndFlush(invoice(1, PaymentMethod.CARD, PaymentMethod.CASH));
        em.clear();

        NamedInvoice reloaded = namedInvoiceRepo.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getPaymentMethods())
                .containsExactlyInAnyOrder(PaymentMethod.CARD, PaymentMethod.CASH);
        assertThat(paymentMethodRows(saved.getId())).isEqualTo(2);
    }

    @Test
    void persistsEmptyPaymentMethods() {
        NamedInvoice saved = em.persistAndFlush(invoice(2));
        em.clear();

        NamedInvoice reloaded = namedInvoiceRepo.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getPaymentMethods()).isEmpty();
        assertThat(paymentMethodRows(saved.getId())).isZero();
    }

    @Test
    void deletingInvoiceRemovesPaymentMethodRows() {
        NamedInvoice saved = em.persistAndFlush(invoice(3, PaymentMethod.CARD, PaymentMethod.BLIK));
        Long id = saved.getId();
        assertThat(paymentMethodRows(id)).isEqualTo(2);

        namedInvoiceRepo.delete(saved);
        em.flush();
        em.clear();

        assertThat(namedInvoiceRepo.findById(id)).isEmpty();
        assertThat(paymentMethodRows(id)).isZero();
    }

    @Test
    void existsBySourceVisitId_returnsTrueForSavedInvoice_andFalseAfterDeletion() {
        NamedInvoice saved = em.persistAndFlush(invoice(4, PaymentMethod.CASH));

        assertThat(namedInvoiceRepo.existsBySourceVisitId(7L)).isTrue();
        assertThat(namedInvoiceRepo.existsBySourceVisitId(8L)).isFalse();

        namedInvoiceRepo.delete(saved);
        em.flush();
        em.clear();

        assertThat(namedInvoiceRepo.existsBySourceVisitId(7L)).isFalse();
    }
}

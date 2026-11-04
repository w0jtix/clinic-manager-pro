package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.support.AbstractPostgresTest;

import com.clinic.clinicmanager.model.KsefIssuedInvoice;
import com.clinic.clinicmanager.model.KsefIssuedInvoiceItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class KsefIssuedInvoiceRepoTest extends AbstractPostgresTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private KsefIssuedInvoiceRepo issuedInvoiceRepo;

    private KsefIssuedInvoice invoice(String ref, String buyerName, String invoiceNumber, LocalDate issueDate) {
        return em.persistAndFlush(KsefIssuedInvoice.builder()
                .ksefReferenceNumber(ref)
                .buyerName(buyerName)
                .buyerNip("0987654321")
                .invoiceNumber(invoiceNumber)
                .invoiceType("VAT")
                .issueDate(issueDate)
                .netAmount(new BigDecimal("100.00"))
                .vatAmount(new BigDecimal("23.00"))
                .grossAmount(new BigDecimal("123.00"))
                .currency("PLN")
                .build());
    }

    @Test
    void findAllWithFilters_includesFirstAndLastDayOfMonth_andExcludesDaysOutsideRange() {
        invoice("REF-JAN-31", "Buyer", "FS/1", LocalDate.of(2026, 1, 31));
        invoice("REF-FEB-01", "Buyer", "FS/2", LocalDate.of(2026, 2, 1));
        invoice("REF-FEB-28", "Buyer", "FS/3", LocalDate.of(2026, 2, 28));
        invoice("REF-MAR-01", "Buyer", "FS/4", LocalDate.of(2026, 3, 1));

        Page<KsefIssuedInvoice> result = issuedInvoiceRepo.findAllWithFilters(
                null, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(KsefIssuedInvoice::getKsefReferenceNumber)
                .containsExactlyInAnyOrder("REF-FEB-01", "REF-FEB-28");
    }

    @Test
    void findAllWithFilters_keywordMatchesBuyerNameAndInvoiceNumber_caseInsensitively() {
        invoice("REF-1", "ACME Corp", "FS/2026/1", LocalDate.of(2026, 2, 10));
        invoice("REF-2", "Other Company", "FS/acme/2026/2", LocalDate.of(2026, 2, 11));
        invoice("REF-3", "Unrelated", "FS/2026/3", LocalDate.of(2026, 2, 12));

        Page<KsefIssuedInvoice> result = issuedInvoiceRepo.findAllWithFilters(
                "acme", null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(KsefIssuedInvoice::getKsefReferenceNumber)
                .containsExactlyInAnyOrder("REF-1", "REF-2");
    }

    @Test
    void countWithFilters_matchesTotalElementsOfFindAllWithFilters_forSamePredicates() {
        invoice("REF-1", "ACME Corp", "FS/1", LocalDate.of(2026, 2, 1));
        invoice("REF-2", "ACME Corp", "FS/2", LocalDate.of(2026, 2, 15));
        invoice("REF-3", "ACME Corp", "FS/3", LocalDate.of(2026, 2, 28));
        invoice("REF-4", "ACME Corp", "FS/4", LocalDate.of(2026, 3, 1));
        invoice("REF-5", "Other", "FS/5", LocalDate.of(2026, 2, 10));

        long count = issuedInvoiceRepo.countWithFilters(
                "acme", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        Page<KsefIssuedInvoice> page = issuedInvoiceRepo.findAllWithFilters(
                "acme", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), PageRequest.of(0, 10));

        assertThat(count).isEqualTo(3L);
        assertThat(count).isEqualTo(page.getTotalElements());
    }

    @Test
    void findAllWithFilters_ordersByIssueDateDescThenIdDesc() {
        KsefIssuedInvoice earliest = invoice("REF-A", "Buyer", "FS/A", LocalDate.of(2026, 4, 1));
        KsefIssuedInvoice sameDayLowerId = invoice("REF-B", "Buyer", "FS/B", LocalDate.of(2026, 5, 10));
        KsefIssuedInvoice sameDayHigherId = invoice("REF-C", "Buyer", "FS/C", LocalDate.of(2026, 5, 10));
        KsefIssuedInvoice latest = invoice("REF-D", "Buyer", "FS/D", LocalDate.of(2026, 6, 1));

        Page<KsefIssuedInvoice> result = issuedInvoiceRepo.findAllWithFilters(
                null, null, null, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(KsefIssuedInvoice::getId)
                .containsExactly(latest.getId(), sameDayHigherId.getId(), sameDayLowerId.getId(), earliest.getId());
    }

    @Test
    void findAllWithFilters_withNullBoundsAndNullKeyword_returnsEverything() {
        invoice("REF-1", "Buyer", "FS/1", LocalDate.of(2025, 12, 31));
        invoice("REF-2", "Buyer", "FS/2", LocalDate.of(2026, 7, 15));

        Page<KsefIssuedInvoice> result = issuedInvoiceRepo.findAllWithFilters(
                null, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2L);
    }

    @Test
    void lazyItemsCollection_returnsItemsInIdOrder_evenAfterRowUpdate() {
        KsefIssuedInvoice invoice = KsefIssuedInvoice.builder()
                .ksefReferenceNumber("REF-1")
                .invoiceType("VAT")
                .issueDate(LocalDate.of(2026, 2, 10))
                .build();
        for (String name : List.of("First", "Second", "Third")) {
            invoice.addItem(KsefIssuedInvoiceItem.builder().ksefItemName(name).build());
        }
        KsefIssuedInvoice saved = em.persistAndFlush(invoice);
        KsefIssuedInvoice temporaryOwner = invoice("REF-TMP", "Buyer", "FS/TMP", LocalDate.of(2026, 2, 11));
        Long firstItemId = saved.getItems().get(0).getId();
        for (KsefIssuedInvoice target : List.of(temporaryOwner, saved)) {
            em.getEntityManager()
                    .createQuery("UPDATE KsefIssuedInvoiceItem it SET it.invoice = :target WHERE it.id = :id")
                    .setParameter("target", target)
                    .setParameter("id", firstItemId)
                    .executeUpdate();
        }
        em.clear();

        KsefIssuedInvoice loaded = issuedInvoiceRepo.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getItems()).extracting(KsefIssuedInvoiceItem::getKsefItemName)
                .containsExactly("First", "Second", "Third");
    }
}

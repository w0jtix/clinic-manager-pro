package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.KsefInvoice;
import com.clinic.clinicmanager.model.KsefInvoiceItem;
import com.clinic.clinicmanager.model.KsefSeller;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class KsefInvoiceItemRepoTest extends AbstractPostgresTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private KsefInvoiceItemRepo itemRepo;

    @Autowired
    private KsefInvoiceRepo invoiceRepo;

    private KsefInvoice invoice(String ref, KsefSeller seller, KsefInvoiceItem... items) {
        KsefInvoice invoice = KsefInvoice.builder()
                .ksefReferenceNumber(ref)
                .invoiceType("VAT")
                .seller(seller)
                .build();
        for (KsefInvoiceItem item : items) {
            invoice.addItem(item);
        }
        return em.persistAndFlush(invoice);
    }

    private KsefInvoiceItem item(String name, Integer lineNumber) {
        return KsefInvoiceItem.builder()
                .ksefItemName(name)
                .quantity(BigDecimal.ONE)
                .lineNumber(lineNumber)
                .build();
    }

    private KsefSeller seller() {
        return em.persistAndFlush(KsefSeller.builder().nip("1234567890").name("Seller").build());
    }

    @Test
    void findInvoiceIdsWithMissingLineNumber_returnsDistinctInvoicesHavingAnyNullLine() {
        KsefSeller seller = seller();
        KsefInvoice missingTwo = invoice("REF-1", seller, item("A", null), item("B", null));
        KsefInvoice mixed = invoice("REF-2", seller, item("A", 1), item("B", null));
        invoice("REF-3", seller, item("A", 1), item("B", 2));

        List<Long> ids = itemRepo.findInvoiceIdsWithMissingLineNumber();

        assertThat(ids).containsExactlyInAnyOrder(missingTwo.getId(), mixed.getId());
    }

    @Test
    void findByInvoiceIdOrderByIdAsc_returnsItemsInInsertionOrder() {
        KsefInvoice invoice = invoice("REF-1", seller(), item("First", null), item("Second", null), item("Third", null));

        List<KsefInvoiceItem> items = itemRepo.findByInvoiceIdOrderByIdAsc(invoice.getId());

        assertThat(items).extracting(KsefInvoiceItem::getKsefItemName)
                .containsExactly("First", "Second", "Third");
    }

    private void rewriteFirstItemAsNewTuple(KsefInvoice owner, KsefInvoice temporaryOwner) {
        Long itemId = owner.getItems().get(0).getId();
        for (KsefInvoice target : List.of(temporaryOwner, owner)) {
            em.getEntityManager()
                    .createQuery("UPDATE KsefInvoiceItem it SET it.invoice = :target WHERE it.id = :id")
                    .setParameter("target", target)
                    .setParameter("id", itemId)
                    .executeUpdate();
        }
        em.clear();
    }

    @Test
    void findByKsefReferenceNumberWithItems_returnsItemsInIdOrder_evenAfterRowUpdate() {
        KsefSeller seller = seller();
        KsefInvoice invoice = invoice("REF-1", seller, item("First", null), item("Second", null), item("Third", null));
        rewriteFirstItemAsNewTuple(invoice, invoice("REF-TMP", seller));

        KsefInvoice loaded = invoiceRepo.findByKsefReferenceNumberWithItems("REF-1").orElseThrow();

        assertThat(loaded.getItems()).extracting(KsefInvoiceItem::getKsefItemName)
                .containsExactly("First", "Second", "Third");
    }

    @Test
    void lazyItemsCollection_returnsItemsInIdOrder_evenAfterRowUpdate() {
        KsefSeller seller = seller();
        KsefInvoice invoice = invoice("REF-1", seller, item("First", null), item("Second", null), item("Third", null));
        rewriteFirstItemAsNewTuple(invoice, invoice("REF-TMP", seller));

        KsefInvoice loaded = invoiceRepo.findById(invoice.getId()).orElseThrow();

        assertThat(loaded.getItems()).extracting(KsefInvoiceItem::getKsefItemName)
                .containsExactly("First", "Second", "Third");
    }

    @Test
    void findByCorrectedInvoiceId_returnsItemsInIdOrder_evenAfterRowUpdate() {
        KsefSeller seller = seller();
        KsefInvoice parent = invoice("REF-1", seller, item("Parent", 1));
        KsefInvoice correction = KsefInvoice.builder()
                .ksefReferenceNumber("REF-KOR")
                .invoiceType("KOR")
                .seller(seller)
                .correctedInvoiceId(parent.getId())
                .build();
        correction.addItem(item("First", null));
        correction.addItem(item("Second", null));
        correction.addItem(item("Third", null));
        em.persistAndFlush(correction);
        rewriteFirstItemAsNewTuple(correction, invoice("REF-TMP", seller));

        List<KsefInvoice> loaded = invoiceRepo.findByCorrectedInvoiceId(parent.getId());

        assertThat(loaded).singleElement()
                .satisfies(kor -> assertThat(kor.getItems()).extracting(KsefInvoiceItem::getKsefItemName)
                        .containsExactly("First", "Second", "Third"));
    }

    @Test
    void updateLineIfMissing_setsLine_onlyWhenLineNumberStillNull() {
        KsefInvoice invoice = invoice("REF-1", seller(), item("Empty", null), item("Filled", 7));
        Long emptyId = invoice.getItems().get(0).getId();
        Long filledId = invoice.getItems().get(1).getId();

        int updatedEmpty = itemRepo.updateLineIfMissing(emptyId, 3, "uuid-3");
        int updatedFilled = itemRepo.updateLineIfMissing(filledId, 9, "uuid-9");
        em.clear();

        assertThat(updatedEmpty).isEqualTo(1);
        assertThat(updatedFilled).isZero();
        KsefInvoiceItem empty = em.find(KsefInvoiceItem.class, emptyId);
        KsefInvoiceItem filled = em.find(KsefInvoiceItem.class, filledId);
        assertThat(empty.getLineNumber()).isEqualTo(3);
        assertThat(empty.getLineUuid()).isEqualTo("uuid-3");
        assertThat(filled.getLineNumber()).isEqualTo(7);
        assertThat(filled.getLineUuid()).isNull();
    }
}

package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.support.AbstractPostgresTest;

import com.clinic.clinicmanager.model.CompanyExpense;
import com.clinic.clinicmanager.model.KsefInvoice;
import com.clinic.clinicmanager.model.KsefSeller;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;
import com.clinic.clinicmanager.repo.projection.CompanyExpenseProjection;
import com.clinic.clinicmanager.repo.projection.UnifiedCategoryExpenseProjection;
import com.clinic.clinicmanager.repo.projection.UnifiedExpenseProjection;
import org.junit.jupiter.api.BeforeEach;
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
import static org.assertj.core.api.Assertions.tuple;

/**
 * Verifies the global, paging-stable ordering of {@code findUnifiedWithFilters}:
 * null-category rows first, then MANUAL (categorized), then KSEF (categorized);
 * within each group sorted by expenseDate descending.
 * <p>
 * The {@code expense_unified} view comes from the Liquibase changelog, the same
 * definition that runs in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CompanyExpenseUnifiedOrderRepoTest extends AbstractPostgresTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CompanyExpenseRepo expenseRepo;

    private int nipCounter;

    private String nextNip() {
        return String.format("%010d", ++nipCounter);
    }

    @BeforeEach
    void clearTables() {
        nipCounter = 0;
        em.getEntityManager().createNativeQuery("DELETE FROM ksef_invoice").executeUpdate();
        em.getEntityManager().createNativeQuery("DELETE FROM company_expense").executeUpdate();
        em.getEntityManager().createNativeQuery("DELETE FROM ksef_seller").executeUpdate();
    }

    @Test
    void findUnifiedWithFilters_ordersByGroupThenDateDesc() {
        // group 0 (category IS NULL) - only KSeF invoices can be uncategorized
        // (company_expense.category is NOT NULL); ordered by date desc
        ksefInvoice("REF-B1", null, LocalDate.of(2099, 6, 20)); // B1 (newer)
        ksefInvoice("REF-B2", null, LocalDate.of(2099, 6, 10)); // B2

        // group 1 (MANUAL, categorized) - ordered by date desc
        manualExpense(ExpenseCategoryCode.RENT, LocalDate.of(2099, 6, 15)); // C
        manualExpense(ExpenseCategoryCode.TAXES, LocalDate.of(2099, 6, 25)); // D (newer)

        // group 2 (KSEF, categorized) - ordered by date desc
        ksefInvoice("REF-E", ExpenseCategoryCode.ZUS, LocalDate.of(2099, 6, 5));  // E
        ksefInvoice("REF-F", ExpenseCategoryCode.RENT, LocalDate.of(2099, 6, 12)); // F (newer)

        em.flush();

        Page<UnifiedExpenseProjection> page = expenseRepo.findUnifiedWithFilters(
                LocalDate.of(2099, 6, 1),
                LocalDate.of(2099, 6, 30),
                List.of(""),
                0,
                PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(UnifiedExpenseProjection::getSourceType,
                        UnifiedExpenseProjection::getCategory,
                        UnifiedExpenseProjection::getExpenseDate)
                .containsExactly(
                        tuple("KSEF", null, LocalDate.of(2099, 6, 20)),     // B1 - null group, newest
                        tuple("KSEF", null, LocalDate.of(2099, 6, 10)),     // B2 - null group
                        tuple("MANUAL", "TAXES", LocalDate.of(2099, 6, 25)), // D - MANUAL group, newest
                        tuple("MANUAL", "RENT", LocalDate.of(2099, 6, 15)), // C - MANUAL group
                        tuple("KSEF", "RENT", LocalDate.of(2099, 6, 12)),   // F - KSEF group, newest
                        tuple("KSEF", "ZUS", LocalDate.of(2099, 6, 5))      // E - KSEF group
                );
    }

    @Test
    void findUnifiedWithFilters_exposesAwaitingProductVerification() {
        manualExpense(ExpenseCategoryCode.RENT, LocalDate.of(2099, 7, 10));
        ksefInvoice("REF-REG", "OTHER", LocalDate.of(2099, 7, 11), false);
        ksefInvoice("REF-PEND", "OTHER", LocalDate.of(2099, 7, 12), true);
        ksefInvoice("REF-PROD", ExpenseCategoryCode.PRODUCTS, LocalDate.of(2099, 7, 13), false);

        em.flush();

        Page<UnifiedExpenseProjection> page = expenseRepo.findUnifiedWithFilters(
                LocalDate.of(2099, 7, 1),
                LocalDate.of(2099, 7, 31),
                List.of(""),
                0,
                PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(UnifiedExpenseProjection::getSource,
                        UnifiedExpenseProjection::getSourceType,
                        UnifiedExpenseProjection::getAwaitingProductVerification)
                .containsExactlyInAnyOrder(
                        tuple("Manual " + LocalDate.of(2099, 7, 10), "MANUAL", false),
                        tuple("Seller REF-REG", "KSEF", false),
                        tuple("Seller REF-PEND", "KSEF", true),
                        tuple("Seller REF-PROD", "KSEF", false)
                );
    }

    @Test
    void findUnifiedWithFilters_exposesSellerDefaultExpenseCategory() {
        manualExpense(ExpenseCategoryCode.RENT, LocalDate.of(2099, 8, 10));
        ksefInvoiceWithSellerDefault("REF-DEF", "OTHER",
                LocalDate.of(2099, 8, 11), "SERVICES");
        ksefInvoiceWithSellerDefault("REF-NONE", "OTHER",
                LocalDate.of(2099, 8, 12), null);

        em.flush();

        Page<UnifiedExpenseProjection> page = expenseRepo.findUnifiedWithFilters(
                LocalDate.of(2099, 8, 1),
                LocalDate.of(2099, 8, 31),
                List.of(""),
                0,
                PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(UnifiedExpenseProjection::getSource,
                        UnifiedExpenseProjection::getSourceType,
                        UnifiedExpenseProjection::getSellerDefaultExpenseCategory)
                .containsExactlyInAnyOrder(
                        tuple("Manual " + LocalDate.of(2099, 8, 10), "MANUAL", null),
                        tuple("Seller REF-DEF", "KSEF", "SERVICES"),
                        tuple("Seller REF-NONE", "KSEF", null)
                );
    }

    @Test
    void findUnifiedWithFilters_exposesSellerIdForKsefRowsAndNullForManual() {
        manualExpense(ExpenseCategoryCode.RENT, LocalDate.of(2099, 9, 10));
        Long sellerId = ksefInvoice("REF-SID", "OTHER", LocalDate.of(2099, 9, 11));

        em.flush();

        Long persistedSellerId = (Long) em.getEntityManager()
                .createNativeQuery("SELECT seller_id FROM ksef_invoice WHERE ksef_reference_number = 'REF-SID'")
                .getSingleResult();
        assertThat(persistedSellerId).isEqualTo(sellerId);

        Page<UnifiedExpenseProjection> page = expenseRepo.findUnifiedWithFilters(
                LocalDate.of(2099, 9, 1),
                LocalDate.of(2099, 9, 30),
                List.of(""),
                0,
                PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(UnifiedExpenseProjection::getSourceType,
                        UnifiedExpenseProjection::getSellerId)
                .containsExactlyInAnyOrder(
                        tuple("MANUAL", null),
                        tuple("KSEF", persistedSellerId)
                );
    }

    @Test
    void sumUnifiedTotalExpenses_excludesUnrelatedAndUncategorized() {
        manualExpense(ExpenseCategoryCode.RENT, LocalDate.of(2099, 6, 15));
        manualExpense(ExpenseCategoryCode.UNRELATED, LocalDate.of(2099, 6, 16));
        ksefInvoice("REF-UNREL", ExpenseCategoryCode.UNRELATED, LocalDate.of(2099, 6, 17));
        ksefInvoice("REF-NULL", null, LocalDate.of(2099, 6, 18));

        em.flush();

        Double total = expenseRepo.sumUnifiedTotalExpenses(
                LocalDate.of(2099, 6, 1),
                LocalDate.of(2099, 6, 30));

        assertThat(total).isEqualTo(123.0);
    }

    @Test
    void findUnifiedExpensesByCategory_reportsUnrelatedButNotUncategorized() {
        manualExpense(ExpenseCategoryCode.RENT, LocalDate.of(2099, 6, 15));
        manualExpense(ExpenseCategoryCode.UNRELATED, LocalDate.of(2099, 6, 16));
        ksefInvoice("REF-NULL", null, LocalDate.of(2099, 6, 17));

        em.flush();

        List<UnifiedCategoryExpenseProjection> result = expenseRepo.findUnifiedExpensesByCategory(
                LocalDate.of(2099, 6, 1),
                LocalDate.of(2099, 6, 30));

        assertThat(result)
                .extracting(UnifiedCategoryExpenseProjection::getCategory)
                .containsExactlyInAnyOrder("RENT", "UNRELATED");
    }

    @Test
    void findUnifiedMonthlyExpensesByYear_excludesUnrelated() {
        manualExpense(ExpenseCategoryCode.RENT, LocalDate.of(2099, 6, 15));
        manualExpense(ExpenseCategoryCode.UNRELATED, LocalDate.of(2099, 6, 16));

        em.flush();

        List<CompanyExpenseProjection> result =
                expenseRepo.findUnifiedMonthlyExpensesByYear(2099);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPeriod()).isEqualTo(6);
        assertThat(result.get(0).getTotalExpense()).isEqualByComparingTo("123.00");
    }

    @Test
    void findUnifiedDailyExpensesByYearAndMonth_excludesUnrelated() {
        manualExpense(ExpenseCategoryCode.RENT, LocalDate.of(2099, 6, 15));
        manualExpense(ExpenseCategoryCode.UNRELATED, LocalDate.of(2099, 6, 15));

        em.flush();

        List<CompanyExpenseProjection> result =
                expenseRepo.findUnifiedDailyExpensesByYearAndMonth(2099, 6);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPeriod()).isEqualTo(15);
        assertThat(result.get(0).getTotalExpense()).isEqualByComparingTo("123.00");
    }

    @Test
    void findUnifiedWithFilters_stillReturnsUnrelated() {
        manualExpense(ExpenseCategoryCode.RENT, LocalDate.of(2099, 6, 15));
        manualExpense(ExpenseCategoryCode.UNRELATED, LocalDate.of(2099, 6, 16));

        em.flush();

        Page<UnifiedExpenseProjection> page = expenseRepo.findUnifiedWithFilters(
                LocalDate.of(2099, 6, 1),
                LocalDate.of(2099, 6, 30),
                List.of(""),
                0,
                PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(UnifiedExpenseProjection::getCategory)
                .containsExactlyInAnyOrder("RENT", "UNRELATED");
    }

    private void manualExpense(String category, LocalDate date) {
        em.persist(CompanyExpense.builder()
                .source("Manual " + date)
                .category(category)
                .expenseDate(date)
                .totalNet(BigDecimal.valueOf(100.0))
                .totalVat(BigDecimal.valueOf(23.0))
                .totalValue(BigDecimal.valueOf(123.0))
                .build());
    }

    private Long ksefInvoice(String ref, String category, LocalDate issueDate) {
        return ksefInvoice(ref, category, issueDate, false);
    }

    private Long ksefInvoice(String ref, String category, LocalDate issueDate,
                             boolean awaitingProductVerification) {
        KsefSeller seller = KsefSeller.builder()
                .nip(nextNip())
                .name("Seller " + ref)
                .build();
        em.persist(seller);

        em.persist(KsefInvoice.builder()
                .ksefReferenceNumber(ref)
                .seller(seller)
                .issueDate(issueDate)
                .category(category)
                .awaitingProductVerification(awaitingProductVerification)
                .netAmount(BigDecimal.valueOf(100))
                .vatAmount(BigDecimal.valueOf(23))
                .grossAmount(BigDecimal.valueOf(123))
                .build());

        return seller.getId();
    }

    private void ksefInvoiceWithSellerDefault(String ref, String category,
                                              LocalDate issueDate,
                                              String sellerDefaultCategory) {
        KsefSeller seller = KsefSeller.builder()
                .nip(nextNip())
                .name("Seller " + ref)
                .defaultExpenseCategory(sellerDefaultCategory)
                .build();
        em.persist(seller);

        em.persist(KsefInvoice.builder()
                .ksefReferenceNumber(ref)
                .seller(seller)
                .issueDate(issueDate)
                .category(category)
                .awaitingProductVerification(false)
                .netAmount(BigDecimal.valueOf(100))
                .vatAmount(BigDecimal.valueOf(23))
                .grossAmount(BigDecimal.valueOf(123))
                .build());
    }

    @Test
    void findUnifiedWithFilters_exposesSellerIdForManualRowLinkedToSeller() {
        KsefSeller seller = KsefSeller.builder().nip(nextNip()).name("Manual seller").build();
        em.persist(seller);
        em.persist(CompanyExpense.builder()
                .source("Manual seller")
                .category("OTHER")
                .expenseDate(LocalDate.of(2099, 10, 10))
                .seller(seller)
                .totalNet(BigDecimal.valueOf(100.0))
                .totalVat(BigDecimal.valueOf(23.0))
                .totalValue(BigDecimal.valueOf(123.0))
                .build());
        manualExpense(ExpenseCategoryCode.RENT, LocalDate.of(2099, 10, 11));

        em.flush();

        Page<UnifiedExpenseProjection> page = expenseRepo.findUnifiedWithFilters(
                LocalDate.of(2099, 10, 1),
                LocalDate.of(2099, 10, 31),
                List.of(""),
                0,
                PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(UnifiedExpenseProjection::getSource,
                        UnifiedExpenseProjection::getSourceType,
                        UnifiedExpenseProjection::getSellerId)
                .containsExactlyInAnyOrder(
                        tuple("Manual seller", "MANUAL", seller.getId()),
                        tuple("Manual " + LocalDate.of(2099, 10, 11), "MANUAL", null)
                );
    }
}

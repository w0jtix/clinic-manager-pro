package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.support.AbstractPostgresTest;

import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.model.CompanyExpense;
import com.clinic.clinicmanager.model.CompanyExpenseItem;
import com.clinic.clinicmanager.model.KsefInvoice;
import com.clinic.clinicmanager.model.KsefSeller;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;
import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.projection.CompanyExpenseProjection;
import com.clinic.clinicmanager.repo.projection.ProductPurchaseProjection;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CompanyExpenseMixedRepoTest extends AbstractPostgresTest {

    private static final LocalDate DATE = LocalDate.of(2099, 6, 15);
    private static final LocalDate FROM = LocalDate.of(2099, 6, 1);
    private static final LocalDate TO = LocalDate.of(2099, 6, 30);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CompanyExpenseRepo expenseRepo;

    @Autowired
    private ProductRepo productRepo;

    private Product product;

    @BeforeEach
    void setUp() {
        em.getEntityManager().createNativeQuery("DELETE FROM ksef_invoice").executeUpdate();
        em.getEntityManager().createNativeQuery("DELETE FROM company_expense_item").executeUpdate();
        em.getEntityManager().createNativeQuery("DELETE FROM company_expense").executeUpdate();
        em.getEntityManager().createNativeQuery("DELETE FROM ksef_seller").executeUpdate();

        ProductCategory productCategory = em.persistAndFlush(ProductCategory.builder()
                .name("Mixed test category").color("#FF0000").build());
        Brand brand = em.persistAndFlush(Brand.builder().name("Mixed test brand").build());
        product = em.persistAndFlush(Product.builder()
                .name("Mixed test product").category(productCategory).brand(brand).supply(0).build());
    }

    @Test
    void statistics_splitMixedExpenseIntoItems_andDropUnrelatedItems() {
        manualExpense(ExpenseCategoryCode.RENT, DATE, item(null, "123.00", 1, VatRate.VAT_23, null));
        mixedExpense(DATE,
                item("OTHER", "100.00", 1, VatRate.VAT_23, null),
                item(ExpenseCategoryCode.UNRELATED, "40.00", 2, VatRate.VAT_23, null),
                item(ExpenseCategoryCode.PRODUCTS, "10.00", 3, VatRate.VAT_8, product));
        ksefInvoice("REF-RENT", ExpenseCategoryCode.RENT, DATE);
        em.flush();

        assertThat(expenseRepo.sumUnifiedTotalExpenses(FROM, TO)).isEqualTo(376.0);

        List<UnifiedCategoryExpenseProjection> byCategory = expenseRepo.findUnifiedExpensesByCategory(FROM, TO);
        assertThat(byCategory)
                .extracting(UnifiedCategoryExpenseProjection::getCategory,
                        p -> p.getTotalAmount().setScale(2))
                .containsExactly(
                        tuple("RENT", new BigDecimal("246.00")),
                        tuple("OTHER", new BigDecimal("100.00")),
                        tuple("UNRELATED", new BigDecimal("80.00")),
                        tuple("PRODUCTS", new BigDecimal("30.00")));

        List<CompanyExpenseProjection> monthly = expenseRepo.findUnifiedMonthlyExpensesByYear(2099);
        assertThat(monthly).hasSize(1);
        assertThat(monthly.get(0).getTotalExpense()).isEqualByComparingTo("376.00");

        List<CompanyExpenseProjection> daily = expenseRepo.findUnifiedDailyExpensesByYearAndMonth(2099, 6);
        assertThat(daily).hasSize(1);
        assertThat(daily.get(0).getPeriod()).isEqualTo(15);
        assertThat(daily.get(0).getTotalExpense()).isEqualByComparingTo("376.00");
    }

    @Test
    void expenseCategoryAmount_neverContainsMixed_andComputesItemNetVatGross() {
        CompanyExpense mixed = mixedExpense(DATE,
                item("SUPPLIES", "100.00", 1, VatRate.VAT_23, null),
                item(ExpenseCategoryCode.PRODUCTS, "10.00", 3, VatRate.VAT_8, product),
                item("CAR", "40.00", 1, VatRate.VAT_ZW, null));
        em.flush();

        Long mixedRows = ((Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM expense_category_amount WHERE category = 'MIXED'")
                .getSingleResult()).longValue();
        assertThat(mixedRows).isZero();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.getEntityManager().createNativeQuery("""
                SELECT category, total_net, total_vat, total_value
                FROM expense_category_amount
                WHERE source_type = 'MANUAL' AND source_id = :id
                ORDER BY category
                """)
                .setParameter("id", mixed.getId())
                .getResultList();

        assertThat(rows)
                .extracting(r -> r[0], r -> ((BigDecimal) r[1]).toPlainString(),
                        r -> ((BigDecimal) r[2]).toPlainString(), r -> ((BigDecimal) r[3]).toPlainString())
                .containsExactly(
                        tuple("CAR", "40.00", "0.00", "40.00"),
                        tuple("PRODUCTS", "27.78", "2.22", "30.00"),
                        tuple("SUPPLIES", "81.30", "18.70", "100.00"));
    }

    @Test
    void findUnifiedWithFilters_matchesMixedByItemCategory_andCountsIt() {
        mixedExpense(DATE,
                item("CAR", "100.00", 1, VatRate.VAT_23, null),
                item("SUPPLIES", "50.00", 1, VatRate.VAT_23, null));
        mixedExpense(DATE.plusDays(1),
                item("OTHER", "100.00", 1, VatRate.VAT_23, null),
                item("SUPPLIES", "50.00", 1, VatRate.VAT_23, null));
        manualExpense("CAR", DATE.plusDays(2), item(null, "10.00", 1, VatRate.VAT_23, null));
        manualExpense(ExpenseCategoryCode.RENT, DATE.plusDays(3), item(null, "10.00", 1, VatRate.VAT_23, null));
        em.flush();

        Page<UnifiedExpenseProjection> firstPage = expenseRepo.findUnifiedWithFilters(
                FROM, TO, List.of("CAR"), 1, PageRequest.of(0, 1));

        assertThat(firstPage.getTotalElements()).isEqualTo(2);

        Page<UnifiedExpenseProjection> all = expenseRepo.findUnifiedWithFilters(
                FROM, TO, List.of("CAR"), 1, PageRequest.of(0, 50));

        assertThat(all.getContent())
                .extracting(UnifiedExpenseProjection::getCategory, UnifiedExpenseProjection::getExpenseDate)
                .containsExactly(
                        tuple("CAR", DATE.plusDays(2)),
                        tuple("MIXED", DATE));
    }

    @Test
    void findUnifiedWithFilters_matchesMixedHeaderCategory() {
        mixedExpense(DATE,
                item("CAR", "100.00", 1, VatRate.VAT_23, null),
                item("SUPPLIES", "50.00", 1, VatRate.VAT_23, null));
        manualExpense(ExpenseCategoryCode.RENT, DATE, item(null, "10.00", 1, VatRate.VAT_23, null));
        em.flush();

        Page<UnifiedExpenseProjection> page = expenseRepo.findUnifiedWithFilters(
                FROM, TO, List.of("MIXED"), 1, PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(UnifiedExpenseProjection::getCategory)
                .containsExactly("MIXED");
    }

    @Test
    void findUnifiedWithFilters_exposesDistinctSortedItemCategoriesOnlyForMixed() {
        mixedExpense(DATE,
                item("SUPPLIES", "50.00", 1, VatRate.VAT_23, null),
                item("CAR", "100.00", 1, VatRate.VAT_23, null),
                item("SUPPLIES", "20.00", 1, VatRate.VAT_23, null));
        manualExpense(ExpenseCategoryCode.RENT, DATE.plusDays(1), item(null, "10.00", 1, VatRate.VAT_23, null));
        ksefInvoice("REF-K", "OTHER", DATE);
        em.flush();

        Page<UnifiedExpenseProjection> page = expenseRepo.findUnifiedWithFilters(
                FROM, TO, List.of(""), 0, PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(UnifiedExpenseProjection::getCategory, UnifiedExpenseProjection::getItemCategories)
                .containsExactlyInAnyOrder(
                        tuple("MIXED", "CAR,SUPPLIES"),
                        tuple("RENT", null),
                        tuple("OTHER", null));
    }

    @Test
    void productPurchase_includesProductItemOfMixedExpense() {
        mixedExpense(DATE,
                item(ExpenseCategoryCode.PRODUCTS, "10.00", 3, VatRate.VAT_8, product),
                item("CAR", "40.00", 1, VatRate.VAT_23, null));
        em.flush();

        List<ProductPurchaseProjection> purchases = productRepo.findAllPurchases(product.getId());

        assertThat(purchases)
                .extracting(ProductPurchaseProjection::getPurchaseDate,
                        ProductPurchaseProjection::getQuantity,
                        ProductPurchaseProjection::getVatRate)
                .containsExactly(tuple(DATE, 3, "VAT_8"));
        assertThat(purchases.get(0).getUnitGrossPrice()).isEqualByComparingTo("10.00");
    }

    private CompanyExpenseItem item(String category, String price, int quantity,
                                    VatRate vatRate, Product product) {
        return CompanyExpenseItem.builder()
                .name(category != null ? category : "Pozycja")
                .category(category)
                .price(new BigDecimal(price))
                .quantity(quantity)
                .vatRate(vatRate)
                .product(product)
                .build();
    }

    private CompanyExpense mixedExpense(LocalDate date, CompanyExpenseItem... items) {
        return manualExpense(ExpenseCategoryCode.MIXED, date, items);
    }

    private CompanyExpense manualExpense(String category, LocalDate date, CompanyExpenseItem... items) {
        CompanyExpense expense = CompanyExpense.builder()
                .source("Manual " + category + " " + date)
                .category(category)
                .expenseDate(date)
                .build();
        for (CompanyExpenseItem item : items) {
            expense.addExpenseItem(item);
        }
        expense.calculateTotals();
        em.persist(expense);
        return expense;
    }

    private void ksefInvoice(String ref, String category, LocalDate issueDate) {
        KsefSeller seller = KsefSeller.builder()
                .nip(String.format("%010d", Math.abs(ref.hashCode()) % 1_000_000_000L))
                .name("Seller " + ref)
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
    void productPurchase_convertsManualUnitGrossPriceToPln_whenEurExpense() {
        foreignExpense(ExpenseCategoryCode.PRODUCTS, DATE, ExpenseCurrency.EUR, "4.0",
                item(null, "10.00", 3, VatRate.VAT_23, product));
        em.flush();

        List<ProductPurchaseProjection> purchases = productRepo.findAllPurchases(product.getId());

        assertThat(purchases).hasSize(1);
        assertThat(purchases.get(0).getQuantity()).isEqualTo(3);
        assertThat(purchases.get(0).getUnitGrossPrice()).isEqualByComparingTo("40.00");
    }

    @Test
    void productPurchase_keepsManualUnitGrossPrice_whenPlnExpense() {
        manualExpense(ExpenseCategoryCode.PRODUCTS, DATE, item(null, "10.00", 3, VatRate.VAT_23, product));
        em.flush();

        List<ProductPurchaseProjection> purchases = productRepo.findAllPurchases(product.getId());

        assertThat(purchases.get(0).getUnitGrossPrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void expenseCategoryAmount_multipliesMixedItemAmountsByRate_whenEurExpense() {
        CompanyExpense mixed = foreignExpense(ExpenseCategoryCode.MIXED, DATE, ExpenseCurrency.EUR, "4.0",
                item("SUPPLIES", "100.00", 1, VatRate.VAT_23, null),
                item(ExpenseCategoryCode.PRODUCTS, "10.00", 3, VatRate.VAT_8, product),
                item("CAR", "40.00", 1, VatRate.VAT_ZW, null));
        em.flush();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.getEntityManager().createNativeQuery("""
                SELECT category, total_net, total_vat, total_value
                FROM expense_category_amount
                WHERE source_type = 'MANUAL' AND source_id = :id
                ORDER BY category
                """)
                .setParameter("id", mixed.getId())
                .getResultList();

        assertThat(rows)
                .extracting(r -> r[0], r -> ((BigDecimal) r[1]).toPlainString(),
                        r -> ((BigDecimal) r[2]).toPlainString(), r -> ((BigDecimal) r[3]).toPlainString())
                .containsExactly(
                        tuple("CAR", "160.00", "0.00", "160.00"),
                        tuple("PRODUCTS", "111.12", "8.88", "120.00"),
                        tuple("SUPPLIES", "325.20", "74.80", "400.00"));
        assertThat(mixed.getTotalValue()).isEqualByComparingTo("680.00");
        assertThat(expenseRepo.sumUnifiedTotalExpenses(FROM, TO)).isEqualTo(680.0);
    }

    @Test
    void expenseCategoryAmount_usesPlnHeaderTotals_whenNonMixedEurExpense() {
        CompanyExpense rent = foreignExpense(ExpenseCategoryCode.RENT, DATE, ExpenseCurrency.EUR, "4.2512",
                item(null, "100.00", 1, VatRate.VAT_23, null));
        em.flush();

        Object[] row = (Object[]) em.getEntityManager().createNativeQuery("""
                SELECT total_net, total_vat, total_value
                FROM expense_category_amount
                WHERE source_type = 'MANUAL' AND source_id = :id
                """)
                .setParameter("id", rent.getId())
                .getSingleResult();

        assertThat((BigDecimal) row[0]).isEqualByComparingTo("345.62");
        assertThat((BigDecimal) row[1]).isEqualByComparingTo("79.50");
        assertThat((BigDecimal) row[2]).isEqualByComparingTo("425.12");
    }

    @Test
    void findUnifiedWithFilters_exposesCurrencyAndCurrencyTotal() {
        foreignExpense(ExpenseCategoryCode.RENT, DATE, ExpenseCurrency.EUR, "4.2512",
                item(null, "100.00", 1, VatRate.VAT_23, null));
        manualExpense("CAR", DATE.plusDays(1), item(null, "10.00", 1, VatRate.VAT_23, null));
        ksefInvoice("REF-CUR", "OTHER", DATE);
        em.flush();

        Page<UnifiedExpenseProjection> page = expenseRepo.findUnifiedWithFilters(
                FROM, TO, List.of(""), 0, PageRequest.of(0, 50));

        assertThat(page.getContent())
                .extracting(UnifiedExpenseProjection::getCategory,
                        UnifiedExpenseProjection::getCurrency,
                        p -> p.getTotalValueCurrency() == null ? null : p.getTotalValueCurrency().toPlainString(),
                        p -> p.getTotalValue().toPlainString())
                .containsExactlyInAnyOrder(
                        tuple("RENT", "EUR", "100.00", "425.12"),
                        tuple("CAR", "PLN", null, "10.00"),
                        tuple("OTHER", "PLN", null, "123.00"));
    }

    private CompanyExpense foreignExpense(String category, LocalDate date, ExpenseCurrency currency, String rate,
                                          CompanyExpenseItem... items) {
        CompanyExpense expense = CompanyExpense.builder()
                .source("Foreign " + category + " " + date)
                .category(category)
                .expenseDate(date)
                .currency(currency)
                .build();
        expense.applyExchangeRate(new BigDecimal(rate), date.minusDays(1), "1/A/NBP/2099");
        for (CompanyExpenseItem item : items) {
            expense.addExpenseItem(item);
        }
        expense.calculateTotals();
        em.persist(expense);
        return expense;
    }
}

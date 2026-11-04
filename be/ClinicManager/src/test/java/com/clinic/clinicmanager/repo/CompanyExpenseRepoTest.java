package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.support.AbstractPostgresTest;

import com.clinic.clinicmanager.model.CompanyExpense;
import com.clinic.clinicmanager.model.CompanyExpenseItem;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.KsefSeller;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.projection.KsefMonthlySpendProjection;
import com.clinic.clinicmanager.repo.projection.SellerManualExpenseSummaryProjection;
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
class CompanyExpenseRepoTest extends AbstractPostgresTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CompanyExpenseRepo expenseRepo;

    @Test
    void findByCategoryAndEmployee_returnsOnlySalaryOfThatEmployee_orderedByDateAsc() {
        Employee anna = em.persistAndFlush(Employee.builder().name("Anna").lastName("Kowalska").drawingColor("#2563eb").build());
        Employee bea = em.persistAndFlush(Employee.builder().name("Bea").lastName("Nowak").drawingColor("#2563eb").build());

        em.persistAndFlush(CompanyExpense.builder()
                .source("Wypłata 07").category(ExpenseCategoryCode.SALARY).employee(anna)
                .expenseDate(LocalDate.of(2026, 8, 5)).totalValue(BigDecimal.valueOf(5000.0)).build());
        em.persistAndFlush(CompanyExpense.builder()
                .source("Wypłata 06").category(ExpenseCategoryCode.SALARY).employee(anna)
                .expenseDate(LocalDate.of(2026, 7, 5)).totalValue(BigDecimal.valueOf(4800.0)).build());
        em.persistAndFlush(CompanyExpense.builder()
                .source("Wypłata Bea").category(ExpenseCategoryCode.SALARY).employee(bea)
                .expenseDate(LocalDate.of(2026, 8, 5)).totalValue(BigDecimal.valueOf(4000.0)).build());
        em.persistAndFlush(CompanyExpense.builder()
                .source("Czynsz").category(ExpenseCategoryCode.RENT).employee(anna)
                .expenseDate(LocalDate.of(2026, 8, 1)).totalValue(BigDecimal.valueOf(2000.0)).build());

        List<CompanyExpense> result = expenseRepo
                .findByCategoryAndEmployeeIdOrderByExpenseDateAsc(ExpenseCategoryCode.SALARY, anna.getId());

        assertThat(result).extracting(CompanyExpense::getSource)
                .containsExactly("Wypłata 06", "Wypłata 07");
    }

    private KsefSeller seller(String nip) {
        return em.persistAndFlush(KsefSeller.builder().nip(nip).name("Seller " + nip).build());
    }

    private CompanyExpense sellerExpense(KsefSeller seller, String source, LocalDate date, String total) {
        return em.persistAndFlush(CompanyExpense.builder()
                .source(source).category("OTHER").seller(seller)
                .expenseDate(date).totalValue(new BigDecimal(total)).build());
    }

    @Test
    void findBySellerId_returnsOnlyThatSeller_orderedByDateDescThenIdDesc() {
        KsefSeller target = seller("5261040828");
        KsefSeller other = seller("7740001454");
        sellerExpense(target, "Stary", LocalDate.of(2026, 1, 10), "10.00");
        sellerExpense(target, "Nowy A", LocalDate.of(2026, 3, 10), "20.00");
        sellerExpense(target, "Nowy B", LocalDate.of(2026, 3, 10), "30.00");
        sellerExpense(other, "Obcy", LocalDate.of(2026, 4, 10), "40.00");
        em.persistAndFlush(CompanyExpense.builder()
                .source("Bez sprzedawcy").category("OTHER")
                .expenseDate(LocalDate.of(2026, 5, 10)).totalValue(BigDecimal.ONE).build());
        em.clear();

        Page<CompanyExpense> page = expenseRepo.findBySellerId(target.getId(), PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(CompanyExpense::getSource)
                .containsExactly("Nowy B", "Nowy A");
        assertThat(page.getContent().get(0).getSeller().getNip()).isEqualTo("5261040828");
    }

    @Test
    void summarizeBySellerId_returnsCountAndTotalOfSellerExpenses() {
        KsefSeller target = seller("5261040828");
        KsefSeller other = seller("7740001454");
        sellerExpense(target, "A", LocalDate.of(2026, 1, 10), "100.50");
        sellerExpense(target, "B", LocalDate.of(2026, 2, 10), "49.50");
        sellerExpense(other, "C", LocalDate.of(2026, 2, 10), "999.00");

        SellerManualExpenseSummaryProjection summary = expenseRepo.summarizeBySellerId(target.getId());

        assertThat(summary.getExpenseCount()).isEqualTo(2L);
        assertThat(summary.getTotalValue()).isEqualByComparingTo("150.00");
    }

    @Test
    void summarizeBySellerId_returnsZeroCountAndNullTotal_whenNoExpenses() {
        KsefSeller target = seller("5261040828");

        SellerManualExpenseSummaryProjection summary = expenseRepo.summarizeBySellerId(target.getId());

        assertThat(summary.getExpenseCount()).isEqualTo(0L);
        assertThat(summary.getTotalValue()).isNull();
    }

    @Test
    void sumMonthlySpendBySellerId_groupsByMonthWithinRange() {
        KsefSeller target = seller("5261040828");
        sellerExpense(target, "A", LocalDate.of(2026, 3, 1), "10.00");
        sellerExpense(target, "B", LocalDate.of(2026, 3, 31), "15.00");
        sellerExpense(target, "C", LocalDate.of(2026, 7, 5), "20.00");
        sellerExpense(target, "D", LocalDate.of(2025, 3, 5), "500.00");

        List<KsefMonthlySpendProjection> rows = expenseRepo.sumMonthlySpendBySellerId(
                target.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(rows).hasSize(2);
        KsefMonthlySpendProjection march = rows.stream()
                .filter(r -> r.getMonth().intValue() == 3).findFirst().orElseThrow();
        assertThat(march.getGross()).isEqualByComparingTo("25.00");
        assertThat(march.getCnt()).isEqualTo(2L);
    }

    @Test
    void findFirstAndLastExpenseDateBySellerId_returnMinAndMax() {
        KsefSeller target = seller("5261040828");
        sellerExpense(target, "A", LocalDate.of(2024, 3, 1), "10.00");
        sellerExpense(target, "B", LocalDate.of(2026, 7, 5), "20.00");

        assertThat(expenseRepo.findFirstExpenseDateBySellerId(target.getId())).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(expenseRepo.findLastExpenseDateBySellerId(target.getId())).isEqualTo(LocalDate.of(2026, 7, 5));
    }

    @Test
    void findOneByIdWithItems_fetchesSeller() {
        KsefSeller target = seller("5261040828");
        CompanyExpense saved = sellerExpense(target, "A", LocalDate.of(2026, 3, 1), "10.00");
        em.clear();

        CompanyExpense loaded = expenseRepo.findOneByIdWithItems(saved.getId()).orElseThrow();
        em.clear();

        assertThat(loaded.getSeller().getName()).isEqualTo("Seller 5261040828");
    }

    private CompanyExpenseItem expenseItem(String name) {
        return CompanyExpenseItem.builder()
                .name(name).quantity(1).vatRate(VatRate.VAT_23).price(new BigDecimal("10.00")).build();
    }

    @Test
    void findOneByIdWithItems_returnsItemsInIdOrder_evenAfterRowUpdate() {
        CompanyExpense expense = CompanyExpense.builder()
                .source("Zakupy").category("OTHER")
                .expenseDate(LocalDate.of(2026, 3, 1)).build();
        expense.addExpenseItem(expenseItem("First"));
        expense.addExpenseItem(expenseItem("Second"));
        expense.addExpenseItem(expenseItem("Third"));
        CompanyExpense saved = em.persistAndFlush(expense);
        CompanyExpense temporaryOwner = em.persistAndFlush(CompanyExpense.builder()
                .source("Tmp").category("OTHER")
                .expenseDate(LocalDate.of(2026, 3, 2)).build());
        Long firstItemId = saved.getExpenseItems().get(0).getId();
        for (CompanyExpense target : List.of(temporaryOwner, saved)) {
            em.getEntityManager()
                    .createQuery("UPDATE CompanyExpenseItem it SET it.companyExpense = :target WHERE it.id = :id")
                    .setParameter("target", target)
                    .setParameter("id", firstItemId)
                    .executeUpdate();
        }
        em.clear();

        CompanyExpense loaded = expenseRepo.findOneByIdWithItems(saved.getId()).orElseThrow();

        assertThat(loaded.getExpenseItems()).extracting(CompanyExpenseItem::getName)
                .containsExactly("First", "Second", "Third");
    }
}

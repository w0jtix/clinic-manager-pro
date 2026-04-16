package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.CompanyExpense;
import com.clinic.clinicmanager.model.constants.ExpenseCategory;
import com.clinic.clinicmanager.repo.projection.CategoryExpenseProjection;
import com.clinic.clinicmanager.repo.projection.CompanyExpenseProjection;
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
class CompanyExpenseRepoTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CompanyExpenseRepo expenseRepo;

    @Test
    void findMonthlyExpensesByYear_groupsByMonth_andSumsValues() {
        em.persistAndFlush(CompanyExpense.builder()
                .source("Invoice 1").category(ExpenseCategory.RENT)
                .expenseDate(LocalDate.of(2024, 1, 10)).totalValue(100.0).build());
        em.persistAndFlush(CompanyExpense.builder()
                .source("Invoice 2").category(ExpenseCategory.RENT)
                .expenseDate(LocalDate.of(2024, 1, 20)).totalValue(50.0).build());
        em.persistAndFlush(CompanyExpense.builder()
                .source("Invoice 3").category(ExpenseCategory.FEES)
                .expenseDate(LocalDate.of(2024, 3, 5)).totalValue(200.0).build());

        List<CompanyExpenseProjection> result = expenseRepo.findMonthlyExpensesByYear(2024);

        assertThat(result).hasSize(2);

        CompanyExpenseProjection january = findByPeriod(result, 1);
        assertThat(january.getTotalExpense()).isEqualByComparingTo(BigDecimal.valueOf(150.0));

        CompanyExpenseProjection march = findByPeriod(result, 3);
        assertThat(march.getTotalExpense()).isEqualByComparingTo(BigDecimal.valueOf(200.0));
    }

    @Test
    void findMonthlyExpensesByYear_differentYear_notIncluded() {
        em.persistAndFlush(CompanyExpense.builder()
                .source("Old expense").category(ExpenseCategory.RENT)
                .expenseDate(LocalDate.of(2023, 6, 1)).totalValue(500.0).build());

        List<CompanyExpenseProjection> result = expenseRepo.findMonthlyExpensesByYear(2024);

        assertThat(result).isEmpty();
    }

    @Test
    void findMonthlyExpensesByYear_orderedByMonth() {
        em.persistAndFlush(CompanyExpense.builder()
                .source("December").category(ExpenseCategory.RENT)
                .expenseDate(LocalDate.of(2024, 12, 1)).totalValue(10.0).build());
        em.persistAndFlush(CompanyExpense.builder()
                .source("February").category(ExpenseCategory.RENT)
                .expenseDate(LocalDate.of(2024, 2, 1)).totalValue(20.0).build());

        List<CompanyExpenseProjection> result = expenseRepo.findMonthlyExpensesByYear(2024);

        assertThat(result).extracting(CompanyExpenseProjection::getPeriod)
                .containsExactly(2, 12);
    }


    @Test
    void findDailyExpensesByYearAndMonth_groupsByDay() {
        em.persistAndFlush(CompanyExpense.builder()
                .source("Day 5").category(ExpenseCategory.FEES)
                .expenseDate(LocalDate.of(2024, 3, 5)).totalValue(100.0).build());
        em.persistAndFlush(CompanyExpense.builder()
                .source("Day 15").category(ExpenseCategory.FEES)
                .expenseDate(LocalDate.of(2024, 3, 15)).totalValue(200.0).build());
        em.persistAndFlush(CompanyExpense.builder()
                .source("Different month").category(ExpenseCategory.FEES)
                .expenseDate(LocalDate.of(2024, 4, 5)).totalValue(999.0).build());

        List<CompanyExpenseProjection> result = expenseRepo.findDailyExpensesByYearAndMonth(2024, 3);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CompanyExpenseProjection::getPeriod)
                .containsExactly(5, 15);
    }

    @Test
    void findExpensesByCategory_sumsPerCategory() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to   = LocalDate.of(2024, 12, 31);

        em.persistAndFlush(CompanyExpense.builder()
                .source("Rent 1").category(ExpenseCategory.RENT)
                .expenseDate(LocalDate.of(2024, 1, 1)).totalValue(1000.0).build());
        em.persistAndFlush(CompanyExpense.builder()
                .source("Rent 2").category(ExpenseCategory.RENT)
                .expenseDate(LocalDate.of(2024, 2, 1)).totalValue(1000.0).build());
        em.persistAndFlush(CompanyExpense.builder()
                .source("ZUS").category(ExpenseCategory.ZUS)
                .expenseDate(LocalDate.of(2024, 1, 15)).totalValue(500.0).build());

        List<CategoryExpenseProjection> result = expenseRepo.findExpensesByCategory(from, to);

        assertThat(result).hasSize(2);

        CategoryExpenseProjection rent = findByCategory(result, ExpenseCategory.RENT);
        assertThat(rent.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(2000.0));

        CategoryExpenseProjection zus = findByCategory(result, ExpenseCategory.ZUS);
        assertThat(zus.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(500.0));
    }

    @Test
    void findExpensesByCategory_orderedByTotalDesc() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to   = LocalDate.of(2024, 12, 31);

        em.persistAndFlush(CompanyExpense.builder()
                .source("Minor").category(ExpenseCategory.FEES)
                .expenseDate(LocalDate.of(2024, 1, 1)).totalValue(100.0).build());
        em.persistAndFlush(CompanyExpense.builder()
                .source("Major").category(ExpenseCategory.RENT)
                .expenseDate(LocalDate.of(2024, 1, 1)).totalValue(5000.0).build());

        List<CategoryExpenseProjection> result = expenseRepo.findExpensesByCategory(from, to);

        assertThat(result.getFirst().getCategory()).isEqualTo(ExpenseCategory.RENT);
        assertThat(result.get(1).getCategory()).isEqualTo(ExpenseCategory.FEES);
    }

    @Test
    void sumTotalExpenses_sumsAllInDateRange() {
        em.persistAndFlush(CompanyExpense.builder()
                .source("A").category(ExpenseCategory.RENT)
                .expenseDate(LocalDate.of(2024, 1, 1)).totalValue(300.0).build());
        em.persistAndFlush(CompanyExpense.builder()
                .source("B").category(ExpenseCategory.FEES)
                .expenseDate(LocalDate.of(2024, 6, 1)).totalValue(200.0).build());
        em.persistAndFlush(CompanyExpense.builder()
                .source("C").category(ExpenseCategory.RENT)
                .expenseDate(LocalDate.of(2025, 1, 1)).totalValue(999.0).build());

        Double result = expenseRepo.sumTotalExpenses(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31));

        assertThat(result).isEqualTo(500.0);
    }

    @Test
    void sumTotalExpenses_noExpenses_returnsZero() {
        Double result = expenseRepo.sumTotalExpenses(
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31));

        assertThat(result).isEqualTo(0.0);
    }

    // helpers

    private CompanyExpenseProjection findByPeriod(List<CompanyExpenseProjection> list, int period) {
        return list.stream()
                .filter(p -> p.getPeriod().equals(period))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Period not found: " + period));
    }

    private CategoryExpenseProjection findByCategory(List<CategoryExpenseProjection> list, ExpenseCategory category) {
        return list.stream()
                .filter(p -> p.getCategory().equals(category))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Category not found: " + category));
    }
}

package com.clinic.clinicmanager.model;

import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.utils.MoneyUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class CompanyExpenseTest {

    private static void assertMoney(String expected, BigDecimal actual) {
        assertNotNull(actual);
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    private static CompanyExpenseItem item(String price, int quantity, VatRate vatRate) {
        return CompanyExpenseItem.builder()
                .name("Pozycja")
                .price(new BigDecimal(price))
                .quantity(quantity)
                .vatRate(vatRate)
                .build();
    }

    private static CompanyExpense expense(ExpenseCurrency currency, CompanyExpenseItem... items) {
        CompanyExpense expense = CompanyExpense.builder()
                .source("Dostawca")
                .expenseDate(LocalDate.of(2026, 11, 2))
                .category("OTHER")
                .currency(currency)
                .build();
        for (CompanyExpenseItem item : items) {
            expense.addExpenseItem(item);
        }
        return expense;
    }

    @Test
    void calculateTotals_shouldKeepPlnTotalsAndLeaveCurrencyFieldsNull_whenPln() {
        CompanyExpense expense = expense(ExpenseCurrency.PLN, item("7.4950", 2, VatRate.VAT_23));

        expense.calculateTotals();

        assertMoney("14.99", expense.getTotalValue());
        assertMoney("2.80", expense.getTotalVat());
        assertMoney("12.19", expense.getTotalNet());
        assertNull(expense.getTotalValueCurrency());
        assertNull(expense.getTotalVatCurrency());
        assertNull(expense.getTotalNetCurrency());
        assertNull(expense.getExchangeRate());
        assertNull(expense.getExchangeRateDate());
        assertNull(expense.getExchangeRateTable());
    }

    @Test
    void calculateTotals_shouldConvertToPlnWithRate_whenEur() {
        CompanyExpense expense = expense(ExpenseCurrency.EUR, item("100.00", 1, VatRate.VAT_23));
        expense.applyExchangeRate(new BigDecimal("4.2512"), LocalDate.of(2026, 10, 30), "211/A/NBP/2026");

        expense.calculateTotals();

        BigDecimal vatCur = MoneyUtils.vatFromGross(new BigDecimal("100.00"), VatRate.VAT_23);
        assertMoney("18.70", vatCur);
        assertMoney("100.00", expense.getTotalValueCurrency());
        assertMoney(vatCur.toPlainString(), expense.getTotalVatCurrency());
        assertMoney("81.30", expense.getTotalNetCurrency());
        assertMoney("425.12", expense.getTotalValue());
        BigDecimal expectedVat = MoneyUtils.money(vatCur.multiply(new BigDecimal("4.2512")));
        assertMoney("79.50", expectedVat);
        assertMoney(expectedVat.toPlainString(), expense.getTotalVat());
        assertMoney("345.62", expense.getTotalNet());
        assertEquals(0, expense.getTotalValue().subtract(expense.getTotalVat()).compareTo(expense.getTotalNet()));
    }

    @Test
    void calculateTotals_shouldSumMixedVatRatesInCurrencyAndPln_whenUsd() {
        CompanyExpense expense = expense(ExpenseCurrency.USD,
                item("50.00", 2, VatRate.VAT_23),
                item("30.0000", 1, VatRate.VAT_8));
        expense.applyExchangeRate(new BigDecimal("3.9876"), LocalDate.of(2026, 10, 30), "211/A/NBP/2026");

        expense.calculateTotals();

        assertMoney("130.00", expense.getTotalValueCurrency());
        assertMoney("20.92", expense.getTotalVatCurrency());
        assertMoney("109.08", expense.getTotalNetCurrency());
        assertMoney("518.39", expense.getTotalValue());
        assertMoney("83.42", expense.getTotalVat());
        assertMoney("434.97", expense.getTotalNet());
    }

    @Test
    void calculateTotals_shouldThrow_whenForeignCurrencyWithoutRate() {
        CompanyExpense expense = expense(ExpenseCurrency.EUR, item("100.00", 1, VatRate.VAT_23));

        assertThrows(IllegalStateException.class, expense::calculateTotals);
    }

    @Test
    void calculateTotals_shouldClearCurrencyTotals_whenSwitchedBackToPln() {
        CompanyExpense expense = expense(ExpenseCurrency.EUR, item("100.00", 1, VatRate.VAT_23));
        expense.applyExchangeRate(new BigDecimal("4.2512"), LocalDate.of(2026, 10, 30), "211/A/NBP/2026");
        expense.calculateTotals();

        expense.setCurrency(ExpenseCurrency.PLN);
        expense.clearExchangeRate();
        expense.calculateTotals();

        assertMoney("100.00", expense.getTotalValue());
        assertMoney("18.70", expense.getTotalVat());
        assertMoney("81.30", expense.getTotalNet());
        assertNull(expense.getTotalValueCurrency());
        assertNull(expense.getTotalVatCurrency());
        assertNull(expense.getTotalNetCurrency());
        assertNull(expense.getExchangeRate());
    }
}

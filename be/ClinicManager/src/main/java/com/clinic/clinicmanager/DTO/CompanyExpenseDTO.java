package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.CompanyExpense;
import com.clinic.clinicmanager.model.CompanyExpenseItem;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;
import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import com.clinic.clinicmanager.repo.projection.UnifiedExpenseProjection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CompanyExpenseDTO {
    private Long id;
    private String source;
    private LocalDate expenseDate;
    private String category;
    private Long employeeId;
    private List<CompanyExpenseItemDTO> expenseItems;
    private BigDecimal totalNet;
    private BigDecimal totalVat;
    private BigDecimal totalValue;
    private String sourceType;
    private Boolean awaitingProductVerification;
    private String sellerDefaultExpenseCategory;
    private String invoiceType;
    private Long sellerId;
    private ExpenseSellerDTO seller;
    private List<String> itemCategories;
    private String invoiceNumber;
    private ExpenseCurrency currency;
    private BigDecimal exchangeRate;
    private LocalDate exchangeRateDate;
    private String exchangeRateTable;
    private BigDecimal totalNetCurrency;
    private BigDecimal totalVatCurrency;
    private BigDecimal totalValueCurrency;

    public CompanyExpenseDTO(UnifiedExpenseProjection projection) {
        this.id = projection.getSourceId();
        this.source = projection.getSource();
        this.expenseDate = projection.getExpenseDate();
        this.category = projection.getCategory();
        this.expenseItems = List.of();
        this.totalNet = projection.getTotalNet();
        this.totalVat = projection.getTotalVat();
        this.totalValue = projection.getTotalValue();
        this.sourceType = projection.getSourceType();
        this.awaitingProductVerification = projection.getAwaitingProductVerification();
        this.sellerDefaultExpenseCategory = projection.getSellerDefaultExpenseCategory();
        this.invoiceType = projection.getInvoiceType();
        this.sellerId = projection.getSellerId();
        this.itemCategories = parseItemCategories(projection.getItemCategories());
        this.currency = projection.getCurrency() != null
                ? ExpenseCurrency.valueOf(projection.getCurrency())
                : ExpenseCurrency.PLN;
        this.totalValueCurrency = projection.getTotalValueCurrency();
    }

    public CompanyExpenseDTO(CompanyExpense expense) {
        this.id = expense.getId();
        this.source = expense.getSource();
        this.expenseDate = expense.getExpenseDate();
        this.category = expense.getCategory();
        this.employeeId = expense.getEmployee() != null ? expense.getEmployee().getId() : null;
        this.expenseItems = expense.getExpenseItems().stream()
                .map(CompanyExpenseItemDTO::new)
                .collect(Collectors.toList());
        this.totalNet = expense.getTotalNet();
        this.totalVat = expense.getTotalVat();
        this.totalValue = expense.getTotalValue();
        this.sourceType = "MANUAL";
        this.awaitingProductVerification = false;
        if (expense.getSeller() != null) {
            this.sellerId = expense.getSeller().getId();
            this.seller = new ExpenseSellerDTO(expense.getSeller());
        }
        this.itemCategories = ExpenseCategoryCode.MIXED.equals(expense.getCategory())
                ? expense.getExpenseItems().stream()
                        .map(CompanyExpenseItem::getCategory)
                        .filter(Objects::nonNull)
                        .distinct()
                        .sorted()
                        .toList()
                : List.of();
        this.invoiceNumber = expense.getInvoiceNumber();
        this.currency = expense.getCurrency();
        this.exchangeRate = expense.getExchangeRate();
        this.exchangeRateDate = expense.getExchangeRateDate();
        this.exchangeRateTable = expense.getExchangeRateTable();
        this.totalNetCurrency = expense.getTotalNetCurrency();
        this.totalVatCurrency = expense.getTotalVatCurrency();
        this.totalValueCurrency = expense.getTotalValueCurrency();
    }

    private static List<String> parseItemCategories(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(","))
                .filter(code -> !code.isBlank())
                .toList();
    }

    public CompanyExpense toEntity() {
        CompanyExpense expense = CompanyExpense.builder()
                .id(this.id)
                .source(this.source)
                .expenseDate(this.expenseDate)
                .category(this.category)
                .invoiceNumber(this.invoiceNumber)
                .totalNet(this.totalNet)
                .totalVat(this.totalVat)
                .totalValue(this.totalValue)
                .currency(this.currency != null ? this.currency : ExpenseCurrency.PLN)
                .exchangeRate(this.exchangeRate)
                .exchangeRateDate(this.exchangeRateDate)
                .exchangeRateTable(this.exchangeRateTable)
                .totalNetCurrency(this.totalNetCurrency)
                .totalVatCurrency(this.totalVatCurrency)
                .totalValueCurrency(this.totalValueCurrency)
                .build();

        for (CompanyExpenseItemDTO itemDTO : this.expenseItems) {
            CompanyExpenseItem item = itemDTO.toEntity(expense);
            expense.addExpenseItem(item);
        }

        return expense;
    }
}

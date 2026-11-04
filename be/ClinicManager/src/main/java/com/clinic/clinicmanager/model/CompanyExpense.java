package com.clinic.clinicmanager.model;

import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import com.clinic.clinicmanager.utils.MoneyUtils;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "company_expense")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CompanyExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Column(nullable = false)
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    @ToString.Exclude
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    @ToString.Exclude
    private KsefSeller seller;

    @Column(name = "invoice_number", length = 64)
    private String invoiceNumber;

    @OneToMany(mappedBy = "companyExpense", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @ToString.Exclude
    @Builder.Default
    private List<CompanyExpenseItem> expenseItems = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal totalVat = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal totalValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    @Builder.Default
    private ExpenseCurrency currency = ExpenseCurrency.PLN;

    @Column(name = "exchange_rate")
    private BigDecimal exchangeRate;

    @Column(name = "exchange_rate_date")
    private LocalDate exchangeRateDate;

    @Column(name = "exchange_rate_table", length = 32)
    private String exchangeRateTable;

    @Column(name = "total_net_currency")
    private BigDecimal totalNetCurrency;

    @Column(name = "total_vat_currency")
    private BigDecimal totalVatCurrency;

    @Column(name = "total_value_currency")
    private BigDecimal totalValueCurrency;

    public void applyExchangeRate(BigDecimal rate, LocalDate rateDate, String tableNo) {
        this.exchangeRate = rate;
        this.exchangeRateDate = rateDate;
        this.exchangeRateTable = tableNo;
    }

    public void clearExchangeRate() {
        this.exchangeRate = null;
        this.exchangeRateDate = null;
        this.exchangeRateTable = null;
    }

    public void addExpenseItem(CompanyExpenseItem item) {
        expenseItems.add(item);
        item.setCompanyExpense(this);
    }

    public void removeExpenseItem(CompanyExpenseItem item) {
        expenseItems.remove(item);
        item.setCompanyExpense(null);
    }

    public void calculateTotals() {
        BigDecimal itemsGrossTotal = BigDecimal.ZERO;
        BigDecimal itemsVat = BigDecimal.ZERO;

        for (CompanyExpenseItem item : expenseItems) {
            BigDecimal itemGross = MoneyUtils.nz(item.getPrice())
                    .multiply(BigDecimal.valueOf(item.getQuantity()));
            itemsGrossTotal = itemsGrossTotal.add(itemGross);
            itemsVat = itemsVat.add(MoneyUtils.vatFromGross(itemGross, item.getVatRate()));
        }

        BigDecimal valueInCurrency = MoneyUtils.money(itemsGrossTotal);
        BigDecimal vatInCurrency = MoneyUtils.money(itemsVat);
        BigDecimal netInCurrency = MoneyUtils.money(itemsGrossTotal.subtract(itemsVat));

        if (currency == null || !currency.isForeign()) {
            this.totalValue = valueInCurrency;
            this.totalVat = vatInCurrency;
            this.totalNet = netInCurrency;
            this.totalValueCurrency = null;
            this.totalVatCurrency = null;
            this.totalNetCurrency = null;
            return;
        }

        if (exchangeRate == null) {
            throw new IllegalStateException("Exchange rate is required for currency " + currency);
        }

        this.totalValueCurrency = valueInCurrency;
        this.totalVatCurrency = vatInCurrency;
        this.totalNetCurrency = netInCurrency;
        this.totalValue = MoneyUtils.money(valueInCurrency.multiply(exchangeRate));
        this.totalVat = MoneyUtils.money(vatInCurrency.multiply(exchangeRate));
        this.totalNet = this.totalValue.subtract(this.totalVat);
    }
}

package com.clinic.clinicmanager.repo.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface UnifiedExpenseProjection {
    Long getSourceId();
    String getSourceType();
    String getSource();
    LocalDate getExpenseDate();
    String getCategory();
    BigDecimal getTotalNet();
    BigDecimal getTotalVat();
    BigDecimal getTotalValue();
    Boolean getAwaitingProductVerification();
    String getSellerDefaultExpenseCategory();
    String getInvoiceType();
    Long getSellerId();
    String getItemCategories();
    String getCurrency();
    BigDecimal getTotalValueCurrency();
}

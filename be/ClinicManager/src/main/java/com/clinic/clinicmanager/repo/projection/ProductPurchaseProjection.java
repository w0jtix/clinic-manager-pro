package com.clinic.clinicmanager.repo.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ProductPurchaseProjection {
    Long getProductId();
    LocalDate getPurchaseDate();
    BigDecimal getUnitNetPrice();
    BigDecimal getUnitGrossPrice();
    Integer getQuantity();
    String getVatRate();
}

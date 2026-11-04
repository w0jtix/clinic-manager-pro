package com.clinic.clinicmanager.repo.projection;

import java.math.BigDecimal;

public interface KsefSellerSummaryProjection {
    Long getInvoiceCount();
    BigDecimal getTotalGross();
    Long getUnpaidCount();
    Long getAwaitingVerificationCount();
    Long getUncategorizedCount();
}

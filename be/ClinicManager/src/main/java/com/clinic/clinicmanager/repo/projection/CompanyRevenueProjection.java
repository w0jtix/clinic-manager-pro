package com.clinic.clinicmanager.repo.projection;

import java.math.BigDecimal;

public interface CompanyRevenueProjection {
    Integer getPeriod();
    BigDecimal getRevenue();
}

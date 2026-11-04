package com.clinic.clinicmanager.repo.projection;

import java.math.BigDecimal;

public interface KsefMonthlySpendProjection {
    Number getMonth();
    BigDecimal getGross();
    Long getCnt();
}

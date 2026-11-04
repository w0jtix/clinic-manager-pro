package com.clinic.clinicmanager.repo.projection;

import java.math.BigDecimal;

public interface UnifiedCategoryExpenseProjection {
    String getCategory();
    BigDecimal getTotalAmount();
}

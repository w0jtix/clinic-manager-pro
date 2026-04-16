package com.clinic.clinicmanager.repo.projection;

import com.clinic.clinicmanager.model.constants.ExpenseCategory;

import java.math.BigDecimal;

public interface CategoryExpenseProjection {
    ExpenseCategory getCategory();
    BigDecimal getTotalAmount();
}

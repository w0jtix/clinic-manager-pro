package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.constants.ExpenseCategory;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExpenseCategoryBreakdownDTO {

    private ExpenseCategory category;
    private Double amount;
    private Double sharePercent;
}

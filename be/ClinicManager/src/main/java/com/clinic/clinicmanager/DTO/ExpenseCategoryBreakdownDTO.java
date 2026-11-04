package com.clinic.clinicmanager.DTO;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExpenseCategoryBreakdownDTO {

    private String category;
    private Double amount;
    private Double sharePercent;
}

package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.ExpenseCategory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ExpenseCategoryDTO {
    private Long id;
    private String code;
    private String name;
    private String color;
    private Boolean system;
    private Boolean internal;

    public ExpenseCategoryDTO(String name, String color) {
        this.name = name;
        this.color = color;
    }

    public ExpenseCategoryDTO(ExpenseCategory category) {
        this.id = category.getId();
        this.code = category.getCode();
        this.name = category.getName();
        this.color = category.getColor();
        this.system = category.getIsSystem();
        this.internal = category.getIsInternal();
    }
}

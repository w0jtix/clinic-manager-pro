package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.CompanyExpense;
import com.clinic.clinicmanager.model.CompanyExpenseItem;
import com.clinic.clinicmanager.model.constants.VatRate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CompanyExpenseItemDTO {

    private Long id;
    private String name;
    private Integer quantity;
    private VatRate vatRate;
    private Double price;

    public CompanyExpenseItemDTO(CompanyExpenseItem item) {
        this.id = item.getId();
        this.name = item.getName();
        this.quantity = item.getQuantity();
        this.vatRate = item.getVatRate();
        this.price = item.getPrice();
    }

    public CompanyExpenseItem toEntity(CompanyExpense expense) {
        return CompanyExpenseItem.builder()
                .id(this.id)
                .companyExpense(expense)
                .name(this.name)
                .quantity(this.quantity)
                .vatRate(this.vatRate)
                .price(this.price)
                .build();
    }
}

package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.CompanyExpense;
import com.clinic.clinicmanager.model.CompanyExpenseItem;
import com.clinic.clinicmanager.model.constants.SalaryComponent;
import com.clinic.clinicmanager.model.constants.VatRate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CompanyExpenseItemDTO {

    private Long id;
    private String name;
    private Integer quantity;
    private VatRate vatRate;
    private BigDecimal price;
    private SalaryComponent salaryComponent;
    private Long productId;
    private String productName;
    private String category;

    public CompanyExpenseItemDTO(CompanyExpenseItem item) {
        this.id = item.getId();
        this.name = item.getName();
        this.quantity = item.getQuantity();
        this.vatRate = item.getVatRate();
        this.price = item.getPrice();
        this.salaryComponent = item.getSalaryComponent();
        this.category = item.getCategory();
        if (item.getProduct() != null) {
            this.productId = item.getProduct().getId();
            this.productName = item.getProduct().getName();
        }
    }

    public CompanyExpenseItem toEntity(CompanyExpense expense) {
        return CompanyExpenseItem.builder()
                .id(this.id)
                .companyExpense(expense)
                .name(this.name)
                .quantity(this.quantity)
                .vatRate(this.vatRate)
                .price(this.price)
                .salaryComponent(this.salaryComponent)
                .category(this.category)
                .build();
    }
}

package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.ProductCategory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProductCategoryDTO {
    private Long id;
    private String name;
    private String color;
    private Boolean system;
    private Boolean restored;

    public ProductCategoryDTO(Long id, String name, String color) {
        this.id = id;
        this.name = name;
        this.color = color;
    }

    public ProductCategoryDTO(ProductCategory category) {
        this.id = category.getId();
        this.name = category.getName();
        this.color = category.getColor();
        this.system = category.getIsSystem();
    }

    public ProductCategory toEntity() {
        return ProductCategory.builder()
                .id(this.id)
                .name(this.name)
                .color(this.color)
                .build();
    }
}

package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.BaseServiceCategory;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BaseServiceCategoryDTO {
    private Long id;
    private String name;
    private String color;
    private Boolean isDeleted;
    private Boolean restored;

    public BaseServiceCategoryDTO(Long id, String name, String color, Boolean isDeleted) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.isDeleted = isDeleted;
    }

    public BaseServiceCategoryDTO(BaseServiceCategory category) {
        this.id = category.getId();
        this.name = category.getName();
        this.color = category.getColor();
        this.isDeleted = category.getIsDeleted();
    }

    public BaseServiceCategory toEntity() {
        return BaseServiceCategory.builder()
                .id(this.id)
                .name(this.name)
                .color(this.color)
                .isDeleted(this.isDeleted != null ? this.isDeleted : false)
                .build();
    }
}

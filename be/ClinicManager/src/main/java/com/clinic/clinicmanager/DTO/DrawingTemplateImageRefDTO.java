package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.repo.projection.DrawingTemplateImageInfoProjection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DrawingTemplateImageRefDTO {
    private Long id;
    private Integer width;
    private Integer height;

    public DrawingTemplateImageRefDTO(DrawingTemplateImageInfoProjection image) {
        this.id = image.getId();
        this.width = image.getWidth();
        this.height = image.getHeight();
    }
}

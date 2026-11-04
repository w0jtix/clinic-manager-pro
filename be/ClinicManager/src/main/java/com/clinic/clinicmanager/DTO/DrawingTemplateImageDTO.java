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
public class DrawingTemplateImageDTO {
    private Long id;
    private Integer width;
    private Integer height;
    private Integer sizeBytes;

    public DrawingTemplateImageDTO(DrawingTemplateImageInfoProjection image) {
        this.id = image.getId();
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.sizeBytes = image.getSizeBytes();
    }
}

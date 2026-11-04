package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.DrawingTemplate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DrawingTemplateRefDTO {
    private Long id;
    private String name;
    private Boolean archived;
    private DrawingTemplateImageRefDTO image;

    public DrawingTemplateRefDTO(DrawingTemplate template, DrawingTemplateImageRefDTO image) {
        this.id = template.getId();
        this.name = template.getName();
        this.archived = template.isArchived();
        this.image = image;
    }
}

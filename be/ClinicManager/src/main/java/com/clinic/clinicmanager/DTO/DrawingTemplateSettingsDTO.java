package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.DrawingTemplate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DrawingTemplateSettingsDTO {
    private Long id;
    private String name;
    private Integer displayOrder;
    private Boolean archived;
    private LocalDateTime archivedAt;
    private DrawingTemplateImageDTO image;
    private Long clientCount;

    public DrawingTemplateSettingsDTO(DrawingTemplate template, DrawingTemplateImageDTO image, long clientCount) {
        this.id = template.getId();
        this.name = template.getName();
        this.displayOrder = template.getDisplayOrder();
        this.archived = template.isArchived();
        this.archivedAt = template.getArchivedAt();
        this.image = image;
        this.clientCount = clientCount;
    }
}

package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.DrawingSheet;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DrawingSheetDTO {
    private Long id;
    private Integer sheetNumber;
    private Long templateId;
    private Long templateImageId;
    private Integer width;
    private Integer height;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;
    private List<DrawingLayerDTO> layers;

    public DrawingSheetDTO(DrawingSheet sheet, List<DrawingLayerDTO> layers) {
        this.id = sheet.getId();
        this.sheetNumber = sheet.getSheetNumber();
        this.templateId = sheet.getTemplateId();
        this.templateImageId = sheet.getTemplateImageId();
        this.width = sheet.getWidth();
        this.height = sheet.getHeight();
        this.createdAt = sheet.getCreatedAt();
        this.closedAt = sheet.getClosedAt();
        this.layers = layers;
    }
}

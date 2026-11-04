package com.clinic.clinicmanager.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DrawingGroupDTO {
    private DrawingTemplateRefDTO template;
    private DrawingSheetDTO currentSheet;
    private List<DrawingSheetDTO> closedSheets;
}

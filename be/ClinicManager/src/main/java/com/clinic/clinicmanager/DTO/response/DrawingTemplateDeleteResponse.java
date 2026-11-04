package com.clinic.clinicmanager.DTO.response;

import com.clinic.clinicmanager.model.constants.DrawingTemplateDeleteResult;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DrawingTemplateDeleteResponse {
    private DrawingTemplateDeleteResult result;
}

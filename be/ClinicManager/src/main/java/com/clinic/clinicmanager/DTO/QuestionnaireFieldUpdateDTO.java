package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.constants.QuestionnaireFieldType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class QuestionnaireFieldUpdateDTO {
    private Long id;
    private String label;
    private String labelEn;
    private QuestionnaireFieldType type;
    private Boolean archived;
}

package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.QuestionnaireField;
import com.clinic.clinicmanager.model.constants.QuestionnaireFieldType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class QuestionnaireFieldDTO {
    private Long id;
    private String label;
    private String labelEn;
    private QuestionnaireFieldType type;
    private Integer displayOrder;
    private Boolean archived;
    private Boolean hasAnswers;

    public QuestionnaireFieldDTO(QuestionnaireField field, boolean hasAnswers) {
        this.id = field.getId();
        this.label = field.getLabel();
        this.labelEn = field.getLabelEn();
        this.type = field.getType();
        this.displayOrder = field.getDisplayOrder();
        this.archived = field.getArchived();
        this.hasAnswers = hasAnswers && field.getType() != QuestionnaireFieldType.SECTION;
    }
}

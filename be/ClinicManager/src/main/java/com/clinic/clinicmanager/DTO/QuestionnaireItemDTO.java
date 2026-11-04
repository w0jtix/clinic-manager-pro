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
public class QuestionnaireItemDTO {
    private Long fieldId;
    private String label;
    private String labelEn;
    private QuestionnaireFieldType type;
    private Boolean archived;
    private Boolean checked;
    private String text;

    public QuestionnaireItemDTO(QuestionnaireField field, String value) {
        this.fieldId = field.getId();
        this.label = field.getLabel();
        this.labelEn = field.getLabelEn();
        this.type = field.getType();
        this.archived = field.getArchived();
        if (field.getType() == QuestionnaireFieldType.CHECKBOX) {
            this.checked = "true".equals(value);
        } else if (field.getType() == QuestionnaireFieldType.TEXT) {
            this.text = value;
        }
    }
}

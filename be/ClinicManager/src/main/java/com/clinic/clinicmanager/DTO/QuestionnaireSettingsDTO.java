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
public class QuestionnaireSettingsDTO {
    private Boolean englishEnabled;
    private List<QuestionnaireFieldDTO> fields;
}

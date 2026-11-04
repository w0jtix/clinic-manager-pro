package com.clinic.clinicmanager.DTO.response;

import com.clinic.clinicmanager.exceptions.QuestionnaireConflictException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class QuestionnaireConflictResponse {

    public static final String CODE = "QUESTIONNAIRE_CONFLICT";

    private String code;
    private Long currentVersion;

    public QuestionnaireConflictResponse(QuestionnaireConflictException ex) {
        this.code = CODE;
        this.currentVersion = ex.getCurrentVersion();
    }
}

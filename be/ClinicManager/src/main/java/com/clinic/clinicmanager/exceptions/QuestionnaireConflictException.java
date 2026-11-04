package com.clinic.clinicmanager.exceptions;

import lombok.Getter;

@Getter
public class QuestionnaireConflictException extends RuntimeException {

    private final Long currentVersion;

    public QuestionnaireConflictException(Long currentVersion) {
        super("Questionnaire was modified concurrently (current version: " + currentVersion + ")");
        this.currentVersion = currentVersion;
    }
}

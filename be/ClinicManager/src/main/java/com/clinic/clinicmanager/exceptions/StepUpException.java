package com.clinic.clinicmanager.exceptions;

import lombok.Getter;

@Getter
public class StepUpException extends RuntimeException {

    public static final String REQUIRED = "STEP_UP_REQUIRED";
    public static final String INVALID = "STEP_UP_INVALID";

    private final String code;

    public StepUpException(String code, String message) {
        super(message);
        this.code = code;
    }
}

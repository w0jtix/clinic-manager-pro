package com.clinic.clinicmanager.exceptions;

import lombok.Getter;

@Getter
public class AccessGateDeniedException extends RuntimeException {

    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String DEVICE_NOT_ALLOWED = "DEVICE_NOT_ALLOWED";
    public static final String MESSAGE = "Brak dostępu";

    private final String code;

    public AccessGateDeniedException(String code) {
        super(MESSAGE);
        this.code = code;
    }
}

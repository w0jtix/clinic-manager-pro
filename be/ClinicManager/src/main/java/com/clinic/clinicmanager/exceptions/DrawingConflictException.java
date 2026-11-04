package com.clinic.clinicmanager.exceptions;

import lombok.Getter;

@Getter
public class DrawingConflictException extends RuntimeException {

    private final Long currentVersion;

    public DrawingConflictException(Long currentVersion) {
        super("Drawing sheet was modified concurrently (current version: " + currentVersion + ")");
        this.currentVersion = currentVersion;
    }
}

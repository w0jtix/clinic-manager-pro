package com.clinic.clinicmanager.exceptions;

import lombok.Getter;

@Getter
public class TherapyEntryConflictException extends RuntimeException {

    private final Long currentVersion;

    public TherapyEntryConflictException(Long currentVersion) {
        super("Therapy entry was modified concurrently (current version: " + currentVersion + ")");
        this.currentVersion = currentVersion;
    }
}

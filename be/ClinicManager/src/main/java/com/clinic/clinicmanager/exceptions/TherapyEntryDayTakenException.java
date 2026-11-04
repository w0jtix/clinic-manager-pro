package com.clinic.clinicmanager.exceptions;

import lombok.Getter;

@Getter
public class TherapyEntryDayTakenException extends RuntimeException {

    private final Long entryId;

    public TherapyEntryDayTakenException(Long entryId) {
        super("Therapy entry day is already taken (existing entry id: " + entryId + ")");
        this.entryId = entryId;
    }

    public TherapyEntryDayTakenException(Long entryId, Throwable cause) {
        super("Therapy entry day is already taken (existing entry id: " + entryId + ")", cause);
        this.entryId = entryId;
    }
}

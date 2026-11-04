package com.clinic.clinicmanager.DTO.response;

import com.clinic.clinicmanager.exceptions.TherapyEntryConflictException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TherapyEntryConflictResponse {

    public static final String CODE = "THERAPY_ENTRY_CONFLICT";

    private String code;
    private Long currentVersion;

    public TherapyEntryConflictResponse(TherapyEntryConflictException ex) {
        this.code = CODE;
        this.currentVersion = ex.getCurrentVersion();
    }
}

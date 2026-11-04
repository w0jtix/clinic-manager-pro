package com.clinic.clinicmanager.DTO.response;

import com.clinic.clinicmanager.exceptions.TherapyEntryDayTakenException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TherapyEntryDayTakenResponse {

    public static final String CODE = "THERAPY_ENTRY_DAY_TAKEN";

    private String code;
    private Long entryId;

    public TherapyEntryDayTakenResponse(TherapyEntryDayTakenException ex) {
        this.code = CODE;
        this.entryId = ex.getEntryId();
    }
}

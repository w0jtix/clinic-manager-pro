package com.clinic.clinicmanager.DTO.response;

import com.clinic.clinicmanager.exceptions.DrawingConflictException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DrawingConflictResponse {

    public static final String CODE = "DRAWING_CONFLICT";

    private String code;
    private Long currentVersion;

    public DrawingConflictResponse(DrawingConflictException ex) {
        this.code = CODE;
        this.currentVersion = ex.getCurrentVersion();
    }
}

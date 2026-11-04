package com.clinic.clinicmanager.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MfaChallengeResponse {
    private boolean mfaRequired;
    private String mfaToken;
}

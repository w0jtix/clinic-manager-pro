package com.clinic.clinicmanager.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MfaSetupResponse {
    private String secret;
    private String otpauthUri;
    private String qrDataUri;
}

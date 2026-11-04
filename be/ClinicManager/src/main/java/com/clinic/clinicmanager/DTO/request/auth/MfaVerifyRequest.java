package com.clinic.clinicmanager.DTO.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class MfaVerifyRequest {

    @NotBlank
    @Size(max = 128)
    @ToString.Exclude
    private String mfaToken;

    @NotBlank
    @Size(max = 32)
    @ToString.Exclude
    private String code;

    private boolean rememberDevice;
}

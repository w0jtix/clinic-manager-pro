package com.clinic.clinicmanager.DTO.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

@Data
public class MfaCodeRequest {

    @NotBlank
    @Size(max = 32)
    @ToString.Exclude
    private String code;
}

package com.clinic.clinicmanager.DTO.request.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class ChangeCompanyPinRequest {

    @NotBlank
    @ToString.Exclude
    private String currentPin;

    @NotBlank
    @ToString.Exclude
    private String newPin;
}

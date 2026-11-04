package com.clinic.clinicmanager.DTO.request.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForceChangePasswordRequest {

    @NotNull
    private Long userId;

    @NotBlank
    @Size(min = 10, max = 40, message = "Hasło musi mieć od 10 do 40 znaków.")
    private String newPassword;
}

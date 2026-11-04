package com.clinic.clinicmanager.DTO.request.auth;

import com.clinic.clinicmanager.model.constants.RoleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.ToString;

import java.util.Set;

@Data
public class SignupRequest {
    @NotBlank
    @Size(min = 3, max = 20)
    private String username;

    @NotBlank
    @Size(min = 10, max = 40, message = "Hasło musi mieć od 10 do 40 znaków.")
    private String password;

    private Set<RoleType> role;

    @ToString.Exclude
    private String companyPin;
}

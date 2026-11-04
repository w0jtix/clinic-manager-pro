package com.clinic.clinicmanager.DTO.request.accesscontrol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GateRequest {
    private Boolean enabled;

    @ToString.Exclude
    private String code;

    @ToString.Exclude
    private String password;
}

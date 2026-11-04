package com.clinic.clinicmanager.DTO.request.accesscontrol;

import com.clinic.clinicmanager.model.constants.GatePauseDuration;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PauseRequest {
    private GatePauseDuration duration;

    @ToString.Exclude
    private String code;

    @ToString.Exclude
    private String password;
}

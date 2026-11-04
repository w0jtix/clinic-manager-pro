package com.clinic.clinicmanager.DTO.request.auth;

import lombok.Data;
import lombok.ToString;

@Data
public class DeviceRegisterRequest {

    @ToString.Exclude
    private String code;
}

package com.clinic.clinicmanager.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DeviceRegistrationResponse {
    private String message;
    private String deviceName;
}

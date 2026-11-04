package com.clinic.clinicmanager.DTO.request.device;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RenameDeviceRequest {
    private String name;
}

package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.DeviceRegistrationCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RegistrationCodeDTO {
    private Long id;
    private String code;
    private String name;
    private Long replaceDeviceId;
    private String replaceDeviceName;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    public RegistrationCodeDTO(DeviceRegistrationCode registrationCode, String replaceDeviceName) {
        this.id = registrationCode.getId();
        this.code = format(registrationCode.getCode());
        this.name = registrationCode.getName();
        this.replaceDeviceId = registrationCode.getReplaceDeviceId();
        this.replaceDeviceName = replaceDeviceName;
        this.createdBy = registrationCode.getCreatedBy();
        this.createdAt = registrationCode.getCreatedAt();
        this.expiresAt = registrationCode.getExpiresAt();
    }

    private static String format(String code) {
        if (code == null || code.length() != 6) {
            return code;
        }
        return code.substring(0, 3) + "-" + code.substring(3);
    }
}

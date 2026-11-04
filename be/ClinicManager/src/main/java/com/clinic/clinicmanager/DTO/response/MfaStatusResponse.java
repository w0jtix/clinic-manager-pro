package com.clinic.clinicmanager.DTO.response;

import com.clinic.clinicmanager.DTO.TrustedDeviceDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class MfaStatusResponse {
    private boolean enabled;
    private LocalDateTime enabledAt;
    private int remainingBackupCodes;
    private List<TrustedDeviceDTO> trustedDevices;
}

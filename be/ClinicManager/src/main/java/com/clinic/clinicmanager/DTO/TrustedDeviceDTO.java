package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.TrustedDevice;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TrustedDeviceDTO {
    private Long id;
    private String label;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime expiresAt;
    private String lastIp;
    private boolean current;

    public TrustedDeviceDTO(TrustedDevice device, boolean current) {
        this.id = device.getId();
        this.label = device.getLabel();
        this.createdAt = device.getCreatedAt();
        this.lastUsedAt = device.getLastUsedAt();
        this.expiresAt = device.getExpiresAt();
        this.lastIp = device.getLastIp();
        this.current = current;
    }
}

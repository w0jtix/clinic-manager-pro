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
public class ClinicDeviceDTO {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUSPECTED_COPY = "SUSPECTED_COPY";
    public static final String STATUS_REVOKED = "REVOKED";

    private Long id;
    private String name;
    private String label;
    private String lastIp;
    private LocalDateTime createdAt;
    private String registeredBy;
    private LocalDateTime lastSeenAt;
    private String lastSeenUsername;
    private String status;
    private String replacedByName;
    private LocalDateTime revokedAt;

    public ClinicDeviceDTO(TrustedDevice device, String replacedByName) {
        this.id = device.getId();
        this.name = device.getName();
        this.label = device.getLabel();
        this.lastIp = device.getLastIp();
        this.createdAt = device.getCreatedAt();
        this.registeredBy = device.getRegisteredBy();
        this.lastSeenAt = device.getLastUsedAt();
        this.lastSeenUsername = device.getLastSeenUsername();
        this.status = statusOf(device);
        this.replacedByName = replacedByName;
        this.revokedAt = device.getRevokedAt();
    }

    private static String statusOf(TrustedDevice device) {
        if (device.getRevokedAt() != null) {
            return STATUS_REVOKED;
        }
        if (device.getSuspectedCopyAt() != null) {
            return STATUS_SUSPECTED_COPY;
        }
        return STATUS_ACTIVE;
    }
}

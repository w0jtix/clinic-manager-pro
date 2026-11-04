package com.clinic.clinicmanager.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "device_registration_codes")
public class DeviceRegistrationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 6, updatable = false)
    private String code;

    @Column(name = "name", length = 100, updatable = false)
    private String name;

    @Column(name = "replace_device_id", updatable = false)
    private Long replaceDeviceId;

    @Column(name = "created_by", updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    public boolean isActive(LocalDateTime now) {
        return usedAt == null && cancelledAt == null && expiresAt.isAfter(now);
    }
}

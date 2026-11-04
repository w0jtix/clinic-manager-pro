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
@Table(name = "access_control_settings")
public class AccessControlSettings {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(name = "gate_enabled", nullable = false)
    private boolean gateEnabled;

    @Column(name = "paused_until")
    private LocalDateTime pausedUntil;

    @Column(name = "paused_by")
    private String pausedBy;

    @Column(name = "hours_enabled", nullable = false)
    private boolean hoursEnabled;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private String updatedBy;

    public boolean isGateActive(LocalDateTime now) {
        return gateEnabled && (pausedUntil == null || !pausedUntil.isAfter(now));
    }
}

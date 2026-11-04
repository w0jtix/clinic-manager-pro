package com.clinic.clinicmanager.model;

import com.clinic.clinicmanager.model.constants.KsefSyncStatusType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "ksef_sync_status")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KsefSyncStatus {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KsefSyncStatusType status;

    private LocalDateTime lastSyncedAt;
    private LocalDateTime lastAttemptAt;

    @Builder.Default
    @Column(nullable = false)
    private int lastNewCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private int lastFailedCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private boolean rateLimitHit = false;

    @Builder.Default
    @Column(name = "last_issued_new_count", nullable = false)
    private int lastIssuedNewCount = 0;

    @Builder.Default
    @Column(name = "last_issued_failed_count", nullable = false)
    private int lastIssuedFailedCount = 0;
}

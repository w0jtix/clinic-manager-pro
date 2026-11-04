package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.KsefSyncStatus;
import com.clinic.clinicmanager.model.constants.KsefSyncStatusType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class KsefSyncStatusDTO {

    private KsefSyncStatusType status;
    private LocalDateTime lastSyncedAt;
    private LocalDateTime lastAttemptAt;
    private int lastNewCount;
    private int lastFailedCount;
    private int lastIssuedNewCount;
    private int lastIssuedFailedCount;
    private boolean rateLimitHit;
    private KsefSyncSummaryDTO summary;

    public KsefSyncStatusDTO(KsefSyncStatus entity) {
        this.status = entity.getStatus();
        this.lastSyncedAt = entity.getLastSyncedAt();
        this.lastAttemptAt = entity.getLastAttemptAt();
        this.lastNewCount = entity.getLastNewCount();
        this.lastFailedCount = entity.getLastFailedCount();
        this.lastIssuedNewCount = entity.getLastIssuedNewCount();
        this.lastIssuedFailedCount = entity.getLastIssuedFailedCount();
        this.rateLimitHit = entity.isRateLimitHit();
    }
}

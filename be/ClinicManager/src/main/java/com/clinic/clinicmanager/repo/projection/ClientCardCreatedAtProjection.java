package com.clinic.clinicmanager.repo.projection;

import java.time.LocalDateTime;

public interface ClientCardCreatedAtProjection {
    Long getId();
    Long getClientId();
    LocalDateTime getCreatedAt();
}

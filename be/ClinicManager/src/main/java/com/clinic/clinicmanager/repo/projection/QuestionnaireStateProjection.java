package com.clinic.clinicmanager.repo.projection;

import java.time.LocalDateTime;

public interface QuestionnaireStateProjection {
    Long getVersion();
    LocalDateTime getUpdatedAt();
}

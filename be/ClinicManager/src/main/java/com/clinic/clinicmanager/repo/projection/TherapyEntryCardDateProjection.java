package com.clinic.clinicmanager.repo.projection;

import java.time.LocalDate;

public interface TherapyEntryCardDateProjection {
    Long getCardId();
    LocalDate getEntryDate();
}

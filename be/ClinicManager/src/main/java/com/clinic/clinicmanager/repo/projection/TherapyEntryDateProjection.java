package com.clinic.clinicmanager.repo.projection;

import java.time.LocalDate;

public interface TherapyEntryDateProjection {
    Long getId();
    LocalDate getEntryDate();
}

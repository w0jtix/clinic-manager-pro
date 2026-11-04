package com.clinic.clinicmanager.repo.projection;

import java.time.LocalDate;

public interface ClientMatchProjection {
    Long getId();
    String getFirstName();
    String getLastName();
    LocalDate getBirthDate();
    String getPhoneNumber();
    LocalDate getLastVisitDate();
    Boolean getIsDeleted();
    String getMatchReason();
}

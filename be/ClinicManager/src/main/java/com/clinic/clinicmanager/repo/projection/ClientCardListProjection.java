package com.clinic.clinicmanager.repo.projection;

import java.time.LocalDate;

public interface ClientCardListProjection {
    Long getId();
    String getFirstName();
    String getLastName();
    LocalDate getBirthDate();
    String getPhoneNumber();
    Boolean getIsDeleted();
    LocalDate getLastVisitDate();
    Boolean getLastVisitAbsence();
}

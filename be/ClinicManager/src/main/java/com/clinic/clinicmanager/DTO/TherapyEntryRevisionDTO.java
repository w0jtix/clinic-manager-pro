package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.TherapyEntryRevision;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TherapyEntryRevisionDTO {
    private LocalDate previousEntryDate;
    private String previousContent;
    private LocalDateTime changedAt;
    private String changedByName;

    public TherapyEntryRevisionDTO(TherapyEntryRevision revision, String changedByName) {
        this.previousEntryDate = revision.getPreviousEntryDate();
        this.previousContent = revision.getPreviousContent();
        this.changedAt = revision.getChangedAt();
        this.changedByName = changedByName;
    }
}

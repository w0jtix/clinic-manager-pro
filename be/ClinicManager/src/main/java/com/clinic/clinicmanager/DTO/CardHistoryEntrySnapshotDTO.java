package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.TherapyEntry;
import com.clinic.clinicmanager.model.TherapyEntryRevision;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CardHistoryEntrySnapshotDTO {
    private LocalDate entryDate;
    private String content;

    public CardHistoryEntrySnapshotDTO(TherapyEntry entry) {
        this.entryDate = entry.getEntryDate();
        this.content = entry.getContent();
    }

    public CardHistoryEntrySnapshotDTO(TherapyEntryRevision revision) {
        this.entryDate = revision.getPreviousEntryDate();
        this.content = revision.getPreviousContent();
    }
}

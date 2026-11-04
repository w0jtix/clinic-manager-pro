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
public class CardHistoryEditedEntryDTO {
    private LocalDate beforeEntryDate;
    private String beforeContent;
    private LocalDate afterEntryDate;
    private String afterContent;

    public CardHistoryEditedEntryDTO(TherapyEntryRevision before, TherapyEntryRevision after) {
        this(before.getPreviousEntryDate(), before.getPreviousContent(),
                after.getPreviousEntryDate(), after.getPreviousContent());
    }

    public CardHistoryEditedEntryDTO(TherapyEntryRevision before, TherapyEntry current) {
        this(before.getPreviousEntryDate(), before.getPreviousContent(),
                current.getEntryDate(), current.getContent());
    }
}

package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.constants.TimelineItemType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TimelineItemDTO {
    private TimelineItemType type;
    private LocalDate date;
    private TherapyEntryDTO entry;
    private List<VisitSummaryDTO> visits;
}

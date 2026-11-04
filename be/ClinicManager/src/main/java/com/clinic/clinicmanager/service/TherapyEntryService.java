package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.NewTherapyEntryDTO;
import com.clinic.clinicmanager.DTO.TherapyEntryRevisionDTO;
import com.clinic.clinicmanager.DTO.TherapyEntryUpdateDTO;
import com.clinic.clinicmanager.DTO.TherapyTimelineDTO;
import com.clinic.clinicmanager.DTO.TimelineItemDTO;

import java.time.LocalDate;
import java.util.List;

public interface TherapyEntryService {

    TimelineItemDTO createEntry(Long clientId, NewTherapyEntryDTO dto);

    TimelineItemDTO updateEntry(Long clientId, Long entryId, TherapyEntryUpdateDTO dto);

    TimelineItemDTO invalidateEntry(Long clientId, Long entryId);

    List<TherapyEntryRevisionDTO> getRevisions(Long clientId, Long entryId);

    TherapyTimelineDTO getTimeline(Long clientId, LocalDate beforeDate, int days);
}

package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.NewTherapyEntryDTO;
import com.clinic.clinicmanager.DTO.TherapyEntryRevisionDTO;
import com.clinic.clinicmanager.DTO.TherapyEntryUpdateDTO;
import com.clinic.clinicmanager.DTO.TherapyTimelineDTO;
import com.clinic.clinicmanager.DTO.TimelineItemDTO;
import com.clinic.clinicmanager.service.TherapyEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cards")
public class TherapyEntryController {

    private final TherapyEntryService therapyEntryService;

    @GetMapping("/{clientId}/timeline")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<TherapyTimelineDTO> getTimeline(
            @PathVariable(value = "clientId") Long clientId,
            @RequestParam(value = "beforeDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate beforeDate,
            @RequestParam(value = "days", defaultValue = "20") int days) {
        return new ResponseEntity<>(therapyEntryService.getTimeline(clientId, beforeDate, days), HttpStatus.OK);
    }

    @PostMapping("/{clientId}/therapy-entries")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<TimelineItemDTO> createEntry(
            @PathVariable(value = "clientId") Long clientId,
            @RequestBody(required = false) NewTherapyEntryDTO entry) {
        return new ResponseEntity<>(therapyEntryService.createEntry(clientId, entry), HttpStatus.CREATED);
    }

    @PutMapping("/{clientId}/therapy-entries/{entryId}")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<TimelineItemDTO> updateEntry(
            @PathVariable(value = "clientId") Long clientId,
            @PathVariable(value = "entryId") Long entryId,
            @RequestBody(required = false) TherapyEntryUpdateDTO entry) {
        return new ResponseEntity<>(therapyEntryService.updateEntry(clientId, entryId, entry), HttpStatus.OK);
    }

    @PostMapping("/{clientId}/therapy-entries/{entryId}/invalidate")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<TimelineItemDTO> invalidateEntry(
            @PathVariable(value = "clientId") Long clientId,
            @PathVariable(value = "entryId") Long entryId) {
        return new ResponseEntity<>(therapyEntryService.invalidateEntry(clientId, entryId), HttpStatus.OK);
    }

    @GetMapping("/{clientId}/therapy-entries/{entryId}/revisions")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<List<TherapyEntryRevisionDTO>> getRevisions(
            @PathVariable(value = "clientId") Long clientId,
            @PathVariable(value = "entryId") Long entryId) {
        return new ResponseEntity<>(therapyEntryService.getRevisions(clientId, entryId), HttpStatus.OK);
    }
}

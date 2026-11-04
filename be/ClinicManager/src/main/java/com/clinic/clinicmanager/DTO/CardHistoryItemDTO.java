package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.CardAccessLog;
import com.clinic.clinicmanager.model.constants.CardAccessAction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CardHistoryItemDTO {
    private Long id;
    private LocalDateTime at;
    private CardAccessAction action;
    private Long userId;
    private String userName;
    private String ipAddress;
    private String deviceName;
    private LocalDate entryDate;
    private CardHistoryEntrySnapshotDTO createdEntry;
    private CardHistoryEditedEntryDTO editedEntry;
    private CardHistoryEntrySnapshotDTO invalidatedEntry;
    private List<CardHistoryChangeDTO> changes;

    public CardHistoryItemDTO(CardAccessLog log, String userName, LocalDate entryDate,
                              CardHistoryEntrySnapshotDTO createdEntry, CardHistoryEditedEntryDTO editedEntry,
                              CardHistoryEntrySnapshotDTO invalidatedEntry, List<CardHistoryChangeDTO> changes) {
        this.id = log.getId();
        this.at = log.getAt();
        this.action = log.getAction();
        this.userId = log.getUserId();
        this.userName = userName;
        this.ipAddress = log.getIpAddress();
        this.deviceName = log.getDeviceName();
        this.entryDate = entryDate;
        this.createdEntry = createdEntry;
        this.editedEntry = editedEntry;
        this.invalidatedEntry = invalidatedEntry;
        this.changes = changes;
    }
}

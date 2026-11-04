package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.TherapyEntry;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TherapyEntryDTO {
    private Long id;
    private String content;
    private Long authorUserId;
    private String authorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean edited;
    private Boolean invalid;
    private LocalDateTime invalidatedAt;
    private Long version;
    private Boolean canEdit;

    public TherapyEntryDTO(TherapyEntry entry, String authorName, Long currentUserId) {
        this.id = entry.getId();
        this.content = entry.getContent();
        this.authorUserId = entry.getAuthorUserId();
        this.authorName = authorName;
        this.createdAt = entry.getCreatedAt();
        this.updatedAt = entry.getUpdatedAt();
        this.edited = entry.getUpdatedAt() != null;
        this.invalid = entry.isInvalid();
        this.invalidatedAt = entry.getInvalidatedAt();
        this.version = entry.getVersion();
        this.canEdit = !entry.isInvalid() && entry.getAuthorUserId().equals(currentUserId);
    }
}

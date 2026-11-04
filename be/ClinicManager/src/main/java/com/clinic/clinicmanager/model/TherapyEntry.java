package com.clinic.clinicmanager.model;

import com.clinic.clinicmanager.model.converter.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "therapy_entry")
public class TherapyEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false, updatable = false)
    private ClientCard card;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "content_enc", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "author_user_id", nullable = false, updatable = false)
    private Long authorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "invalidated_at")
    private LocalDateTime invalidatedAt;

    @Column(name = "invalidated_by_user_id")
    private Long invalidatedByUserId;

    public boolean isInvalid() {
        return invalidatedAt != null;
    }

    public void invalidate(LocalDateTime at, Long userId) {
        this.invalidatedAt = at;
        this.invalidatedByUserId = userId;
    }
}

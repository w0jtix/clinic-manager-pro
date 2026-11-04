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
@Table(name = "therapy_entry_revision")
public class TherapyEntryRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false, updatable = false)
    private TherapyEntry entry;

    @Column(name = "previous_entry_date", nullable = false, updatable = false)
    private LocalDate previousEntryDate;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "previous_content_enc", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String previousContent;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @Column(name = "changed_by_user_id", nullable = false, updatable = false)
    private Long changedByUserId;
}

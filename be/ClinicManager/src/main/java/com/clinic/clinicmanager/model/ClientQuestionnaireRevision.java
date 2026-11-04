package com.clinic.clinicmanager.model;

import com.clinic.clinicmanager.model.converter.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "client_questionnaire_revision")
public class ClientQuestionnaireRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false, updatable = false)
    private Client client;

    @Column(nullable = false, updatable = false)
    private Long version;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    @Column(name = "changed_by_user_id", updatable = false)
    private Long changedByUserId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "changes_enc", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String changes;
}

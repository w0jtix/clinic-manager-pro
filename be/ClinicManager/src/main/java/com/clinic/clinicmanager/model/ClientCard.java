package com.clinic.clinicmanager.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "client_card")
public class ClientCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false, unique = true)
    private Client client;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "questionnaire_version", nullable = false, insertable = false, updatable = false)
    @Builder.Default
    private Long questionnaireVersion = 0L;

    @Column(name = "questionnaire_updated_at", insertable = false, updatable = false)
    private LocalDateTime questionnaireUpdatedAt;

    @Column(name = "questionnaire_updated_by_user_id", insertable = false, updatable = false)
    private Long questionnaireUpdatedByUserId;
}

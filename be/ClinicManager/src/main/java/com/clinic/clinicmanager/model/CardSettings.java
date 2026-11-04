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
@Table(name = "card_settings")
public class CardSettings {

    @Id
    private Long id;

    @Column(name = "questionnaire_english_enabled", nullable = false)
    @Builder.Default
    private Boolean questionnaireEnglishEnabled = false;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

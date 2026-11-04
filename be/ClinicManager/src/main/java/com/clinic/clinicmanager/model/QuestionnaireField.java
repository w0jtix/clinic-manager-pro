package com.clinic.clinicmanager.model;

import com.clinic.clinicmanager.model.constants.QuestionnaireFieldType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "questionnaire_field")
public class QuestionnaireField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String label;

    @Column(name = "label_en", length = 300)
    private String labelEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QuestionnaireFieldType type;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(nullable = false)
    @Builder.Default
    private Boolean archived = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

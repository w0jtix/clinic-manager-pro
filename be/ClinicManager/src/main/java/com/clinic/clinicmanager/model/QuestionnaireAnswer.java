package com.clinic.clinicmanager.model;

import com.clinic.clinicmanager.model.converter.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "questionnaire_answer")
public class QuestionnaireAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "card_id", nullable = false)
    private ClientCard card;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "field_id", nullable = false)
    private QuestionnaireField field;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "value_enc", nullable = false, columnDefinition = "TEXT")
    private String value;
}

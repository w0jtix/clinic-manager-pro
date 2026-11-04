package com.clinic.clinicmanager.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClientQuestionnaireDTO {
    private Long version;
    private LocalDateTime updatedAt;
    private Boolean englishEnabled;
    private List<QuestionnaireItemDTO> items;
}

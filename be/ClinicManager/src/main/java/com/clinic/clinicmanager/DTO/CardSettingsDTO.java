package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.CardSettings;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CardSettingsDTO {
    private Long id;
    private Boolean questionnaireEnglishEnabled;

    public CardSettingsDTO(CardSettings settings) {
        this.id = settings.getId();
        this.questionnaireEnglishEnabled = settings.getQuestionnaireEnglishEnabled();
    }
}

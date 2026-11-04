package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.QuestionnaireSettingsDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireSettingsUpdateDTO;

public interface QuestionnaireFieldService {

    QuestionnaireSettingsDTO getSettings();

    QuestionnaireSettingsDTO updateSettings(QuestionnaireSettingsUpdateDTO settings);
}

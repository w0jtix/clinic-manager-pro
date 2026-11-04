package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ClientQuestionnaireDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireUpdateDTO;

public interface ClientQuestionnaireService {

    ClientQuestionnaireDTO getQuestionnaire(Long clientId);

    ClientQuestionnaireDTO updateQuestionnaire(Long clientId, QuestionnaireUpdateDTO dto);
}

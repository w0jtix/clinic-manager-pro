package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.repo.DrawingLayerRepo;
import com.clinic.clinicmanager.repo.QuestionnaireAnswerRepo;
import com.clinic.clinicmanager.repo.TherapyEntryRepo;
import com.clinic.clinicmanager.service.ClientCardContentChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientCardContentCheckerImpl implements ClientCardContentChecker {

    private final QuestionnaireAnswerRepo questionnaireAnswerRepo;
    private final DrawingLayerRepo drawingLayerRepo;
    private final TherapyEntryRepo therapyEntryRepo;

    @Override
    public boolean hasProtectedContent(Long clientId) {
        return questionnaireAnswerRepo.existsByCardClientId(clientId)
                || drawingLayerRepo.existsBySheetCardClientId(clientId)
                || therapyEntryRepo.existsByCardClientId(clientId);
    }
}

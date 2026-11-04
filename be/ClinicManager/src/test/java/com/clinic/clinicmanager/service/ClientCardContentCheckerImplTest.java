package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.repo.DrawingLayerRepo;
import com.clinic.clinicmanager.repo.QuestionnaireAnswerRepo;
import com.clinic.clinicmanager.repo.TherapyEntryRepo;
import com.clinic.clinicmanager.service.impl.ClientCardContentCheckerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientCardContentCheckerImplTest {

    @Mock QuestionnaireAnswerRepo questionnaireAnswerRepo;
    @Mock DrawingLayerRepo drawingLayerRepo;
    @Mock TherapyEntryRepo therapyEntryRepo;

    @InjectMocks ClientCardContentCheckerImpl checker;

    @Test
    void hasProtectedContent_trueWhenQuestionnaireHasAnswer() {
        when(questionnaireAnswerRepo.existsByCardClientId(1L)).thenReturn(true);

        assertThat(checker.hasProtectedContent(1L)).isTrue();
    }

    @Test
    void hasProtectedContent_trueWhenCardHasDrawingLayer() {
        when(questionnaireAnswerRepo.existsByCardClientId(3L)).thenReturn(false);
        when(drawingLayerRepo.existsBySheetCardClientId(3L)).thenReturn(true);

        assertThat(checker.hasProtectedContent(3L)).isTrue();
    }

    @Test
    void hasProtectedContent_trueWhenCardHasTherapyEntry() {
        when(questionnaireAnswerRepo.existsByCardClientId(4L)).thenReturn(false);
        when(drawingLayerRepo.existsBySheetCardClientId(4L)).thenReturn(false);
        when(therapyEntryRepo.existsByCardClientId(4L)).thenReturn(true);

        assertThat(checker.hasProtectedContent(4L)).isTrue();
    }

    @Test
    void hasProtectedContent_falseWithoutAnswersLayersAndEntries() {
        when(questionnaireAnswerRepo.existsByCardClientId(2L)).thenReturn(false);
        when(drawingLayerRepo.existsBySheetCardClientId(2L)).thenReturn(false);
        when(therapyEntryRepo.existsByCardClientId(2L)).thenReturn(false);

        assertThat(checker.hasProtectedContent(2L)).isFalse();
    }
}

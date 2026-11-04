package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.ClientQuestionnaireDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireAnswerDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireItemDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireUpdateDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.QuestionnaireConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.CardSettings;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientCard;
import com.clinic.clinicmanager.model.ClientQuestionnaireRevision;
import com.clinic.clinicmanager.model.QuestionnaireAnswer;
import com.clinic.clinicmanager.model.QuestionnaireField;
import com.clinic.clinicmanager.model.constants.CardAccessAction;
import com.clinic.clinicmanager.model.constants.QuestionnaireFieldType;
import com.clinic.clinicmanager.model.revision.QuestionnaireFieldChange;
import com.clinic.clinicmanager.model.revision.QuestionnaireFieldChange.AnswerSnapshot;
import com.clinic.clinicmanager.repo.CardSettingsRepo;
import com.clinic.clinicmanager.repo.ClientCardRepo;
import com.clinic.clinicmanager.repo.ClientQuestionnaireRevisionRepo;
import com.clinic.clinicmanager.repo.ClientRepo;
import com.clinic.clinicmanager.repo.QuestionnaireAnswerRepo;
import com.clinic.clinicmanager.repo.QuestionnaireFieldRepo;
import com.clinic.clinicmanager.repo.projection.QuestionnaireStateProjection;
import com.clinic.clinicmanager.service.impl.ClientQuestionnaireServiceImpl;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.clinic.clinicmanager.model.constants.QuestionnaireFieldType.CHECKBOX;
import static com.clinic.clinicmanager.model.constants.QuestionnaireFieldType.SECTION;
import static com.clinic.clinicmanager.model.constants.QuestionnaireFieldType.TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientQuestionnaireServiceImplTest {

    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2025, 10, 1, 9, 30);

    @Mock ClientRepo clientRepo;
    @Mock ClientCardRepo clientCardRepo;
    @Mock ClientCardService clientCardService;
    @Mock QuestionnaireFieldRepo fieldRepo;
    @Mock QuestionnaireAnswerRepo answerRepo;
    @Mock CardAccessLogService cardAccessLogService;
    @Mock CardSettingsRepo cardSettingsRepo;
    @Mock ClientQuestionnaireRevisionRepo revisionRepo;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks ClientQuestionnaireServiceImpl service;

    private final QuestionnaireField diabetes = field(1L, "Cukrzyca", CHECKBOX, 0, false);
    private final QuestionnaireField drugs = field(2L, "Leki", TEXT, 1, false);
    private final QuestionnaireField allergies = field(3L, "Alergie", TEXT, 0, true);
    private final QuestionnaireField oldCheckbox = field(4L, "Stare pole", CHECKBOX, 1, true);

    @BeforeEach
    void stubRevisionSave() {
        lenient().when(revisionRepo.save(any(ClientQuestionnaireRevision.class))).thenAnswer(invocation -> {
            ClientQuestionnaireRevision revision = invocation.getArgument(0);
            revision.setId(500L);
            return revision;
        });
    }

    private List<QuestionnaireFieldChange> savedRevisionChanges() throws Exception {
        ArgumentCaptor<ClientQuestionnaireRevision> captor = ArgumentCaptor.forClass(ClientQuestionnaireRevision.class);
        verify(revisionRepo).save(captor.capture());
        return objectMapper.readValue(captor.getValue().getChanges(), new TypeReference<>() {
        });
    }

    private static QuestionnaireField field(Long id, String label, QuestionnaireFieldType type, int order, boolean archived) {
        return QuestionnaireField.builder()
                .id(id).label(label).type(type).displayOrder(order).archived(archived)
                .build();
    }

    private Client client(boolean deleted) {
        return Client.builder().id(1L).firstName("Jan").lastName("Kowalski").isDeleted(deleted).build();
    }

    private ClientCard card() {
        return ClientCard.builder()
                .id(10L).client(client(false)).version(0L)
                .questionnaireVersion(3L).questionnaireUpdatedAt(UPDATED_AT)
                .build();
    }

    private QuestionnaireAnswer answer(ClientCard card, QuestionnaireField field, String value) {
        return QuestionnaireAnswer.builder().id(100L + field.getId()).card(card).field(field).value(value).build();
    }

    private static QuestionnaireStateProjection state(long version, LocalDateTime updatedAt) {
        return new QuestionnaireStateProjection() {
            @Override
            public Long getVersion() {
                return version;
            }

            @Override
            public LocalDateTime getUpdatedAt() {
                return updatedAt;
            }
        };
    }

    private void stubFields() {
        stubFields(diabetes, drugs, allergies, oldCheckbox);
    }

    private void stubFields(QuestionnaireField... fields) {
        when(fieldRepo.findAllByOrderByArchivedAscDisplayOrderAscIdAsc()).thenReturn(List.of(fields));
        lenient().when(cardSettingsRepo.getSettings()).thenReturn(cardSettings(false));
    }

    private static CardSettings cardSettings(boolean englishEnabled) {
        return CardSettings.builder().id(1L).questionnaireEnglishEnabled(englishEnabled).build();
    }

    private static QuestionnaireField withLabelEn(QuestionnaireField field, String labelEn) {
        field.setLabelEn(labelEn);
        return field;
    }

    private ClientCard stubCardForUpdate(long currentVersion, QuestionnaireAnswer... existing) {
        ClientCard card = card();
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client(false)));
        stubFields();
        when(clientCardService.getOrCreateCard(1L)).thenReturn(card);
        when(clientCardRepo.lockQuestionnaireState(10L)).thenReturn(state(currentVersion, UPDATED_AT));
        lenient().when(answerRepo.findByCardId(10L)).thenReturn(new ArrayList<>(Arrays.asList(existing)));
        return card;
    }

    private void stubClientForValidation() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client(false)));
        stubFields();
    }

    private static QuestionnaireAnswerDTO checkbox(Long fieldId, Boolean checked) {
        return new QuestionnaireAnswerDTO(fieldId, checked, null);
    }

    private static QuestionnaireAnswerDTO text(Long fieldId, String text) {
        return new QuestionnaireAnswerDTO(fieldId, null, text);
    }

    private static QuestionnaireItemDTO item(ClientQuestionnaireDTO dto, Long fieldId) {
        return dto.getItems().stream().filter(i -> i.getFieldId().equals(fieldId)).findFirst().orElseThrow();
    }

    @Test
    void getQuestionnaire_returnsActiveFields_andArchivedOnlyWithAnswers() {
        ClientCard card = card();
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client(false)));
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.of(card));
        when(answerRepo.findByCardId(10L)).thenReturn(List.of(
                answer(card, diabetes, "true"),
                answer(card, allergies, "penicylina")));
        stubFields();

        ClientQuestionnaireDTO result = service.getQuestionnaire(1L);

        assertThat(result.getVersion()).isEqualTo(3L);
        assertThat(result.getUpdatedAt()).isEqualTo(UPDATED_AT);
        assertThat(result.getItems()).extracting(QuestionnaireItemDTO::getFieldId).containsExactly(1L, 2L, 3L);
        assertThat(item(result, 1L).getChecked()).isTrue();
        assertThat(item(result, 1L).getText()).isNull();
        assertThat(item(result, 2L).getChecked()).isNull();
        assertThat(item(result, 2L).getText()).isNull();
        assertThat(item(result, 3L).getArchived()).isTrue();
        assertThat(item(result, 3L).getText()).isEqualTo("penicylina");
        assertThat(item(result, 3L).getChecked()).isNull();
        verify(cardAccessLogService).log(CardAccessAction.VIEW_QUESTIONNAIRE, 1L, "QUESTIONNAIRE", 10L);
    }

    @Test
    void getQuestionnaire_withoutCard_returnsEmptyValues_andDoesNotCreateCard() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client(false)));
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.empty());
        stubFields();

        ClientQuestionnaireDTO result = service.getQuestionnaire(1L);

        assertThat(result.getVersion()).isZero();
        assertThat(result.getUpdatedAt()).isNull();
        assertThat(result.getItems()).extracting(QuestionnaireItemDTO::getFieldId).containsExactly(1L, 2L);
        assertThat(item(result, 1L).getChecked()).isFalse();
        assertThat(item(result, 2L).getText()).isNull();
        verifyNoInteractions(clientCardService, answerRepo);
        verify(clientCardRepo, never()).insertIfAbsent(any(), any(), any());
        verify(cardAccessLogService).log(CardAccessAction.VIEW_QUESTIONNAIRE, 1L, "QUESTIONNAIRE", null);
    }

    @Test
    void getQuestionnaire_deletedClient_throwsNotFoundForUser() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client(true)));

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(false);

            assertThatThrownBy(() -> service.getQuestionnaire(1L)).isInstanceOf(ResourceNotFoundException.class);
        }

        verifyNoInteractions(cardAccessLogService, answerRepo);
    }

    @Test
    void getQuestionnaire_deletedClient_visibleForAdmin() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client(true)));
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.empty());
        stubFields();

        ClientQuestionnaireDTO result;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(true);

            result = service.getQuestionnaire(1L);
        }

        assertThat(result.getItems()).hasSize(2);
        verify(cardAccessLogService).log(CardAccessAction.VIEW_QUESTIONNAIRE, 1L, "QUESTIONNAIRE", null);
    }

    @Test
    void getQuestionnaire_unknownClient_throwsNotFound() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getQuestionnaire(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateQuestionnaire_deletedClient_throwsConflictBeforeAnything() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client(true)));

        assertThatThrownBy(() -> service.updateQuestionnaire(1L,
                new QuestionnaireUpdateDTO(3L, List.of(checkbox(1L, true)))))
                .isInstanceOf(ConflictException.class);

        verifyNoInteractions(clientCardService, clientCardRepo, fieldRepo, answerRepo, cardAccessLogService);
    }

    @Test
    void updateQuestionnaire_versionMismatch_throwsQuestionnaireConflict() {
        stubCardForUpdate(4L);

        assertThatThrownBy(() -> service.updateQuestionnaire(1L,
                new QuestionnaireUpdateDTO(3L, List.of(checkbox(1L, true)))))
                .isInstanceOf(QuestionnaireConflictException.class)
                .satisfies(ex -> assertThat(((QuestionnaireConflictException) ex).getCurrentVersion()).isEqualTo(4L));

        verify(answerRepo, never()).save(any());
        verify(answerRepo, never()).delete(any());
        verify(clientCardRepo, never()).bumpQuestionnaireVersion(any(), any(), any());
        verifyNoInteractions(cardAccessLogService);
        verify(revisionRepo, never()).save(any());
    }

    @Test
    void updateQuestionnaire_withoutChanges_doesNotBumpVersion() {
        ClientCard card = card();
        stubCardForUpdate(3L, answer(card, diabetes, "true"), answer(card, drugs, "Metformina"));

        ClientQuestionnaireDTO result = service.updateQuestionnaire(1L, new QuestionnaireUpdateDTO(3L, List.of(
                checkbox(1L, true), text(2L, "  Metformina "))));

        assertThat(result.getVersion()).isEqualTo(3L);
        assertThat(result.getUpdatedAt()).isEqualTo(UPDATED_AT);
        verify(answerRepo, never()).save(any());
        verify(answerRepo, never()).delete(any());
        verify(clientCardRepo, never()).bumpQuestionnaireVersion(any(), any(), any());
        verifyNoInteractions(cardAccessLogService);
        verify(revisionRepo, never()).save(any());
    }

    @Test
    void updateQuestionnaire_withChanges_bumpsVersion_andLogsWithoutContent() {
        ClientCard card = card();
        QuestionnaireAnswer existingDiabetes = answer(card, diabetes, "true");
        ClientCard lockedCard = stubCardForUpdate(3L, existingDiabetes);

        ClientQuestionnaireDTO result;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(SessionUtils::getUserIdFromSession).thenReturn(7L);

            result = service.updateQuestionnaire(1L, new QuestionnaireUpdateDTO(3L, List.of(
                    checkbox(1L, false), text(2L, "  Metformina  "))));
        }

        verify(answerRepo).delete(existingDiabetes);
        ArgumentCaptor<QuestionnaireAnswer> saved = ArgumentCaptor.forClass(QuestionnaireAnswer.class);
        verify(answerRepo).save(saved.capture());
        assertThat(saved.getValue().getField()).isSameAs(drugs);
        assertThat(saved.getValue().getCard()).isSameAs(lockedCard);
        assertThat(saved.getValue().getValue()).isEqualTo("Metformina");
        verify(clientCardRepo).bumpQuestionnaireVersion(eq(10L), any(LocalDateTime.class), eq(7L));
        verify(cardAccessLogService).log(CardAccessAction.UPDATE_QUESTIONNAIRE, 1L, "QUESTIONNAIRE_REVISION", 500L);
        assertThat(result.getVersion()).isEqualTo(4L);
        assertThat(result.getUpdatedAt()).isAfter(UPDATED_AT);
        assertThat(item(result, 1L).getChecked()).isFalse();
        assertThat(item(result, 2L).getText()).isEqualTo("Metformina");
    }

    @Test
    void updateQuestionnaire_changedText_updatesExistingAnswer() {
        ClientCard card = card();
        QuestionnaireAnswer existingDrugs = answer(card, drugs, "Metformina");
        stubCardForUpdate(3L, existingDrugs);

        service.updateQuestionnaire(1L, new QuestionnaireUpdateDTO(3L, List.of(text(2L, "Insulina"))));

        assertThat(existingDrugs.getValue()).isEqualTo("Insulina");
        verify(answerRepo).save(existingDrugs);
        verify(clientCardRepo).bumpQuestionnaireVersion(eq(10L), any(), any());
    }

    @Test
    void updateQuestionnaire_clearsAbsentActiveFields_butKeepsAbsentArchived() {
        ClientCard card = card();
        QuestionnaireAnswer existingDrugs = answer(card, drugs, "Metformina");
        QuestionnaireAnswer existingAllergies = answer(card, allergies, "penicylina");
        stubCardForUpdate(3L, existingDrugs, existingAllergies);

        ClientQuestionnaireDTO result = service.updateQuestionnaire(1L, new QuestionnaireUpdateDTO(3L, List.of()));

        verify(answerRepo).delete(existingDrugs);
        verify(answerRepo, never()).delete(existingAllergies);
        assertThat(result.getItems()).extracting(QuestionnaireItemDTO::getFieldId).containsExactly(1L, 2L, 3L);
        assertThat(item(result, 2L).getText()).isNull();
        assertThat(item(result, 3L).getText()).isEqualTo("penicylina");
        assertThat(result.getVersion()).isEqualTo(4L);
    }

    @Test
    void updateQuestionnaire_nullAnswers_treatedAsEmpty() {
        ClientCard card = card();
        QuestionnaireAnswer existingDiabetes = answer(card, diabetes, "true");
        stubCardForUpdate(3L, existingDiabetes);

        service.updateQuestionnaire(1L, new QuestionnaireUpdateDTO(3L, null));

        verify(answerRepo).delete(existingDiabetes);
    }

    @Test
    void updateQuestionnaire_acceptsAnswerForArchivedField() {
        ClientCard card = card();
        stubCardForUpdate(3L, answer(card, diabetes, "true"));

        ClientQuestionnaireDTO result = service.updateQuestionnaire(1L, new QuestionnaireUpdateDTO(3L, List.of(
                checkbox(1L, true), checkbox(4L, true))));

        ArgumentCaptor<QuestionnaireAnswer> saved = ArgumentCaptor.forClass(QuestionnaireAnswer.class);
        verify(answerRepo).save(saved.capture());
        assertThat(saved.getValue().getField()).isSameAs(oldCheckbox);
        assertThat(saved.getValue().getValue()).isEqualTo("true");
        assertThat(result.getItems()).extracting(QuestionnaireItemDTO::getFieldId).containsExactly(1L, 2L, 4L);
    }

    @Test
    void updateQuestionnaire_blankText_isStoredAsNoAnswer() {
        ClientCard card = card();
        QuestionnaireAnswer existingDrugs = answer(card, drugs, "Metformina");
        stubCardForUpdate(3L, existingDrugs);

        service.updateQuestionnaire(1L, new QuestionnaireUpdateDTO(3L, List.of(text(2L, "   "))));

        verify(answerRepo).delete(existingDrugs);
        verify(answerRepo, never()).save(any());
    }

    @Test
    void updateQuestionnaire_textOf2000Chars_isAccepted() {
        stubCardForUpdate(3L);

        service.updateQuestionnaire(1L, new QuestionnaireUpdateDTO(3L, List.of(text(2L, "a".repeat(2000)))));

        verify(answerRepo).save(any(QuestionnaireAnswer.class));
    }

    @Test
    void updateQuestionnaire_missingExpectedVersion_throws400() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client(false)));

        assertThatThrownBy(() -> service.updateQuestionnaire(1L, new QuestionnaireUpdateDTO(null, List.of())))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(clientCardService);
    }

    @Test
    void updateQuestionnaire_unknownField_throws400_withoutCreatingCard() {
        stubClientForValidation();

        assertThatThrownBy(() -> service.updateQuestionnaire(1L,
                new QuestionnaireUpdateDTO(0L, List.of(checkbox(99L, true)))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("99");
        verifyNoInteractions(clientCardService, answerRepo);
    }

    @Test
    void updateQuestionnaire_duplicateField_throws400() {
        stubClientForValidation();

        assertThatThrownBy(() -> service.updateQuestionnaire(1L,
                new QuestionnaireUpdateDTO(0L, List.of(checkbox(1L, true), checkbox(1L, false)))))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(clientCardService);
    }

    @Test
    void updateQuestionnaire_checkedForTextField_throws400() {
        stubClientForValidation();

        assertThatThrownBy(() -> service.updateQuestionnaire(1L,
                new QuestionnaireUpdateDTO(0L, List.of(new QuestionnaireAnswerDTO(2L, true, null)))))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(clientCardService);
    }

    @Test
    void updateQuestionnaire_textForCheckboxField_throws400() {
        stubClientForValidation();

        assertThatThrownBy(() -> service.updateQuestionnaire(1L,
                new QuestionnaireUpdateDTO(0L, List.of(new QuestionnaireAnswerDTO(1L, null, "tak")))))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(clientCardService);
    }

    @Test
    void updateQuestionnaire_textTooLong_throws400() {
        stubClientForValidation();

        assertThatThrownBy(() -> service.updateQuestionnaire(1L,
                new QuestionnaireUpdateDTO(0L, List.of(text(2L, "a".repeat(2001))))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("2000");
        verifyNoInteractions(clientCardService);
    }

    @Test
    void updateQuestionnaire_missingFieldId_throws400() {
        stubClientForValidation();

        assertThatThrownBy(() -> service.updateQuestionnaire(1L,
                new QuestionnaireUpdateDTO(0L, List.of(text(null, "x")))))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(clientCardService);
    }

    @Test
    void getQuestionnaire_returnsEnglishFlag_labelEn_andSectionsInPlace() {
        QuestionnaireField diseases = withLabelEn(field(5L, "Choroby", SECTION, 0, false), "Diseases");
        QuestionnaireField diabetesEn = withLabelEn(field(1L, "Cukrzyca", CHECKBOX, 1, false), "Diabetes");
        QuestionnaireField other = field(6L, "Inne", SECTION, 2, false);
        QuestionnaireField drugsNoEn = field(2L, "Leki", TEXT, 3, false);
        ClientCard card = card();
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client(false)));
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.of(card));
        when(answerRepo.findByCardId(10L)).thenReturn(List.of(
                answer(card, diabetesEn, "true"),
                answer(card, allergies, "penicylina")));
        stubFields(diseases, diabetesEn, other, drugsNoEn, allergies);
        when(cardSettingsRepo.getSettings()).thenReturn(cardSettings(true));

        ClientQuestionnaireDTO result = service.getQuestionnaire(1L);

        assertThat(result.getEnglishEnabled()).isTrue();
        assertThat(result.getItems()).extracting(QuestionnaireItemDTO::getFieldId).containsExactly(5L, 1L, 6L, 2L, 3L);
        assertThat(result.getItems()).extracting(QuestionnaireItemDTO::getType)
                .containsExactly(SECTION, CHECKBOX, SECTION, TEXT, TEXT);
        assertThat(result.getItems()).extracting(QuestionnaireItemDTO::getLabelEn)
                .containsExactly("Diseases", "Diabetes", null, null, null);
        assertThat(item(result, 5L).getChecked()).isNull();
        assertThat(item(result, 5L).getText()).isNull();
        assertThat(item(result, 5L).getArchived()).isFalse();
        assertThat(item(result, 6L).getChecked()).isNull();
        assertThat(item(result, 6L).getText()).isNull();
        assertThat(item(result, 1L).getChecked()).isTrue();
    }

    @Test
    void getQuestionnaire_returnsLabelEn_evenWhenEnglishDisabled() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client(false)));
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.empty());
        stubFields(withLabelEn(field(1L, "Cukrzyca", CHECKBOX, 0, false), "Diabetes"));

        ClientQuestionnaireDTO result = service.getQuestionnaire(1L);

        assertThat(result.getEnglishEnabled()).isFalse();
        assertThat(item(result, 1L).getLabelEn()).isEqualTo("Diabetes");
    }

    @Test
    void updateQuestionnaire_answerForSection_throws400_withoutCreatingCard() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client(false)));
        stubFields(field(5L, "Choroby", SECTION, 0, false), diabetes);

        assertThatThrownBy(() -> service.updateQuestionnaire(1L,
                new QuestionnaireUpdateDTO(0L, List.of(checkbox(5L, true)))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Tytuł sekcji \"Choroby\" nie przyjmuje odpowiedzi.");
        assertThatThrownBy(() -> service.updateQuestionnaire(1L,
                new QuestionnaireUpdateDTO(0L, List.of(new QuestionnaireAnswerDTO(5L, null, null)))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Tytuł sekcji \"Choroby\" nie przyjmuje odpowiedzi.");
        verifyNoInteractions(clientCardService, answerRepo);
    }

    @Test
    void updateQuestionnaire_sectionsAreNotTouched_whenClearingAbsentFields() {
        QuestionnaireField diseases = field(5L, "Choroby", SECTION, 0, false);
        ClientCard card = card();
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client(false)));
        stubFields(diseases, diabetes, drugs);
        when(clientCardService.getOrCreateCard(1L)).thenReturn(card);
        when(clientCardRepo.lockQuestionnaireState(10L)).thenReturn(state(3L, UPDATED_AT));
        when(answerRepo.findByCardId(10L)).thenReturn(new ArrayList<>());
        when(cardSettingsRepo.getSettings()).thenReturn(cardSettings(true));

        ClientQuestionnaireDTO result = service.updateQuestionnaire(1L, new QuestionnaireUpdateDTO(3L, List.of()));

        verify(answerRepo, never()).save(any());
        verify(answerRepo, never()).delete(any());
        verify(clientCardRepo, never()).bumpQuestionnaireVersion(any(), any(), any());
        assertThat(result.getVersion()).isEqualTo(3L);
        assertThat(result.getEnglishEnabled()).isTrue();
        assertThat(result.getItems()).extracting(QuestionnaireItemDTO::getFieldId).containsExactly(5L, 1L, 2L);
        assertThat(item(result, 5L).getChecked()).isNull();
        assertThat(item(result, 5L).getText()).isNull();
    }

    @Test
    void updateQuestionnaire_clearsAbsentFields_aroundSections() {
        QuestionnaireField diseases = field(5L, "Choroby", SECTION, 0, false);
        ClientCard card = card();
        QuestionnaireAnswer existingDrugs = answer(card, drugs, "Metformina");
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client(false)));
        stubFields(diseases, diabetes, drugs);
        when(clientCardService.getOrCreateCard(1L)).thenReturn(card);
        when(clientCardRepo.lockQuestionnaireState(10L)).thenReturn(state(3L, UPDATED_AT));
        when(answerRepo.findByCardId(10L)).thenReturn(new ArrayList<>(List.of(existingDrugs)));

        ClientQuestionnaireDTO result = service.updateQuestionnaire(1L,
                new QuestionnaireUpdateDTO(3L, List.of(checkbox(1L, true))));

        verify(answerRepo).delete(existingDrugs);
        ArgumentCaptor<QuestionnaireAnswer> saved = ArgumentCaptor.forClass(QuestionnaireAnswer.class);
        verify(answerRepo).save(saved.capture());
        assertThat(saved.getValue().getField()).isSameAs(diabetes);
        assertThat(result.getVersion()).isEqualTo(4L);
        assertThat(result.getItems()).extracting(QuestionnaireItemDTO::getFieldId).containsExactly(5L, 1L, 2L);
    }

    @Test
    void updateQuestionnaire_withChanges_savesRevisionWithOnlyChangedFields() throws Exception {
        ClientCard card = card();
        stubCardForUpdate(3L, answer(card, diabetes, "true"), answer(card, drugs, "Metformina"),
                answer(card, allergies, "penicylina"));

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(SessionUtils::getUserIdFromSession).thenReturn(7L);

            service.updateQuestionnaire(1L, new QuestionnaireUpdateDTO(3L, List.of(
                    checkbox(1L, true), text(2L, "Insulina"))));
        }

        ArgumentCaptor<ClientQuestionnaireRevision> captor = ArgumentCaptor.forClass(ClientQuestionnaireRevision.class);
        verify(revisionRepo).save(captor.capture());
        ClientQuestionnaireRevision revision = captor.getValue();
        assertThat(revision.getClient().getId()).isEqualTo(1L);
        assertThat(revision.getVersion()).isEqualTo(4L);
        assertThat(revision.getChangedByUserId()).isEqualTo(7L);
        assertThat(revision.getChangedAt()).isNotNull();

        List<QuestionnaireFieldChange> changes = objectMapper.readValue(revision.getChanges(), new TypeReference<>() {
        });
        assertThat(changes).containsExactly(new QuestionnaireFieldChange(2L, "Leki", "TEXT",
                new AnswerSnapshot(null, "Metformina"), new AnswerSnapshot(null, "Insulina")));
    }

    @Test
    void updateQuestionnaire_revisionSnapshots_newAndRemovedAnswers() throws Exception {
        ClientCard card = card();
        stubCardForUpdate(3L, answer(card, diabetes, "true"));

        service.updateQuestionnaire(1L, new QuestionnaireUpdateDTO(3L, List.of(
                checkbox(1L, false), text(2L, "Metformina"))));

        assertThat(savedRevisionChanges()).containsExactly(
                new QuestionnaireFieldChange(1L, "Cukrzyca", "YES_NO", new AnswerSnapshot(true, null), null),
                new QuestionnaireFieldChange(2L, "Leki", "TEXT", null, new AnswerSnapshot(null, "Metformina")));
    }

    @Test
    void updateQuestionnaire_revisionLabel_isSnapshotOfCurrentFieldLabel() throws Exception {
        stubCardForUpdate(3L);
        diabetes.setLabel("Cukrzyca typu 2");

        service.updateQuestionnaire(1L, new QuestionnaireUpdateDTO(3L, List.of(checkbox(1L, true))));

        assertThat(savedRevisionChanges()).extracting(QuestionnaireFieldChange::label).containsExactly("Cukrzyca typu 2");
    }
}

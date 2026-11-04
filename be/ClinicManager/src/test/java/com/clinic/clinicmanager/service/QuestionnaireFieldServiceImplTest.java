package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.CardSettingsDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireFieldDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireFieldUpdateDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireSettingsDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireSettingsUpdateDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.CardSettings;
import com.clinic.clinicmanager.model.QuestionnaireField;
import com.clinic.clinicmanager.model.constants.QuestionnaireFieldType;
import com.clinic.clinicmanager.repo.CardSettingsRepo;
import com.clinic.clinicmanager.repo.QuestionnaireAnswerRepo;
import com.clinic.clinicmanager.repo.QuestionnaireFieldRepo;
import com.clinic.clinicmanager.service.impl.QuestionnaireFieldServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static com.clinic.clinicmanager.model.constants.QuestionnaireFieldType.CHECKBOX;
import static com.clinic.clinicmanager.model.constants.QuestionnaireFieldType.SECTION;
import static com.clinic.clinicmanager.model.constants.QuestionnaireFieldType.TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestionnaireFieldServiceImplTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 9, 1, 10, 0);

    @Mock QuestionnaireFieldRepo fieldRepo;
    @Mock QuestionnaireAnswerRepo answerRepo;
    @Mock CardSettingsRepo cardSettingsRepo;
    @Mock AuditLogService auditLogService;

    @InjectMocks QuestionnaireFieldServiceImpl service;

    private QuestionnaireField field(Long id, String label, QuestionnaireFieldType type, int order, boolean archived) {
        return QuestionnaireField.builder()
                .id(id).label(label).type(type).displayOrder(order).archived(archived)
                .createdAt(CREATED).updatedAt(CREATED)
                .build();
    }

    private QuestionnaireFieldUpdateDTO item(Long id, String label, QuestionnaireFieldType type, boolean archived) {
        return new QuestionnaireFieldUpdateDTO(id, label, null, type, archived);
    }

    private QuestionnaireFieldUpdateDTO item(Long id, String label, String labelEn, QuestionnaireFieldType type) {
        return new QuestionnaireFieldUpdateDTO(id, label, labelEn, type, false);
    }

    private static QuestionnaireSettingsUpdateDTO settings(QuestionnaireFieldUpdateDTO... fields) {
        return new QuestionnaireSettingsUpdateDTO(false, List.of(fields));
    }

    private static QuestionnaireSettingsUpdateDTO settings(boolean englishEnabled, List<QuestionnaireFieldUpdateDTO> fields) {
        return new QuestionnaireSettingsUpdateDTO(englishEnabled, fields);
    }

    private CardSettings cardSettings(boolean englishEnabled) {
        return CardSettings.builder().id(1L).questionnaireEnglishEnabled(englishEnabled).updatedAt(CREATED).build();
    }

    private void stubExisting(Set<Long> withAnswers, QuestionnaireField... fields) {
        when(fieldRepo.findAll()).thenReturn(new ArrayList<>(Arrays.asList(fields)));
        when(answerRepo.findFieldIdsWithAnswers()).thenReturn(withAnswers);
        lenient().when(cardSettingsRepo.getSettings()).thenReturn(cardSettings(false));
    }

    private void stubSaveReturnsArgument() {
        when(fieldRepo.save(any(QuestionnaireField.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private List<QuestionnaireField> captureSaved(int times) {
        ArgumentCaptor<QuestionnaireField> captor = ArgumentCaptor.forClass(QuestionnaireField.class);
        verify(fieldRepo, times(times)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void getSettings_mapsHasAnswers_labelEn_andEnglishFlag_inRepoOrder() {
        QuestionnaireField diabetes = field(1L, "Cukrzyca", CHECKBOX, 1, false);
        diabetes.setLabelEn("Diabetes");
        when(answerRepo.findFieldIdsWithAnswers()).thenReturn(Set.of(1L, 3L));
        when(fieldRepo.findAllByOrderByArchivedAscDisplayOrderAscIdAsc()).thenReturn(List.of(
                field(2L, "Choroby", SECTION, 0, false),
                diabetes,
                field(3L, "Leki", TEXT, 0, true)));
        when(cardSettingsRepo.getSettings()).thenReturn(cardSettings(true));

        QuestionnaireSettingsDTO result = service.getSettings();

        assertThat(result.getEnglishEnabled()).isTrue();
        assertThat(result.getFields()).extracting(QuestionnaireFieldDTO::getId).containsExactly(2L, 1L, 3L);
        assertThat(result.getFields()).extracting(QuestionnaireFieldDTO::getType).containsExactly(SECTION, CHECKBOX, TEXT);
        assertThat(result.getFields()).extracting(QuestionnaireFieldDTO::getHasAnswers).containsExactly(false, true, true);
        assertThat(result.getFields()).extracting(QuestionnaireFieldDTO::getLabelEn).containsExactly(null, "Diabetes", null);
        assertThat(result.getFields().get(2).getArchived()).isTrue();
    }

    @Test
    void getSettings_emptyList_withDefaultFlag() {
        when(answerRepo.findFieldIdsWithAnswers()).thenReturn(Set.of());
        when(fieldRepo.findAllByOrderByArchivedAscDisplayOrderAscIdAsc()).thenReturn(List.of());
        when(cardSettingsRepo.getSettings()).thenReturn(CardSettings.builder().id(1L).build());

        QuestionnaireSettingsDTO result = service.getSettings();

        assertThat(result.getFields()).isEmpty();
        assertThat(result.getEnglishEnabled()).isFalse();
    }

    @Test
    void sectionDto_neverReportsAnswers() {
        assertThat(new QuestionnaireFieldDTO(field(1L, "Choroby", SECTION, 0, false), true).getHasAnswers()).isFalse();
    }

    @Test
    void updateSettings_createsNewFields_withTrimmedLabel_andIndexOrder() {
        stubExisting(Set.of());
        when(fieldRepo.save(any(QuestionnaireField.class))).thenAnswer(inv -> {
            QuestionnaireField f = inv.getArgument(0);
            f.setId(f.getDisplayOrder() + 100L);
            return f;
        });

        service.updateSettings(settings(
                item(null, "  Cukrzyca  ", CHECKBOX, false),
                item(null, "Leki", TEXT, false)));

        List<QuestionnaireField> saved = captureSaved(2);
        assertThat(saved).extracting(QuestionnaireField::getLabel).containsExactly("Cukrzyca", "Leki");
        assertThat(saved).extracting(QuestionnaireField::getDisplayOrder).containsExactly(0, 1);
        assertThat(saved).allSatisfy(f -> {
            assertThat(f.getArchived()).isFalse();
            assertThat(f.getLabelEn()).isNull();
            assertThat(f.getCreatedAt()).isNotNull();
            assertThat(f.getUpdatedAt()).isNotNull();
        });
        verify(auditLogService).logCreate(eq("QuestionnaireField"), eq(100L), eq("Cukrzyca"), any());
        verify(auditLogService).logCreate(eq("QuestionnaireField"), eq(101L), eq("Leki"), any());
    }

    @Test
    void updateSettings_createsSections_betweenFields_keepingListOrder() {
        stubExisting(Set.of());
        stubSaveReturnsArgument();

        service.updateSettings(settings(
                item(null, "Choroby", SECTION, false),
                item(null, "Cukrzyca", CHECKBOX, false),
                item(null, "Inne", SECTION, false),
                item(null, "Uwagi", TEXT, false)));

        List<QuestionnaireField> saved = captureSaved(4);
        assertThat(saved).extracting(QuestionnaireField::getType).containsExactly(SECTION, CHECKBOX, SECTION, TEXT);
        assertThat(saved).extracting(QuestionnaireField::getDisplayOrder).containsExactly(0, 1, 2, 3);
        assertThat(saved).extracting(QuestionnaireField::getArchived).containsOnly(false);
    }

    @Test
    void updateSettings_reordersAndArchives_existingFields() {
        QuestionnaireField a = field(1L, "Cukrzyca", CHECKBOX, 0, false);
        QuestionnaireField b = field(2L, "Leki", TEXT, 1, false);
        stubExisting(Set.of(1L), a, b);
        stubSaveReturnsArgument();

        service.updateSettings(settings(
                item(2L, "Leki", TEXT, false),
                item(1L, "Cukrzyca", CHECKBOX, true)));

        assertThat(b.getDisplayOrder()).isZero();
        assertThat(a.getDisplayOrder()).isEqualTo(1);
        assertThat(a.getArchived()).isTrue();
        assertThat(a.getUpdatedAt()).isAfter(CREATED);
        verify(fieldRepo, never()).delete(any());
        verify(auditLogService, times(2)).logUpdate(eq("QuestionnaireField"), any(), anyString(), any(), any());
    }

    @Test
    void updateSettings_unchangedFieldAndFlag_isNotSavedOrAudited() {
        QuestionnaireField a = field(1L, "Cukrzyca", CHECKBOX, 0, false);
        a.setLabelEn("Diabetes");
        stubExisting(Set.of(), a);

        service.updateSettings(settings(item(1L, " Cukrzyca ", " Diabetes ", CHECKBOX)));

        verify(fieldRepo, never()).save(any());
        verify(cardSettingsRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
        assertThat(a.getUpdatedAt()).isEqualTo(CREATED);
    }

    @Test
    void updateSettings_hardDeletesOmittedField_withoutAnswers() {
        QuestionnaireField a = field(1L, "Cukrzyca", CHECKBOX, 0, false);
        QuestionnaireField b = field(2L, "Leki", TEXT, 1, false);
        stubExisting(Set.of(1L), a, b);

        service.updateSettings(settings(item(1L, "Cukrzyca", CHECKBOX, false)));

        verify(fieldRepo).delete(b);
        verify(fieldRepo).flush();
        verify(fieldRepo, never()).delete(a);
        verify(auditLogService).logDelete(eq("QuestionnaireField"), eq(2L), eq("Leki"), any());
    }

    @Test
    void updateSettings_hardDeletesOmittedSection() {
        QuestionnaireField section = field(1L, "Choroby", SECTION, 0, false);
        QuestionnaireField a = field(2L, "Cukrzyca", CHECKBOX, 1, false);
        stubExisting(Set.of(2L), section, a);
        stubSaveReturnsArgument();

        service.updateSettings(settings(item(2L, "Cukrzyca", CHECKBOX, false)));

        verify(fieldRepo).delete(section);
        verify(auditLogService).logDelete(eq("QuestionnaireField"), eq(1L), eq("Choroby"), any());
        assertThat(a.getDisplayOrder()).isZero();
    }

    @Test
    void updateSettings_omittedFieldWithAnswers_throwsConflict() {
        stubExisting(Set.of(2L),
                field(1L, "Cukrzyca", CHECKBOX, 0, false),
                field(2L, "Leki", TEXT, 1, false));

        assertThatThrownBy(() -> service.updateSettings(settings(item(1L, "Cukrzyca", CHECKBOX, false))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Pole ma odpowiedzi w kartach - zarchiwizuj je zamiast usuwać.");

        verify(fieldRepo, never()).delete(any());
        verify(fieldRepo, never()).save(any());
        verify(cardSettingsRepo, never()).save(any());
    }

    @Test
    void updateSettings_typeChangeOnFieldWithAnswers_throwsConflict() {
        stubExisting(Set.of(1L), field(1L, "Cukrzyca", CHECKBOX, 0, false));

        assertThatThrownBy(() -> service.updateSettings(settings(item(1L, "Cukrzyca", TEXT, false))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Nie można zmienić typu pola, które ma już odpowiedzi w kartach.");

        verify(fieldRepo, never()).save(any());
    }

    @Test
    void updateSettings_changeToSectionOnFieldWithAnswers_throwsConflict() {
        stubExisting(Set.of(1L), field(1L, "Cukrzyca", CHECKBOX, 0, false));

        assertThatThrownBy(() -> service.updateSettings(settings(item(1L, "Cukrzyca", SECTION, false))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Nie można zmienić typu pola, które ma już odpowiedzi w kartach.");
    }

    @Test
    void updateSettings_typeChangeWithoutAnswers_isAllowed_includingSection() {
        QuestionnaireField a = field(1L, "Cukrzyca", CHECKBOX, 0, false);
        QuestionnaireField b = field(2L, "Choroby", SECTION, 1, false);
        stubExisting(Set.of(), a, b);
        stubSaveReturnsArgument();

        service.updateSettings(settings(
                item(1L, "Cukrzyca", SECTION, false),
                item(2L, "Choroby", TEXT, false)));

        assertThat(a.getType()).isEqualTo(SECTION);
        assertThat(b.getType()).isEqualTo(TEXT);
    }

    @Test
    void updateSettings_archivedSection_throws400() {
        stubExisting(Set.of(), field(1L, "Choroby", SECTION, 0, false));

        assertThatThrownBy(() -> service.updateSettings(settings(item(1L, "Choroby", SECTION, true))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Tytułu sekcji nie archiwizuje się - usuń go z listy.");
        verify(fieldRepo, never()).save(any());
    }

    @Test
    void updateSettings_concurrentAnswerOnDeletedField_mapsToConflict() {
        stubExisting(Set.of(), field(1L, "Cukrzyca", CHECKBOX, 0, false));
        doThrow(new DataIntegrityViolationException("fk")).when(fieldRepo).flush();

        assertThatThrownBy(() -> service.updateSettings(settings()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Pole ma odpowiedzi w kartach - zarchiwizuj je zamiast usuwać.");
    }

    @Test
    void updateSettings_blankLabel_throws400() {
        stubExisting(Set.of());

        assertThatThrownBy(() -> service.updateSettings(settings(item(null, "   ", CHECKBOX, false))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nazwa pola nie może być pusta.");
        assertThatThrownBy(() -> service.updateSettings(settings(item(null, null, CHECKBOX, false))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateSettings_blankSectionTitle_throws400() {
        stubExisting(Set.of());

        assertThatThrownBy(() -> service.updateSettings(settings(item(null, "  ", SECTION, false))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Tytuł sekcji nie może być pusty.");
    }

    @Test
    void updateSettings_labelTooLong_throws400() {
        stubExisting(Set.of());

        assertThatThrownBy(() -> service.updateSettings(settings(item(null, "a".repeat(301), CHECKBOX, false))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nazwa pola może mieć maksymalnie 300 znaków.");
        assertThatThrownBy(() -> service.updateSettings(settings(item(null, "a".repeat(301), SECTION, false))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Tytuł sekcji może mieć maksymalnie 300 znaków.");
    }

    @Test
    void updateSettings_labelOf300CharsAfterTrim_isAccepted() {
        stubExisting(Set.of());
        stubSaveReturnsArgument();

        service.updateSettings(settings(item(null, " " + "a".repeat(300) + " ", CHECKBOX, false)));

        verify(fieldRepo).save(any(QuestionnaireField.class));
    }

    @Test
    void updateSettings_missingType_throws400() {
        stubExisting(Set.of());

        assertThatThrownBy(() -> service.updateSettings(settings(item(null, "Cukrzyca", null, false))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("typ");
    }

    @Test
    void updateSettings_unknownId_throws400() {
        stubExisting(Set.of(), field(1L, "Cukrzyca", CHECKBOX, 0, false));

        assertThatThrownBy(() -> service.updateSettings(settings(item(99L, "Cukrzyca", CHECKBOX, false))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("99");
    }

    @Test
    void updateSettings_duplicateId_throws400() {
        stubExisting(Set.of(), field(1L, "Cukrzyca", CHECKBOX, 0, false));

        assertThatThrownBy(() -> service.updateSettings(settings(
                item(1L, "Cukrzyca", CHECKBOX, false),
                item(1L, "Inna", CHECKBOX, true))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateSettings_duplicateActiveLabel_beforeFirstSection_caseInsensitive_throws400() {
        stubExisting(Set.of());

        assertThatThrownBy(() -> service.updateSettings(settings(
                item(null, "Cukrzyca", CHECKBOX, false),
                item(null, " CUKRZYCA ", TEXT, false))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Pole \"CUKRZYCA\" występuje więcej niż raz przed pierwszym tytułem sekcji.");
        verify(fieldRepo, never()).save(any());
    }

    @Test
    void updateSettings_sameLabelInDifferentSections_isAllowed() {
        stubExisting(Set.of());
        stubSaveReturnsArgument();

        service.updateSettings(settings(
                item(null, "Brodawki wirusowe", "Viral warts", CHECKBOX),
                item(null, "Wywiad", SECTION, false),
                item(null, "Brodawki wirusowe", "Viral warts", CHECKBOX),
                item(null, "Badanie podologiczne", SECTION, false),
                item(null, " brodawki wirusowe ", " VIRAL WARTS ", CHECKBOX)));

        verify(fieldRepo, times(5)).save(any(QuestionnaireField.class));
    }

    @Test
    void updateSettings_duplicateLabelInSameSection_throws400_withSectionTitle() {
        stubExisting(Set.of());

        assertThatThrownBy(() -> service.updateSettings(settings(
                item(null, "Wywiad", SECTION, false),
                item(null, "Brodawki wirusowe", CHECKBOX, false),
                item(null, "Badanie podologiczne", SECTION, false),
                item(null, "Brodawki wirusowe", CHECKBOX, false),
                item(null, " BRODAWKI WIRUSOWE ", TEXT, false))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Pole \"BRODAWKI WIRUSOWE\" występuje więcej niż raz w sekcji \"Badanie podologiczne\".");
        verify(fieldRepo, never()).save(any());
    }

    @Test
    void updateSettings_duplicateLabelEnInSameSection_throws400_withSectionTitle() {
        stubExisting(Set.of());

        assertThatThrownBy(() -> service.updateSettings(settings(
                item(null, "Wywiad", "Interview", SECTION),
                item(null, "Brodawki", "Warts", CHECKBOX),
                item(null, "Kurzajki", " warts ", CHECKBOX))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nazwa angielska \"warts\" występuje więcej niż raz w sekcji \"Wywiad\".");
        verify(fieldRepo, never()).save(any());
    }

    @Test
    void updateSettings_archivedFieldInSection_isIgnoredByDuplicateCheck() {
        stubExisting(Set.of());
        stubSaveReturnsArgument();

        service.updateSettings(settings(
                item(null, "Wywiad", SECTION, false),
                item(null, "Brodawki wirusowe", CHECKBOX, false),
                item(null, "Brodawki wirusowe", CHECKBOX, true)));

        verify(fieldRepo, times(3)).save(any(QuestionnaireField.class));
    }

    @Test
    void updateSettings_duplicateSectionTitle_throws400() {
        stubExisting(Set.of());

        assertThatThrownBy(() -> service.updateSettings(settings(
                item(null, "Inne", SECTION, false),
                item(null, "Cukrzyca", CHECKBOX, false),
                item(null, " inne ", SECTION, false))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Tytuł sekcji \"inne\" występuje w ankiecie więcej niż raz.");
        verify(fieldRepo, never()).save(any());
    }

    @Test
    void updateSettings_sameLabelForSectionAndField_isAllowed() {
        stubExisting(Set.of());
        stubSaveReturnsArgument();

        service.updateSettings(settings(
                item(null, "Inne", "Other", SECTION),
                item(null, "Inne", "Other", TEXT)));

        verify(fieldRepo, times(2)).save(any(QuestionnaireField.class));
    }

    @Test
    void updateSettings_duplicateLabel_allowedWhenOneArchived() {
        stubExisting(Set.of());
        stubSaveReturnsArgument();

        service.updateSettings(settings(
                item(null, "Cukrzyca", CHECKBOX, false),
                item(null, "cukrzyca", CHECKBOX, true)));

        verify(fieldRepo, times(2)).save(any(QuestionnaireField.class));
    }

    @Test
    void updateSettings_labelEn_isTrimmed_andBlankStoredAsNull() {
        stubExisting(Set.of());
        stubSaveReturnsArgument();

        service.updateSettings(settings(
                item(null, "Cukrzyca", "  Diabetes  ", CHECKBOX),
                item(null, "Leki", "   ", TEXT),
                item(null, "Uwagi", null, TEXT)));

        assertThat(captureSaved(3)).extracting(QuestionnaireField::getLabelEn)
                .containsExactly("Diabetes", null, null);
    }

    @Test
    void updateSettings_labelEnChange_updatesAndAuditsField() {
        QuestionnaireField a = field(1L, "Cukrzyca", CHECKBOX, 0, false);
        stubExisting(Set.of(), a);
        stubSaveReturnsArgument();

        service.updateSettings(settings(item(1L, "Cukrzyca", "Diabetes", CHECKBOX)));

        assertThat(a.getLabelEn()).isEqualTo("Diabetes");
        ArgumentCaptor<QuestionnaireFieldDTO> oldSnapshot = ArgumentCaptor.forClass(QuestionnaireFieldDTO.class);
        ArgumentCaptor<QuestionnaireFieldDTO> newSnapshot = ArgumentCaptor.forClass(QuestionnaireFieldDTO.class);
        verify(auditLogService).logUpdate(eq("QuestionnaireField"), eq(1L), eq("Cukrzyca"),
                oldSnapshot.capture(), newSnapshot.capture());
        assertThat(oldSnapshot.getValue().getLabelEn()).isNull();
        assertThat(newSnapshot.getValue().getLabelEn()).isEqualTo("Diabetes");
    }

    @Test
    void updateSettings_labelEnIsStored_evenWhenEnglishDisabled() {
        QuestionnaireField a = field(1L, "Cukrzyca", CHECKBOX, 0, false);
        a.setLabelEn("Diabetes");
        stubExisting(Set.of(), a);
        stubSaveReturnsArgument();

        service.updateSettings(settings(false, List.of(item(1L, "Cukrzyca", "Diabetes mellitus", CHECKBOX))));

        assertThat(a.getLabelEn()).isEqualTo("Diabetes mellitus");
    }

    @Test
    void updateSettings_labelEnNotRequired_whenEnglishEnabled() {
        stubExisting(Set.of());
        stubSaveReturnsArgument();
        when(cardSettingsRepo.save(any(CardSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateSettings(settings(true, List.of(item(null, "Cukrzyca", CHECKBOX, false))));

        assertThat(captureSaved(1).get(0).getLabelEn()).isNull();
    }

    @Test
    void updateSettings_labelEnTooLong_throws400() {
        stubExisting(Set.of());

        assertThatThrownBy(() -> service.updateSettings(settings(item(null, "Cukrzyca", "a".repeat(301), CHECKBOX))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nazwa angielska może mieć maksymalnie 300 znaków.");
    }

    @Test
    void updateSettings_labelEnOf300CharsAfterTrim_isAccepted() {
        stubExisting(Set.of());
        stubSaveReturnsArgument();

        service.updateSettings(settings(item(null, "Cukrzyca", " " + "a".repeat(300) + " ", CHECKBOX)));

        assertThat(captureSaved(1).get(0).getLabelEn()).hasSize(300);
    }

    @Test
    void updateSettings_duplicateLabelEn_amongFields_beforeFirstSection_throws400() {
        stubExisting(Set.of());

        assertThatThrownBy(() -> service.updateSettings(settings(
                item(null, "Cukrzyca", "Diabetes", CHECKBOX),
                item(null, "Choroba cukrowa", " DIABETES ", TEXT))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nazwa angielska \"DIABETES\" występuje więcej niż raz przed pierwszym tytułem sekcji.");
    }

    @Test
    void updateSettings_duplicateLabelEn_amongSections_throws400() {
        stubExisting(Set.of());

        assertThatThrownBy(() -> service.updateSettings(settings(
                item(null, "Inne", "Other", SECTION),
                item(null, "Pozostałe", "other", SECTION))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nazwa angielska \"other\" występuje w ankiecie więcej niż raz.");
    }

    @Test
    void updateSettings_duplicateLabelEn_allowedWhenBlankOrArchived() {
        stubExisting(Set.of());
        stubSaveReturnsArgument();

        service.updateSettings(settings(false, List.of(
                item(null, "Cukrzyca", "Diabetes", CHECKBOX),
                new QuestionnaireFieldUpdateDTO(null, "Stara cukrzyca", "Diabetes", CHECKBOX, true),
                item(null, "Leki", " ", TEXT),
                item(null, "Uwagi", "", TEXT))));

        verify(fieldRepo, times(4)).save(any(QuestionnaireField.class));
    }

    @Test
    void updateSettings_enablesEnglish_savesFlag_andAudits() {
        CardSettings current = cardSettings(false);
        stubExisting(Set.of());
        when(cardSettingsRepo.getSettings()).thenReturn(current);
        when(cardSettingsRepo.save(any(CardSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        QuestionnaireSettingsDTO result = service.updateSettings(settings(true, List.of()));

        assertThat(current.getQuestionnaireEnglishEnabled()).isTrue();
        assertThat(current.getUpdatedAt()).isAfter(CREATED);
        verify(cardSettingsRepo).save(current);
        ArgumentCaptor<CardSettingsDTO> oldSnapshot = ArgumentCaptor.forClass(CardSettingsDTO.class);
        ArgumentCaptor<CardSettingsDTO> newSnapshot = ArgumentCaptor.forClass(CardSettingsDTO.class);
        verify(auditLogService).logUpdate(eq("CardSettings"), eq(1L), isNull(),
                oldSnapshot.capture(), newSnapshot.capture());
        assertThat(oldSnapshot.getValue().getQuestionnaireEnglishEnabled()).isFalse();
        assertThat(newSnapshot.getValue().getQuestionnaireEnglishEnabled()).isTrue();
        assertThat(result.getEnglishEnabled()).isTrue();
    }

    @Test
    void updateSettings_missingSettingsRow_isCreatedWithId1() {
        stubExisting(Set.of());
        when(cardSettingsRepo.getSettings()).thenReturn(CardSettings.builder().id(1L).build());
        when(cardSettingsRepo.save(any(CardSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateSettings(settings(true, List.of()));

        ArgumentCaptor<CardSettings> saved = ArgumentCaptor.forClass(CardSettings.class);
        verify(cardSettingsRepo).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(1L);
        assertThat(saved.getValue().getQuestionnaireEnglishEnabled()).isTrue();
        assertThat(saved.getValue().getUpdatedAt()).isNotNull();
    }

    @Test
    void updateSettings_nullEnglishEnabled_throws400() {
        assertThatThrownBy(() -> service.updateSettings(new QuestionnaireSettingsUpdateDTO(null, List.of())))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Brak ustawienia wersji angielskiej ankiety.");
        verifyNoInteractions(fieldRepo, cardSettingsRepo, auditLogService);
    }

    @Test
    void updateSettings_flagIsNotSaved_whenFieldValidationFails() {
        stubExisting(Set.of());

        assertThatThrownBy(() -> service.updateSettings(settings(true, List.of(item(null, " ", CHECKBOX, false)))))
                .isInstanceOf(ValidationException.class);
        verify(cardSettingsRepo, never()).save(any());
    }

    @Test
    void updateSettings_moreThan150Elements_throws400() {
        List<QuestionnaireFieldUpdateDTO> tooMany = IntStream.range(0, 151)
                .mapToObj(i -> item(null, "Pole " + i, i % 10 == 0 ? SECTION : CHECKBOX, false))
                .toList();

        assertThatThrownBy(() -> service.updateSettings(settings(false, tooMany)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Ankieta może mieć maksymalnie 150 elementów (pól i tytułów sekcji).");
        verifyNoInteractions(fieldRepo);
    }

    @Test
    void updateSettings_exactly150Elements_isAccepted() {
        stubExisting(Set.of());
        stubSaveReturnsArgument();
        List<QuestionnaireFieldUpdateDTO> max = IntStream.range(0, 150)
                .mapToObj(i -> item(null, "Pole " + i, i % 10 == 0 ? SECTION : CHECKBOX, false))
                .toList();

        service.updateSettings(settings(false, max));

        verify(fieldRepo, times(150)).save(any(QuestionnaireField.class));
    }

    @Test
    void updateSettings_nullFieldsOrBody_throws400() {
        assertThatThrownBy(() -> service.updateSettings(new QuestionnaireSettingsUpdateDTO(false, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Brak listy pól ankiety.");
        assertThatThrownBy(() -> service.updateSettings(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void updateSettings_returnsFreshSettings() {
        stubExisting(Set.of());
        when(fieldRepo.findAllByOrderByArchivedAscDisplayOrderAscIdAsc())
                .thenReturn(List.of(field(5L, "Leki", TEXT, 0, false)));

        QuestionnaireSettingsDTO result = service.updateSettings(settings());

        assertThat(result.getFields()).extracting(QuestionnaireFieldDTO::getId).containsExactly(5L);
        assertThat(result.getEnglishEnabled()).isFalse();
    }
}

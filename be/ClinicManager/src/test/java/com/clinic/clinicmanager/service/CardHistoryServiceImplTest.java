package com.clinic.clinicmanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.CardHistoryChangeDTO;
import com.clinic.clinicmanager.DTO.CardHistoryEditedEntryDTO;
import com.clinic.clinicmanager.DTO.CardHistoryEntrySnapshotDTO;
import com.clinic.clinicmanager.DTO.CardHistoryItemDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.CardAccessLog;
import com.clinic.clinicmanager.model.ClientCardDataRevision;
import com.clinic.clinicmanager.model.ClientQuestionnaireRevision;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.TherapyEntry;
import com.clinic.clinicmanager.model.TherapyEntryRevision;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.CardAccessAction;
import com.clinic.clinicmanager.model.revision.ClientDataFieldChange;
import com.clinic.clinicmanager.model.revision.QuestionnaireFieldChange;
import com.clinic.clinicmanager.model.revision.QuestionnaireFieldChange.AnswerSnapshot;
import com.clinic.clinicmanager.repo.CardAccessLogRepo;
import com.clinic.clinicmanager.repo.ClientCardDataRevisionRepo;
import com.clinic.clinicmanager.repo.ClientQuestionnaireRevisionRepo;
import com.clinic.clinicmanager.repo.ClientRepo;
import com.clinic.clinicmanager.repo.TherapyEntryRepo;
import com.clinic.clinicmanager.repo.TherapyEntryRevisionRepo;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.repo.projection.CardAccessLogResourceProjection;
import com.clinic.clinicmanager.repo.projection.TherapyEntryDateProjection;
import com.clinic.clinicmanager.repo.projection.TherapyEntryRevisionRefProjection;
import com.clinic.clinicmanager.service.impl.CardHistoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CardHistoryServiceImplTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 13, 10, 15);

    @Mock ClientRepo clientRepo;
    @Mock CardAccessLogRepo cardAccessLogRepo;
    @Mock ClientQuestionnaireRevisionRepo questionnaireRevisionRepo;
    @Mock ClientCardDataRevisionRepo dataRevisionRepo;
    @Mock TherapyEntryRepo entryRepo;
    @Mock TherapyEntryRevisionRepo entryRevisionRepo;
    @Mock UserRepo userRepo;
    @Mock CardAccessLogService cardAccessLogService;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks CardHistoryServiceImpl service;

    private static CardAccessLog log(Long id, CardAccessAction action, Long userId, String resourceType, Long resourceId) {
        return CardAccessLog.builder()
                .id(id).at(AT.minusMinutes(id)).userId(userId).ipAddress("1.2.3.4")
                .action(action).clientId(1L).resourceType(resourceType).resourceId(resourceId)
                .build();
    }

    private void stubLogs(CardAccessLog... logs) {
        when(clientRepo.existsById(1L)).thenReturn(true);
        when(cardAccessLogRepo.findHistory(eq(1L), anyCollection(), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(logs), invocation.getArgument(2), logs.length));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private static TherapyEntryDateProjection entryDate(Long id, LocalDate date) {
        return new TherapyEntryDateProjection() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public LocalDate getEntryDate() {
                return date;
            }
        };
    }

    private static TherapyEntryRevisionRefProjection revisionRef(Long id, Long entryId) {
        return new TherapyEntryRevisionRefProjection() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Long getEntryId() {
                return entryId;
            }
        };
    }

    private static CardAccessLogResourceProjection logRef(Long id, Long resourceId) {
        return new CardAccessLogResourceProjection() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public Long getResourceId() {
                return resourceId;
            }
        };
    }

    private static TherapyEntryRevision revision(Long id, Long entryId, LocalDate previousDate, String previousContent) {
        return TherapyEntryRevision.builder().id(id).entry(TherapyEntry.builder().id(entryId).build())
                .previousEntryDate(previousDate).previousContent(previousContent)
                .changedAt(AT).changedByUserId(4L).build();
    }

    @Test
    void getHistory_unknownClient_throwsNotFound_andDoesNotLogAccess() {
        when(clientRepo.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.getHistory(1L, 0, 30)).isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(cardAccessLogRepo, cardAccessLogService);
    }

    @Test
    void getHistory_negativePage_throwsValidation_withoutQueryingOrLogging() {
        assertThatThrownBy(() -> service.getHistory(1L, -1, 30))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Numer strony nie może być ujemny.");

        verifyNoInteractions(clientRepo, cardAccessLogRepo, cardAccessLogService);
    }

    @Test
    void getHistory_clampsPageSizeToRange1To100() {
        stubLogs();

        service.getHistory(1L, 0, 1000);
        service.getHistory(1L, 0, 100);
        service.getHistory(1L, 0, 1);
        service.getHistory(1L, 0, 0);
        service.getHistory(1L, 0, -5);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(cardAccessLogRepo, times(5)).findHistory(eq(1L), anyCollection(), pageable.capture());
        assertThat(pageable.getAllValues()).containsExactly(PageRequest.of(0, 100), PageRequest.of(0, 100),
                PageRequest.of(0, 1), PageRequest.of(0, 1), PageRequest.of(0, 1));
    }

    @Test
    void getHistory_excludesViewActionsExceptViewCard_andDrawingSheetCreation_andPassesPaging() {
        stubLogs();

        service.getHistory(1L, 2, 30);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<CardAccessAction>> excluded = ArgumentCaptor.forClass(Collection.class);
        verify(cardAccessLogRepo).findHistory(eq(1L), excluded.capture(), eq(PageRequest.of(2, 30)));
        assertThat(excluded.getValue()).containsExactlyInAnyOrder(CardAccessAction.VIEW_QUESTIONNAIRE,
                CardAccessAction.VIEW_DRAWING, CardAccessAction.VIEW_DRAWING_SHEET,
                CardAccessAction.CREATE_DRAWING_SHEET, CardAccessAction.VIEW_THERAPY_ENTRIES,
                CardAccessAction.VIEW_THERAPY_ENTRY_HISTORY, CardAccessAction.VIEW_CARD_HISTORY);
        assertThat(excluded.getValue()).doesNotContain(CardAccessAction.VIEW_CARD, CardAccessAction.UPDATE_DRAWING);
        assertThat(CardAccessAction.values())
                .filteredOn(action -> action.name().startsWith("VIEW_") && action != CardAccessAction.VIEW_CARD)
                .allMatch(excluded.getValue()::contains);
    }

    @Test
    void getHistory_mapsQuestionnaireRevisionChanges() throws Exception {
        stubLogs(log(1L, CardAccessAction.UPDATE_QUESTIONNAIRE, 4L, "QUESTIONNAIRE_REVISION", 50L));
        String changes = json(List.of(
                new QuestionnaireFieldChange(1L, "Cukrzyca", "YES_NO", null, new AnswerSnapshot(true, null)),
                new QuestionnaireFieldChange(2L, "Choroby serca", "YES_NO", new AnswerSnapshot(true, null), null),
                new QuestionnaireFieldChange(3L, "Leki", "TEXT", new AnswerSnapshot(null, "Metformina"),
                        new AnswerSnapshot(null, "Insulina")),
                new QuestionnaireFieldChange(4L, "Alergie", "TEXT", null, new AnswerSnapshot(null, "penicylina"))));
        when(questionnaireRevisionRepo.findByClientIdAndIdIn(1L, Set.of(50L))).thenReturn(List.of(
                ClientQuestionnaireRevision.builder().id(50L).changes(changes).build()));

        Page<CardHistoryItemDTO> result = service.getHistory(1L, 0, 30);

        CardHistoryItemDTO item = result.getContent().get(0);
        assertThat(item.getEntryDate()).isNull();
        assertThat(item.getChanges())
                .extracting(CardHistoryChangeDTO::getLabel, CardHistoryChangeDTO::getType,
                        CardHistoryChangeDTO::getBefore, CardHistoryChangeDTO::getAfter)
                .containsExactly(
                        tuple("Cukrzyca", "YES_NO", "NIE", "TAK"),
                        tuple("Choroby serca", "YES_NO", "TAK", "NIE"),
                        tuple("Leki", "TEXT", "Metformina", "Insulina"),
                        tuple("Alergie", "TEXT", null, "penicylina"));
    }

    @Test
    void getHistory_mapsClientDataRevisionChanges_withPolishLabels() throws Exception {
        stubLogs(log(1L, CardAccessAction.UPDATE_CLIENT_DATA, 4L, "CLIENT_DATA_REVISION", 70L));
        String changes = json(List.of(
                new ClientDataFieldChange("firstName", "Jan", "Janusz"),
                new ClientDataFieldChange("lastName", "Kowalski", "Nowak"),
                new ClientDataFieldChange("birthDate", null, "1981-02-03"),
                new ClientDataFieldChange("phoneNumber", "600100200", null),
                new ClientDataFieldChange("signedRegulations", "false", "true")));
        when(dataRevisionRepo.findByClientIdAndIdIn(1L, Set.of(70L))).thenReturn(List.of(
                ClientCardDataRevision.builder().id(70L).changes(changes).build()));

        CardHistoryItemDTO item = service.getHistory(1L, 0, 30).getContent().get(0);

        assertThat(item.getChanges())
                .extracting(CardHistoryChangeDTO::getLabel, CardHistoryChangeDTO::getType,
                        CardHistoryChangeDTO::getBefore, CardHistoryChangeDTO::getAfter)
                .containsExactly(
                        tuple("Imię", "TEXT", "Jan", "Janusz"),
                        tuple("Nazwisko", "TEXT", "Kowalski", "Nowak"),
                        tuple("Data urodzenia", "TEXT", null, "1981-02-03"),
                        tuple("Telefon", "TEXT", "600100200", null),
                        tuple("Regulamin podpisany", "TEXT", "NIE", "TAK"));
    }

    @Test
    void getHistory_mapsRemarksChange_asTextWithPolishLabel() throws Exception {
        stubLogs(log(1L, CardAccessAction.UPDATE_CLIENT_DATA, 4L, "CLIENT_DATA_REVISION", 71L));
        String changes = json(List.of(
                new ClientDataFieldChange("remarks", "Rocznica ślubu 12.05", "Rocznica ślubu 14.05"),
                new ClientDataFieldChange("remarks", null, "Lubi kawę"),
                new ClientDataFieldChange("remarks", "Lubi kawę", null)));
        when(dataRevisionRepo.findByClientIdAndIdIn(1L, Set.of(71L))).thenReturn(List.of(
                ClientCardDataRevision.builder().id(71L).changes(changes).build()));

        CardHistoryItemDTO item = service.getHistory(1L, 0, 30).getContent().get(0);

        assertThat(item.getChanges())
                .extracting(CardHistoryChangeDTO::getLabel, CardHistoryChangeDTO::getType,
                        CardHistoryChangeDTO::getBefore, CardHistoryChangeDTO::getAfter)
                .containsExactly(
                        tuple("Warto pamiętać", "TEXT", "Rocznica ślubu 12.05", "Rocznica ślubu 14.05"),
                        tuple("Warto pamiętać", "TEXT", null, "Lubi kawę"),
                        tuple("Warto pamiętać", "TEXT", "Lubi kawę", null));
    }

    @Test
    void getHistory_legacyLogsWithoutRevision_haveNullChanges() {
        stubLogs(
                log(1L, CardAccessAction.UPDATE_QUESTIONNAIRE, 4L, "QUESTIONNAIRE", 10L),
                log(2L, CardAccessAction.UPDATE_CLIENT_DATA, 4L, null, null));

        List<CardHistoryItemDTO> items = service.getHistory(1L, 0, 30).getContent();

        assertThat(items).extracting(CardHistoryItemDTO::getChanges).containsOnlyNulls();
        verifyNoInteractions(questionnaireRevisionRepo, dataRevisionRepo);
    }

    @Test
    void getHistory_mapsEntryDate_forTherapyActions_andNullWhenEntryMissing() {
        stubLogs(
                log(1L, CardAccessAction.CREATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY", 20L),
                log(2L, CardAccessAction.UPDATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY", 20L),
                log(3L, CardAccessAction.INVALIDATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY", 21L),
                log(4L, CardAccessAction.INVALIDATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY", 99L),
                log(5L, CardAccessAction.UPDATE_DRAWING, 4L, "DRAWING_SHEET", 20L));
        when(entryRepo.findEntryDatesByClientIdAndIdIn(1L, Set.of(20L, 21L, 99L))).thenReturn(List.of(
                entryDate(20L, LocalDate.of(2026, 9, 15)),
                entryDate(21L, LocalDate.of(2026, 9, 1))));

        List<CardHistoryItemDTO> items = service.getHistory(1L, 0, 30).getContent();

        assertThat(items).extracting(CardHistoryItemDTO::getEntryDate).containsExactly(
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 1),
                null, null);
        assertThat(items).extracting(CardHistoryItemDTO::getChanges).containsOnlyNulls();
        assertThat(items.get(4).getAction()).isEqualTo(CardAccessAction.UPDATE_DRAWING);
    }

    @Test
    void getHistory_mapsCreatedEntry_fromOldestRevisionOrCurrentEntry_andNullWhenMissing() {
        stubLogs(
                log(1L, CardAccessAction.CREATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY", 20L),
                log(2L, CardAccessAction.CREATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY", 21L),
                log(3L, CardAccessAction.CREATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY", 22L),
                log(4L, CardAccessAction.UPDATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY", 20L),
                log(5L, CardAccessAction.INVALIDATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY", 21L),
                log(6L, CardAccessAction.CREATE_THERAPY_ENTRY, 4L, null, null));
        when(entryRevisionRepo.findOldestByClientIdAndEntryIdIn(1L, Set.of(20L, 21L, 22L))).thenReturn(List.of(
                TherapyEntryRevision.builder().id(300L).entry(TherapyEntry.builder().id(20L).build())
                        .previousEntryDate(LocalDate.of(2026, 9, 10)).previousContent("Pierwotna treść")
                        .changedAt(AT).changedByUserId(4L).build()));
        when(entryRepo.findByCardClientIdAndIdIn(1L, Set.of(21L, 22L))).thenReturn(List.of(
                TherapyEntry.builder().id(21L).entryDate(LocalDate.of(2026, 9, 1)).content("Bez edycji").build()));

        List<CardHistoryItemDTO> items = service.getHistory(1L, 0, 30).getContent();

        assertThat(items.subList(0, 2))
                .extracting(item -> item.getCreatedEntry().getEntryDate(), item -> item.getCreatedEntry().getContent())
                .containsExactly(
                        tuple(LocalDate.of(2026, 9, 10), "Pierwotna treść"),
                        tuple(LocalDate.of(2026, 9, 1), "Bez edycji"));
        assertThat(items.subList(2, 6)).extracting(CardHistoryItemDTO::getCreatedEntry).containsOnlyNulls();
    }

    @Test
    void getHistory_skipsCurrentEntryLookup_whenAllCreatedEntriesWereEdited() {
        stubLogs(log(1L, CardAccessAction.CREATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY", 20L));
        when(entryRevisionRepo.findOldestByClientIdAndEntryIdIn(1L, Set.of(20L))).thenReturn(List.of(
                TherapyEntryRevision.builder().id(300L).entry(TherapyEntry.builder().id(20L).build())
                        .previousEntryDate(LocalDate.of(2026, 9, 10)).previousContent("Pierwotna treść").build()));

        CardHistoryEntrySnapshotDTO created = service.getHistory(1L, 0, 30).getContent().get(0).getCreatedEntry();

        assertThat(created.getContent()).isEqualTo("Pierwotna treść");
        verify(entryRepo, never()).findByCardClientIdAndIdIn(any(), anyCollection());
    }

    @Test
    void getHistory_doesNotTouchEntryRevisions_withoutTherapyLogs() {
        stubLogs(
                log(1L, CardAccessAction.VIEW_CARD, 4L, null, null),
                log(2L, CardAccessAction.UPDATE_DRAWING, 4L, "DRAWING_SHEET", 20L));

        List<CardHistoryItemDTO> items = service.getHistory(1L, 0, 30).getContent();

        assertThat(items).extracting(CardHistoryItemDTO::getCreatedEntry, CardHistoryItemDTO::getEditedEntry,
                CardHistoryItemDTO::getInvalidatedEntry).containsOnly(tuple(null, null, null));
        verifyNoInteractions(entryRevisionRepo, entryRepo);
    }

    @Test
    void getHistory_mapsEditedEntry_forRevisionLinkedLogs_fromNextRevisionOrCurrentEntry() {
        stubLogs(
                log(1L, CardAccessAction.UPDATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY_REVISION", 302L),
                log(2L, CardAccessAction.UPDATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY_REVISION", 301L));
        when(entryRevisionRepo.findRefsByClientIdAndIdIn(1L, Set.of(301L, 302L)))
                .thenReturn(List.of(revisionRef(301L, 20L), revisionRef(302L, 20L)));
        when(entryRepo.findEntryDatesByClientIdAndIdIn(1L, Set.of(20L)))
                .thenReturn(List.of(entryDate(20L, LocalDate.of(2026, 9, 20))));
        when(entryRevisionRepo.findOrderedRefsByClientIdAndEntryIdIn(1L, Set.of(20L)))
                .thenReturn(List.of(revisionRef(300L, 20L), revisionRef(301L, 20L), revisionRef(302L, 20L)));
        when(entryRevisionRepo.findByEntryCardClientIdAndIdIn(1L, Set.of(301L, 302L))).thenReturn(List.of(
                revision(301L, 20L, LocalDate.of(2026, 9, 15), "Druga wersja"),
                revision(302L, 20L, LocalDate.of(2026, 9, 18), "Trzecia wersja")));
        when(entryRepo.findByCardClientIdAndIdIn(1L, Set.of(20L))).thenReturn(List.of(
                TherapyEntry.builder().id(20L).entryDate(LocalDate.of(2026, 9, 20)).content("Bieżąca").build()));

        List<CardHistoryItemDTO> items = service.getHistory(1L, 0, 30).getContent();

        assertThat(items).extracting(CardHistoryItemDTO::getEntryDate)
                .containsOnly(LocalDate.of(2026, 9, 20));
        assertThat(items).extracting(CardHistoryItemDTO::getEditedEntry)
                .extracting(CardHistoryEditedEntryDTO::getBeforeEntryDate, CardHistoryEditedEntryDTO::getBeforeContent,
                        CardHistoryEditedEntryDTO::getAfterEntryDate, CardHistoryEditedEntryDTO::getAfterContent)
                .containsExactly(
                        tuple(LocalDate.of(2026, 9, 18), "Trzecia wersja", LocalDate.of(2026, 9, 20), "Bieżąca"),
                        tuple(LocalDate.of(2026, 9, 15), "Druga wersja", LocalDate.of(2026, 9, 18), "Trzecia wersja"));
        assertThat(items).extracting(CardHistoryItemDTO::getCreatedEntry, CardHistoryItemDTO::getInvalidatedEntry,
                CardHistoryItemDTO::getChanges).containsOnly(tuple(null, null, null));
        verify(cardAccessLogRepo, never()).findOrderedResourceRefs(any(), any(), any(), anyCollection());
    }

    @Test
    void getHistory_mapsEditedEntry_forLegacyLogs_byOrdinalAmongAllUpdateLogsOfEntry() {
        stubLogs(log(7L, CardAccessAction.UPDATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY", 20L));
        when(entryRevisionRepo.findOrderedRefsByClientIdAndEntryIdIn(1L, Set.of(20L)))
                .thenReturn(List.of(revisionRef(300L, 20L), revisionRef(301L, 20L), revisionRef(302L, 20L)));
        when(cardAccessLogRepo.findOrderedResourceRefs(1L, CardAccessAction.UPDATE_THERAPY_ENTRY, "THERAPY_ENTRY",
                Set.of(20L))).thenReturn(List.of(logRef(5L, 20L), logRef(7L, 20L), logRef(9L, 20L)));
        when(entryRevisionRepo.findByEntryCardClientIdAndIdIn(1L, Set.of(301L, 302L))).thenReturn(List.of(
                revision(301L, 20L, LocalDate.of(2026, 9, 15), "Druga wersja"),
                revision(302L, 20L, LocalDate.of(2026, 9, 18), "Trzecia wersja")));

        CardHistoryEditedEntryDTO edited = service.getHistory(1L, 0, 30).getContent().get(0).getEditedEntry();

        assertThat(edited)
                .extracting(CardHistoryEditedEntryDTO::getBeforeEntryDate, CardHistoryEditedEntryDTO::getBeforeContent,
                        CardHistoryEditedEntryDTO::getAfterEntryDate, CardHistoryEditedEntryDTO::getAfterContent)
                .containsExactly(LocalDate.of(2026, 9, 15), "Druga wersja", LocalDate.of(2026, 9, 18), "Trzecia wersja");
        verify(entryRepo, never()).findByCardClientIdAndIdIn(any(), anyCollection());
    }

    @Test
    void getHistory_legacyLogWithoutMatchingRevision_hasNullEditedEntry_andLoadsNoContent() {
        stubLogs(log(7L, CardAccessAction.UPDATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY", 20L));
        when(entryRevisionRepo.findOrderedRefsByClientIdAndEntryIdIn(1L, Set.of(20L)))
                .thenReturn(List.of(revisionRef(300L, 20L)));
        when(cardAccessLogRepo.findOrderedResourceRefs(1L, CardAccessAction.UPDATE_THERAPY_ENTRY, "THERAPY_ENTRY",
                Set.of(20L))).thenReturn(List.of(logRef(5L, 20L), logRef(7L, 20L)));

        CardHistoryItemDTO item = service.getHistory(1L, 0, 30).getContent().get(0);

        assertThat(item.getEditedEntry()).isNull();
        verify(entryRevisionRepo, never()).findByEntryCardClientIdAndIdIn(any(), anyCollection());
        verify(entryRepo, never()).findByCardClientIdAndIdIn(any(), anyCollection());
    }

    @Test
    void getHistory_ignoresEntryRevisionsOfOtherClients() {
        stubLogs(log(1L, CardAccessAction.UPDATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY_REVISION", 301L));
        when(entryRevisionRepo.findRefsByClientIdAndIdIn(1L, Set.of(301L))).thenReturn(List.of());

        CardHistoryItemDTO item = service.getHistory(1L, 0, 30).getContent().get(0);

        assertThat(item.getEditedEntry()).isNull();
        assertThat(item.getEntryDate()).isNull();
        verify(entryRevisionRepo, never()).findOrderedRefsByClientIdAndEntryIdIn(any(), anyCollection());
        verify(entryRevisionRepo, never()).findByEntryCardClientIdAndIdIn(any(), anyCollection());
        verifyNoInteractions(entryRepo);
    }

    @Test
    void getHistory_mapsInvalidatedEntry_fromCurrentEntry_andNullWhenMissing() {
        stubLogs(
                log(1L, CardAccessAction.INVALIDATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY", 21L),
                log(2L, CardAccessAction.INVALIDATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY", 99L),
                log(3L, CardAccessAction.INVALIDATE_THERAPY_ENTRY, 4L, null, null));
        when(entryRepo.findByCardClientIdAndIdIn(1L, Set.of(21L, 99L))).thenReturn(List.of(
                TherapyEntry.builder().id(21L).entryDate(LocalDate.of(2026, 9, 1)).content("Błędny wpis").build()));

        List<CardHistoryItemDTO> items = service.getHistory(1L, 0, 30).getContent();

        assertThat(items.get(0).getInvalidatedEntry())
                .extracting(CardHistoryEntrySnapshotDTO::getEntryDate, CardHistoryEntrySnapshotDTO::getContent)
                .containsExactly(LocalDate.of(2026, 9, 1), "Błędny wpis");
        assertThat(items.subList(1, 3)).extracting(CardHistoryItemDTO::getInvalidatedEntry).containsOnlyNulls();
        assertThat(items).extracting(CardHistoryItemDTO::getCreatedEntry, CardHistoryItemDTO::getEditedEntry)
                .containsOnly(tuple(null, null));
        verifyNoInteractions(entryRevisionRepo);
    }

    @Test
    void getHistory_loadsCurrentEntries_inSingleBatch_forCreatedEditedAndInvalidated() {
        stubLogs(
                log(1L, CardAccessAction.CREATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY", 22L),
                log(2L, CardAccessAction.UPDATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY_REVISION", 301L),
                log(3L, CardAccessAction.INVALIDATE_THERAPY_ENTRY, 4L, "THERAPY_ENTRY", 21L));
        when(entryRevisionRepo.findRefsByClientIdAndIdIn(1L, Set.of(301L))).thenReturn(List.of(revisionRef(301L, 20L)));
        when(entryRevisionRepo.findOrderedRefsByClientIdAndEntryIdIn(1L, Set.of(20L)))
                .thenReturn(List.of(revisionRef(301L, 20L)));
        when(entryRevisionRepo.findByEntryCardClientIdAndIdIn(1L, Set.of(301L)))
                .thenReturn(List.of(revision(301L, 20L, LocalDate.of(2026, 9, 15), "Przed")));
        when(entryRepo.findByCardClientIdAndIdIn(1L, Set.of(20L, 21L, 22L))).thenReturn(List.of(
                TherapyEntry.builder().id(20L).entryDate(LocalDate.of(2026, 9, 16)).content("Po").build(),
                TherapyEntry.builder().id(21L).entryDate(LocalDate.of(2026, 9, 1)).content("Błędny").build(),
                TherapyEntry.builder().id(22L).entryDate(LocalDate.of(2026, 9, 2)).content("Nowy").build()));

        List<CardHistoryItemDTO> items = service.getHistory(1L, 0, 30).getContent();

        assertThat(items.get(0).getCreatedEntry().getContent()).isEqualTo("Nowy");
        assertThat(items.get(1).getEditedEntry().getAfterContent()).isEqualTo("Po");
        assertThat(items.get(2).getInvalidatedEntry().getContent()).isEqualTo("Błędny");
        verify(entryRepo, times(1)).findByCardClientIdAndIdIn(any(), anyCollection());
    }

    @Test
    void getHistory_resolvesUserNames_inSingleBatch_withUsernameFallback() {
        stubLogs(
                log(1L, CardAccessAction.VIEW_CARD, 4L, null, null),
                log(2L, CardAccessAction.VIEW_CARD, 5L, null, null),
                log(3L, CardAccessAction.VIEW_CARD, 6L, null, null),
                log(4L, CardAccessAction.VIEW_CARD, null, null, null),
                log(5L, CardAccessAction.VIEW_CARD, 4L, null, null));
        when(userRepo.findAllWithEmployeeByIdIn(Set.of(4L, 5L, 6L))).thenReturn(List.of(
                User.builder().id(4L).username("anna")
                        .employee(Employee.builder().id(1L).name("Anna").lastName("Kowalska").build()).build(),
                User.builder().id(5L).username("admin").build()));

        List<CardHistoryItemDTO> items = service.getHistory(1L, 0, 30).getContent();

        assertThat(items).extracting(CardHistoryItemDTO::getUserName)
                .containsExactly("Anna Kowalska", "admin", null, null, "Anna Kowalska");
        assertThat(items.get(0))
                .extracting(CardHistoryItemDTO::getId, CardHistoryItemDTO::getAt, CardHistoryItemDTO::getUserId,
                        CardHistoryItemDTO::getIpAddress, CardHistoryItemDTO::getAction)
                .containsExactly(1L, AT.minusMinutes(1), 4L, "1.2.3.4", CardAccessAction.VIEW_CARD);
        verify(userRepo, times(1)).findAllWithEmployeeByIdIn(anyCollection());
    }

    @Test
    void getHistory_preservesRepositoryOrder_andPageMetadata() {
        stubLogs(
                log(3L, CardAccessAction.VIEW_CARD, null, null, null),
                log(2L, CardAccessAction.VIEW_CARD, null, null, null),
                log(1L, CardAccessAction.VIEW_CARD, null, null, null));

        Page<CardHistoryItemDTO> result = service.getHistory(1L, 0, 30);

        assertThat(result.getContent()).extracting(CardHistoryItemDTO::getId).containsExactly(3L, 2L, 1L);
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getSize()).isEqualTo(30);
    }

    @Test
    void getHistory_firstPage_logsViewCardHistoryOnce_withoutResource() {
        stubLogs(log(1L, CardAccessAction.VIEW_CARD, null, null, null));

        service.getHistory(1L, 0, 30);

        verify(cardAccessLogService).log(CardAccessAction.VIEW_CARD_HISTORY, 1L, null, null);
        verifyNoMoreInteractions(cardAccessLogService);
    }

    @Test
    void getHistory_furtherPages_doNotLogAccess() {
        stubLogs(log(1L, CardAccessAction.VIEW_CARD, null, null, null));

        service.getHistory(1L, 1, 30);
        service.getHistory(1L, 5, 30);

        verifyNoInteractions(cardAccessLogService);
    }

    @Test
    void getHistory_ignoresRevisionsOfOtherClients() {
        stubLogs(log(1L, CardAccessAction.UPDATE_QUESTIONNAIRE, null, "QUESTIONNAIRE_REVISION", 50L));
        when(questionnaireRevisionRepo.findByClientIdAndIdIn(1L, Set.of(50L))).thenReturn(List.of());

        CardHistoryItemDTO item = service.getHistory(1L, 0, 30).getContent().get(0);

        assertThat(item.getChanges()).isNull();
        verify(questionnaireRevisionRepo).findByClientIdAndIdIn(1L, Set.of(50L));
    }
}

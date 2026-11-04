package com.clinic.clinicmanager.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import com.clinic.clinicmanager.model.TherapyEntry;
import com.clinic.clinicmanager.model.TherapyEntryRevision;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.CardAccessAction;
import com.clinic.clinicmanager.model.revision.ClientCardDataField;
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
import com.clinic.clinicmanager.service.CardAccessLogService;
import com.clinic.clinicmanager.service.CardHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CardHistoryServiceImpl implements CardHistoryService {

    static final Set<CardAccessAction> EXCLUDED_ACTIONS = EnumSet.of(
            CardAccessAction.VIEW_QUESTIONNAIRE,
            CardAccessAction.VIEW_DRAWING,
            CardAccessAction.VIEW_DRAWING_SHEET,
            CardAccessAction.CREATE_DRAWING_SHEET,
            CardAccessAction.VIEW_THERAPY_ENTRIES,
            CardAccessAction.VIEW_THERAPY_ENTRY_HISTORY,
            CardAccessAction.VIEW_CARD_HISTORY);
    static final Set<CardAccessAction> ENTRY_ACTIONS = EnumSet.of(
            CardAccessAction.CREATE_THERAPY_ENTRY,
            CardAccessAction.UPDATE_THERAPY_ENTRY,
            CardAccessAction.INVALIDATE_THERAPY_ENTRY);
    static final String YES = "TAK";
    static final String NO = "NIE";

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<CardAccessAction> CREATE_ACTIONS = EnumSet.of(CardAccessAction.CREATE_THERAPY_ENTRY);
    private static final Set<CardAccessAction> UPDATE_ACTIONS = EnumSet.of(CardAccessAction.UPDATE_THERAPY_ENTRY);
    private static final Set<CardAccessAction> INVALIDATE_ACTIONS =
            EnumSet.of(CardAccessAction.INVALIDATE_THERAPY_ENTRY);
    private static final TypeReference<List<QuestionnaireFieldChange>> QUESTIONNAIRE_CHANGES = new TypeReference<>() {
    };
    private static final TypeReference<List<ClientDataFieldChange>> CLIENT_DATA_CHANGES = new TypeReference<>() {
    };

    private final ClientRepo clientRepo;
    private final CardAccessLogRepo cardAccessLogRepo;
    private final ClientQuestionnaireRevisionRepo questionnaireRevisionRepo;
    private final ClientCardDataRevisionRepo dataRevisionRepo;
    private final TherapyEntryRepo entryRepo;
    private final TherapyEntryRevisionRepo entryRevisionRepo;
    private final UserRepo userRepo;
    private final CardAccessLogService cardAccessLogService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Page<CardHistoryItemDTO> getHistory(Long clientId, int page, int size) {
        if (page < 0) {
            throw new ValidationException("Numer strony nie może być ujemny.");
        }
        if (!clientRepo.existsById(clientId)) {
            throw new ResourceNotFoundException("Client not found with given id: " + clientId);
        }

        Page<CardAccessLog> logs = cardAccessLogRepo.findHistory(clientId, EXCLUDED_ACTIONS,
                PageRequest.of(page, Math.clamp(size, 1, MAX_PAGE_SIZE)));
        List<CardAccessLog> content = logs.getContent();

        Map<Long, List<CardHistoryChangeDTO>> questionnaireChanges = loadQuestionnaireChanges(clientId,
                resourceIds(content, Set.of(CardAccessAction.UPDATE_QUESTIONNAIRE),
                        ClientQuestionnaireServiceImpl.REVISION_RESOURCE_TYPE));
        Map<Long, List<CardHistoryChangeDTO>> dataChanges = loadClientDataChanges(clientId,
                resourceIds(content, Set.of(CardAccessAction.UPDATE_CLIENT_DATA),
                        ClientCardServiceImpl.DATA_REVISION_RESOURCE_TYPE));

        Map<Long, Long> revisionEntryIds = loadRevisionEntryIds(clientId,
                resourceIds(content, UPDATE_ACTIONS, TherapyEntryServiceImpl.REVISION_RESOURCE_TYPE));
        Map<Long, Long> entryIdsByLog = entryIdsByLog(content, revisionEntryIds);
        Map<Long, LocalDate> entryDates = loadEntryDates(clientId, new HashSet<>(entryIdsByLog.values()));

        Set<Long> createdIds = resourceIds(content, CREATE_ACTIONS, TherapyEntryServiceImpl.RESOURCE_TYPE);
        Set<Long> invalidatedIds = resourceIds(content, INVALIDATE_ACTIONS, TherapyEntryServiceImpl.RESOURCE_TYPE);
        Map<Long, TherapyEntryRevision> oldestRevisions = loadOldestRevisions(clientId, createdIds);
        EditTargets edits = resolveEditTargets(clientId, content, revisionEntryIds);
        Map<Long, TherapyEntryRevision> editRevisions = loadRevisions(clientId, edits.revisionIds());

        Set<Long> currentIds = new HashSet<>(edits.currentEntryIds());
        currentIds.addAll(invalidatedIds);
        createdIds.stream()
                .filter(id -> !oldestRevisions.containsKey(id))
                .forEach(currentIds::add);
        Map<Long, TherapyEntry> currentEntries = loadCurrentEntries(clientId, currentIds);

        Map<Long, String> names = resolveNames(content.stream()
                .map(CardAccessLog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));

        if (page == 0) {
            cardAccessLogService.log(CardAccessAction.VIEW_CARD_HISTORY, clientId, null, null);
        }
        return logs.map(log -> new CardHistoryItemDTO(log,
                log.getUserId() == null ? null : names.get(log.getUserId()),
                valueOf(entryDates, entryIdsByLog.get(log.getId())),
                createdEntryFor(log, oldestRevisions, currentEntries),
                edits.editedEntryFor(log, editRevisions, currentEntries),
                invalidatedEntryFor(log, currentEntries),
                changesFor(log, questionnaireChanges, dataChanges)));
    }

    private static List<CardHistoryChangeDTO> changesFor(CardAccessLog log,
                                                         Map<Long, List<CardHistoryChangeDTO>> questionnaireChanges,
                                                         Map<Long, List<CardHistoryChangeDTO>> dataChanges) {
        if (log.getAction() == CardAccessAction.UPDATE_QUESTIONNAIRE
                && ClientQuestionnaireServiceImpl.REVISION_RESOURCE_TYPE.equals(log.getResourceType())) {
            return lookup(questionnaireChanges, log);
        }
        if (log.getAction() == CardAccessAction.UPDATE_CLIENT_DATA
                && ClientCardServiceImpl.DATA_REVISION_RESOURCE_TYPE.equals(log.getResourceType())) {
            return lookup(dataChanges, log);
        }
        return null;
    }

    private static CardHistoryEntrySnapshotDTO createdEntryFor(CardAccessLog log,
                                                               Map<Long, TherapyEntryRevision> oldestRevisions,
                                                               Map<Long, TherapyEntry> currentEntries) {
        if (log.getAction() != CardAccessAction.CREATE_THERAPY_ENTRY
                || !TherapyEntryServiceImpl.RESOURCE_TYPE.equals(log.getResourceType())) {
            return null;
        }
        TherapyEntryRevision oldest = lookup(oldestRevisions, log);
        if (oldest != null) {
            return new CardHistoryEntrySnapshotDTO(oldest);
        }
        TherapyEntry entry = lookup(currentEntries, log);
        return entry == null ? null : new CardHistoryEntrySnapshotDTO(entry);
    }

    private static CardHistoryEntrySnapshotDTO invalidatedEntryFor(CardAccessLog log,
                                                                   Map<Long, TherapyEntry> currentEntries) {
        if (log.getAction() != CardAccessAction.INVALIDATE_THERAPY_ENTRY
                || !TherapyEntryServiceImpl.RESOURCE_TYPE.equals(log.getResourceType())) {
            return null;
        }
        TherapyEntry entry = lookup(currentEntries, log);
        return entry == null ? null : new CardHistoryEntrySnapshotDTO(entry);
    }

    private static <T> T lookup(Map<Long, T> values, CardAccessLog log) {
        return valueOf(values, log.getResourceId());
    }

    private static <T> T valueOf(Map<Long, T> values, Long key) {
        return key == null ? null : values.get(key);
    }

    private static Set<Long> resourceIds(List<CardAccessLog> logs, Set<CardAccessAction> actions, String resourceType) {
        return logs.stream()
                .filter(log -> actions.contains(log.getAction()))
                .filter(log -> resourceType.equals(log.getResourceType()))
                .map(CardAccessLog::getResourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static Map<Long, Long> entryIdsByLog(List<CardAccessLog> logs, Map<Long, Long> revisionEntryIds) {
        Map<Long, Long> entryIds = new HashMap<>();
        for (CardAccessLog log : logs) {
            if (!ENTRY_ACTIONS.contains(log.getAction()) || log.getResourceId() == null) {
                continue;
            }
            if (TherapyEntryServiceImpl.RESOURCE_TYPE.equals(log.getResourceType())) {
                entryIds.put(log.getId(), log.getResourceId());
            } else if (isRevisionUpdate(log) && revisionEntryIds.containsKey(log.getResourceId())) {
                entryIds.put(log.getId(), revisionEntryIds.get(log.getResourceId()));
            }
        }
        return entryIds;
    }

    private static boolean isRevisionUpdate(CardAccessLog log) {
        return log.getAction() == CardAccessAction.UPDATE_THERAPY_ENTRY
                && TherapyEntryServiceImpl.REVISION_RESOURCE_TYPE.equals(log.getResourceType());
    }

    private EditTargets resolveEditTargets(Long clientId, List<CardAccessLog> logs, Map<Long, Long> revisionEntryIds) {
        Set<Long> legacyEntryIds = resourceIds(logs, UPDATE_ACTIONS, TherapyEntryServiceImpl.RESOURCE_TYPE);
        Set<Long> entryIds = new HashSet<>(revisionEntryIds.values());
        entryIds.addAll(legacyEntryIds);
        if (entryIds.isEmpty()) {
            return EditTargets.NONE;
        }

        Map<Long, List<Long>> sequences = entryRevisionRepo.findOrderedRefsByClientIdAndEntryIdIn(clientId, entryIds)
                .stream()
                .collect(Collectors.groupingBy(TherapyEntryRevisionRefProjection::getEntryId,
                        Collectors.mapping(TherapyEntryRevisionRefProjection::getId, Collectors.toList())));
        Map<Long, Long> nextRevisions = new HashMap<>();
        Map<Long, Long> revisionEntries = new HashMap<>();
        sequences.forEach((entryId, revisionIds) -> {
            for (int i = 0; i < revisionIds.size(); i++) {
                revisionEntries.put(revisionIds.get(i), entryId);
                if (i + 1 < revisionIds.size()) {
                    nextRevisions.put(revisionIds.get(i), revisionIds.get(i + 1));
                }
            }
        });

        Map<Long, Long> revisionsByLog = new HashMap<>();
        logs.stream()
                .filter(CardHistoryServiceImpl::isRevisionUpdate)
                .filter(log -> log.getResourceId() != null && revisionEntries.containsKey(log.getResourceId()))
                .forEach(log -> revisionsByLog.put(log.getId(), log.getResourceId()));
        if (!legacyEntryIds.isEmpty()) {
            Set<Long> pageLogIds = logs.stream().map(CardAccessLog::getId).collect(Collectors.toSet());
            Map<Long, Integer> ordinals = new HashMap<>();
            for (CardAccessLogResourceProjection ref : cardAccessLogRepo.findOrderedResourceRefs(clientId,
                    CardAccessAction.UPDATE_THERAPY_ENTRY, TherapyEntryServiceImpl.RESOURCE_TYPE, legacyEntryIds)) {
                int ordinal = ordinals.merge(ref.getResourceId(), 1, Integer::sum) - 1;
                List<Long> sequence = sequences.getOrDefault(ref.getResourceId(), List.of());
                if (pageLogIds.contains(ref.getId()) && ordinal < sequence.size()) {
                    revisionsByLog.put(ref.getId(), sequence.get(ordinal));
                }
            }
        }
        return new EditTargets(revisionsByLog, nextRevisions, revisionEntries);
    }

    private Map<Long, List<CardHistoryChangeDTO>> loadQuestionnaireChanges(Long clientId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return questionnaireRevisionRepo.findByClientIdAndIdIn(clientId, ids).stream()
                .collect(Collectors.toMap(ClientQuestionnaireRevision::getId,
                        revision -> readJson(revision.getChanges(), QUESTIONNAIRE_CHANGES).stream()
                                .map(CardHistoryServiceImpl::toChangeDTO)
                                .toList()));
    }

    private Map<Long, List<CardHistoryChangeDTO>> loadClientDataChanges(Long clientId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return dataRevisionRepo.findByClientIdAndIdIn(clientId, ids).stream()
                .collect(Collectors.toMap(ClientCardDataRevision::getId,
                        revision -> readJson(revision.getChanges(), CLIENT_DATA_CHANGES).stream()
                                .map(CardHistoryServiceImpl::toChangeDTO)
                                .toList()));
    }

    private Map<Long, Long> loadRevisionEntryIds(Long clientId, Collection<Long> revisionIds) {
        if (revisionIds.isEmpty()) {
            return Map.of();
        }
        return entryRevisionRepo.findRefsByClientIdAndIdIn(clientId, revisionIds).stream()
                .collect(Collectors.toMap(TherapyEntryRevisionRefProjection::getId,
                        TherapyEntryRevisionRefProjection::getEntryId));
    }

    private Map<Long, LocalDate> loadEntryDates(Long clientId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return entryRepo.findEntryDatesByClientIdAndIdIn(clientId, ids).stream()
                .collect(Collectors.toMap(TherapyEntryDateProjection::getId, TherapyEntryDateProjection::getEntryDate));
    }

    private Map<Long, TherapyEntryRevision> loadOldestRevisions(Long clientId, Collection<Long> entryIds) {
        if (entryIds.isEmpty()) {
            return Map.of();
        }
        return entryRevisionRepo.findOldestByClientIdAndEntryIdIn(clientId, entryIds).stream()
                .collect(Collectors.toMap(revision -> revision.getEntry().getId(), Function.identity()));
    }

    private Map<Long, TherapyEntryRevision> loadRevisions(Long clientId, Collection<Long> revisionIds) {
        if (revisionIds.isEmpty()) {
            return Map.of();
        }
        return entryRevisionRepo.findByEntryCardClientIdAndIdIn(clientId, revisionIds).stream()
                .collect(Collectors.toMap(TherapyEntryRevision::getId, Function.identity()));
    }

    private Map<Long, TherapyEntry> loadCurrentEntries(Long clientId, Collection<Long> entryIds) {
        if (entryIds.isEmpty()) {
            return Map.of();
        }
        return entryRepo.findByCardClientIdAndIdIn(clientId, entryIds).stream()
                .collect(Collectors.toMap(TherapyEntry::getId, Function.identity()));
    }

    private Map<Long, String> resolveNames(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepo.findAllWithEmployeeByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId,
                        user -> user.getEmployee() != null
                                ? user.getEmployee().getName() + " " + user.getEmployee().getLastName()
                                : user.getUsername(),
                        (first, second) -> first));
    }

    private static CardHistoryChangeDTO toChangeDTO(QuestionnaireFieldChange change) {
        if (QuestionnaireFieldChange.TYPE_YES_NO.equals(change.type())) {
            return new CardHistoryChangeDTO(change.label(), change.type(),
                    yesNo(change.before()), yesNo(change.after()));
        }
        return new CardHistoryChangeDTO(change.label(), change.type(),
                text(change.before()), text(change.after()));
    }

    private static CardHistoryChangeDTO toChangeDTO(ClientDataFieldChange change) {
        ClientCardDataField field = ClientCardDataField.fromKey(change.field()).orElse(null);
        String label = field != null ? field.getLabel() : change.field();
        if (field == ClientCardDataField.SIGNED_REGULATIONS) {
            return new CardHistoryChangeDTO(label, QuestionnaireFieldChange.TYPE_TEXT,
                    yesNo(change.before()), yesNo(change.after()));
        }
        return new CardHistoryChangeDTO(label, QuestionnaireFieldChange.TYPE_TEXT,
                blankToNull(change.before()), blankToNull(change.after()));
    }

    private static String yesNo(AnswerSnapshot snapshot) {
        return snapshot != null && Boolean.TRUE.equals(snapshot.checked()) ? YES : NO;
    }

    private static String yesNo(String value) {
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(value) ? YES : NO;
    }

    private static String text(AnswerSnapshot snapshot) {
        return snapshot == null ? null : blankToNull(snapshot.text());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private <T> List<T> readJson(String json, TypeReference<List<T>> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to read card history revision.");
        }
    }

    private record EditTargets(Map<Long, Long> revisionsByLog, Map<Long, Long> nextRevisions,
                               Map<Long, Long> revisionEntries) {

        static final EditTargets NONE = new EditTargets(Map.of(), Map.of(), Map.of());

        Set<Long> revisionIds() {
            Set<Long> ids = new HashSet<>(revisionsByLog.values());
            revisionsByLog.values().stream()
                    .map(nextRevisions::get)
                    .filter(Objects::nonNull)
                    .forEach(ids::add);
            return ids;
        }

        Set<Long> currentEntryIds() {
            return revisionsByLog.values().stream()
                    .filter(id -> !nextRevisions.containsKey(id))
                    .map(revisionEntries::get)
                    .collect(Collectors.toSet());
        }

        CardHistoryEditedEntryDTO editedEntryFor(CardAccessLog log, Map<Long, TherapyEntryRevision> revisions,
                                                 Map<Long, TherapyEntry> currentEntries) {
            Long revisionId = revisionsByLog.get(log.getId());
            TherapyEntryRevision before = valueOf(revisions, revisionId);
            if (before == null) {
                return null;
            }
            Long nextId = nextRevisions.get(revisionId);
            if (nextId != null) {
                TherapyEntryRevision after = revisions.get(nextId);
                return after == null ? null : new CardHistoryEditedEntryDTO(before, after);
            }
            TherapyEntry current = valueOf(currentEntries, revisionEntries.get(revisionId));
            return current == null ? null : new CardHistoryEditedEntryDTO(before, current);
        }
    }
}

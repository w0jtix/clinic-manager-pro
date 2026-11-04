package com.clinic.clinicmanager.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.ClientQuestionnaireDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireAnswerDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireItemDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireUpdateDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.QuestionnaireConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientCard;
import com.clinic.clinicmanager.model.ClientQuestionnaireRevision;
import com.clinic.clinicmanager.model.QuestionnaireAnswer;
import com.clinic.clinicmanager.model.QuestionnaireField;
import com.clinic.clinicmanager.model.constants.CardAccessAction;
import com.clinic.clinicmanager.model.constants.QuestionnaireFieldType;
import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.model.revision.QuestionnaireFieldChange;
import com.clinic.clinicmanager.model.revision.QuestionnaireFieldChange.AnswerSnapshot;
import com.clinic.clinicmanager.repo.CardSettingsRepo;
import com.clinic.clinicmanager.repo.ClientCardRepo;
import com.clinic.clinicmanager.repo.ClientQuestionnaireRevisionRepo;
import com.clinic.clinicmanager.repo.ClientRepo;
import com.clinic.clinicmanager.repo.QuestionnaireAnswerRepo;
import com.clinic.clinicmanager.repo.QuestionnaireFieldRepo;
import com.clinic.clinicmanager.repo.projection.QuestionnaireStateProjection;
import com.clinic.clinicmanager.service.CardAccessLogService;
import com.clinic.clinicmanager.service.ClientCardService;
import com.clinic.clinicmanager.service.ClientQuestionnaireService;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientQuestionnaireServiceImpl implements ClientQuestionnaireService {

    static final String RESOURCE_TYPE = "QUESTIONNAIRE";
    static final String REVISION_RESOURCE_TYPE = "QUESTIONNAIRE_REVISION";
    static final int MAX_TEXT_LENGTH = 2000;
    private static final String CHECKED_VALUE = "true";

    private final ClientRepo clientRepo;
    private final ClientCardRepo clientCardRepo;
    private final ClientCardService clientCardService;
    private final QuestionnaireFieldRepo fieldRepo;
    private final QuestionnaireAnswerRepo answerRepo;
    private final CardSettingsRepo cardSettingsRepo;
    private final CardAccessLogService cardAccessLogService;
    private final ClientQuestionnaireRevisionRepo revisionRepo;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ClientQuestionnaireDTO getQuestionnaire(Long clientId) {
        Client client = findClient(clientId);
        if (Boolean.TRUE.equals(client.getIsDeleted()) && !SessionUtils.hasRole(AppRoleHierarchy.MASTER)) {
            throw new ResourceNotFoundException("Client not found with given id: " + clientId);
        }

        Optional<ClientCard> card = clientCardRepo.findByClientId(clientId);
        Map<Long, String> values = card.map(c -> loadValues(c.getId())).orElseGet(Map::of);
        Long version = card.map(ClientCard::getQuestionnaireVersion).orElse(0L);
        LocalDateTime updatedAt = card.map(ClientCard::getQuestionnaireUpdatedAt).orElse(null);

        cardAccessLogService.log(CardAccessAction.VIEW_QUESTIONNAIRE, clientId, RESOURCE_TYPE,
                card.map(ClientCard::getId).orElse(null));
        return new ClientQuestionnaireDTO(version, updatedAt, isEnglishEnabled(),
                buildItems(fieldRepo.findAllByOrderByArchivedAscDisplayOrderAscIdAsc(), values));
    }

    @Override
    @Transactional
    public ClientQuestionnaireDTO updateQuestionnaire(Long clientId, QuestionnaireUpdateDTO dto) {
        Client client = findClient(clientId);
        if (Boolean.TRUE.equals(client.getIsDeleted())) {
            throw new ConflictException("Klient jest usunięty. Przywróć klienta przed edycją karty.");
        }
        if (dto == null || dto.getExpectedVersion() == null) {
            throw new ValidationException("Brak wersji ankiety.");
        }

        List<QuestionnaireField> fields = fieldRepo.findAllByOrderByArchivedAscDisplayOrderAscIdAsc();
        Map<Long, QuestionnaireField> fieldsById = fields.stream()
                .collect(Collectors.toMap(QuestionnaireField::getId, Function.identity()));
        Map<Long, String> desired = resolveDesiredValues(dto.getAnswers(), fields, fieldsById);

        ClientCard card = clientCardService.getOrCreateCard(clientId);
        QuestionnaireStateProjection state = clientCardRepo.lockQuestionnaireState(card.getId());
        if (!state.getVersion().equals(dto.getExpectedVersion())) {
            throw new QuestionnaireConflictException(state.getVersion());
        }

        Map<Long, QuestionnaireAnswer> existing = answerRepo.findByCardId(card.getId()).stream()
                .collect(Collectors.toMap(answer -> answer.getField().getId(), Function.identity()));
        Map<Long, String> values = new HashMap<>();
        existing.forEach((fieldId, answer) -> values.put(fieldId, answer.getValue()));

        List<QuestionnaireFieldChange> changes = diff(fields, desired, values);
        boolean changed = applyChanges(card, desired, existing, fieldsById);
        desired.forEach((fieldId, value) -> {
            if (value == null) {
                values.remove(fieldId);
            } else {
                values.put(fieldId, value);
            }
        });

        Long version = state.getVersion();
        LocalDateTime updatedAt = state.getUpdatedAt();
        if (changed) {
            updatedAt = LocalDateTime.now();
            Long userId = SessionUtils.getUserIdFromSession();
            clientCardRepo.bumpQuestionnaireVersion(card.getId(), updatedAt, userId);
            version = version + 1;
            ClientQuestionnaireRevision revision = revisionRepo.save(ClientQuestionnaireRevision.builder()
                    .client(client)
                    .version(version)
                    .changedAt(updatedAt)
                    .changedByUserId(userId)
                    .changes(toJson(changes))
                    .build());
            cardAccessLogService.log(CardAccessAction.UPDATE_QUESTIONNAIRE, clientId, REVISION_RESOURCE_TYPE,
                    revision.getId());
        }

        return new ClientQuestionnaireDTO(version, updatedAt, isEnglishEnabled(), buildItems(fields, values));
    }

    private Map<Long, String> resolveDesiredValues(List<QuestionnaireAnswerDTO> answers,
                                                   List<QuestionnaireField> fields,
                                                   Map<Long, QuestionnaireField> fieldsById) {
        Map<Long, String> desired = new LinkedHashMap<>();
        Set<Long> requestedIds = new HashSet<>();

        for (QuestionnaireAnswerDTO answer : answers == null ? List.<QuestionnaireAnswerDTO>of() : answers) {
            if (answer == null || answer.getFieldId() == null) {
                throw new ValidationException("Brak identyfikatora pola ankiety.");
            }
            QuestionnaireField field = fieldsById.get(answer.getFieldId());
            if (field == null) {
                throw new ValidationException("Pole ankiety o id " + answer.getFieldId() + " nie istnieje.");
            }
            if (field.getType() == QuestionnaireFieldType.SECTION) {
                throw new ValidationException("Tytuł sekcji \"" + field.getLabel() + "\" nie przyjmuje odpowiedzi.");
            }
            if (!requestedIds.add(field.getId())) {
                throw new ValidationException("Pole ankiety o id " + field.getId() + " występuje w żądaniu więcej niż raz.");
            }
            desired.put(field.getId(), normalizeValue(field, answer));
        }

        for (QuestionnaireField field : fields) {
            if (field.getType() != QuestionnaireFieldType.SECTION
                    && !Boolean.TRUE.equals(field.getArchived())
                    && !requestedIds.contains(field.getId())) {
                desired.put(field.getId(), null);
            }
        }
        return desired;
    }

    private String normalizeValue(QuestionnaireField field, QuestionnaireAnswerDTO answer) {
        if (field.getType() == QuestionnaireFieldType.CHECKBOX) {
            if (answer.getText() != null) {
                throw new ValidationException("Pole \"" + field.getLabel() + "\" jest polem wyboru i nie przyjmuje tekstu.");
            }
            return Boolean.TRUE.equals(answer.getChecked()) ? CHECKED_VALUE : null;
        }

        if (answer.getChecked() != null) {
            throw new ValidationException("Pole \"" + field.getLabel() + "\" jest polem tekstowym i nie przyjmuje zaznaczenia.");
        }
        if (answer.getText() == null) {
            return null;
        }
        String text = answer.getText().trim();
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new ValidationException("Odpowiedź w polu \"" + field.getLabel() + "\" może mieć maksymalnie "
                    + MAX_TEXT_LENGTH + " znaków.");
        }
        return text.isEmpty() ? null : text;
    }

    private boolean applyChanges(ClientCard card, Map<Long, String> desired,
                                 Map<Long, QuestionnaireAnswer> existing,
                                 Map<Long, QuestionnaireField> fieldsById) {
        boolean changed = false;

        for (Map.Entry<Long, String> entry : desired.entrySet()) {
            QuestionnaireAnswer current = existing.get(entry.getKey());
            String value = entry.getValue();

            if (value == null) {
                if (current != null) {
                    answerRepo.delete(current);
                    changed = true;
                }
            } else if (current == null) {
                answerRepo.save(QuestionnaireAnswer.builder()
                        .card(card)
                        .field(fieldsById.get(entry.getKey()))
                        .value(value)
                        .build());
                changed = true;
            } else if (!value.equals(current.getValue())) {
                current.setValue(value);
                answerRepo.save(current);
                changed = true;
            }
        }
        return changed;
    }

    private static List<QuestionnaireFieldChange> diff(List<QuestionnaireField> fields, Map<Long, String> desired,
                                                       Map<Long, String> current) {
        List<QuestionnaireFieldChange> changes = new ArrayList<>();
        for (QuestionnaireField field : fields) {
            if (!desired.containsKey(field.getId())) {
                continue;
            }
            String before = current.get(field.getId());
            String after = desired.get(field.getId());
            if (!Objects.equals(before, after)) {
                changes.add(new QuestionnaireFieldChange(field.getId(), field.getLabel(), changeType(field),
                        snapshot(field, before), snapshot(field, after)));
            }
        }
        return changes;
    }

    private static String changeType(QuestionnaireField field) {
        return field.getType() == QuestionnaireFieldType.CHECKBOX
                ? QuestionnaireFieldChange.TYPE_YES_NO
                : QuestionnaireFieldChange.TYPE_TEXT;
    }

    private static AnswerSnapshot snapshot(QuestionnaireField field, String value) {
        if (value == null) {
            return null;
        }
        return field.getType() == QuestionnaireFieldType.CHECKBOX
                ? new AnswerSnapshot(CHECKED_VALUE.equals(value), null)
                : new AnswerSnapshot(null, value);
    }

    private String toJson(List<QuestionnaireFieldChange> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize questionnaire revision.", e);
        }
    }

    private Map<Long, String> loadValues(Long cardId) {
        return answerRepo.findByCardId(cardId).stream()
                .collect(Collectors.toMap(answer -> answer.getField().getId(), QuestionnaireAnswer::getValue));
    }

    private List<QuestionnaireItemDTO> buildItems(List<QuestionnaireField> fields, Map<Long, String> values) {
        return fields.stream()
                .filter(field -> !Boolean.TRUE.equals(field.getArchived()) || values.containsKey(field.getId()))
                .map(field -> new QuestionnaireItemDTO(field, values.get(field.getId())))
                .toList();
    }

    private boolean isEnglishEnabled() {
        return Boolean.TRUE.equals(cardSettingsRepo.getSettings().getQuestionnaireEnglishEnabled());
    }

    private Client findClient(Long clientId) {
        return clientRepo.findOneById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with given id: " + clientId));
    }
}

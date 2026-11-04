package com.clinic.clinicmanager.service.impl;

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
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.QuestionnaireFieldService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionnaireFieldServiceImpl implements QuestionnaireFieldService {

    static final int MAX_FIELDS = 150;
    static final int MAX_LABEL_LENGTH = 300;
    static final String TYPE_CHANGE_CONFLICT = "Nie można zmienić typu pola, które ma już odpowiedzi w kartach.";
    static final String REMOVE_CONFLICT = "Pole ma odpowiedzi w kartach - zarchiwizuj je zamiast usuwać.";
    static final String SECTION_ARCHIVED = "Tytułu sekcji nie archiwizuje się - usuń go z listy.";
    private static final String AUDIT_ENTITY = "QuestionnaireField";
    private static final String SETTINGS_AUDIT_ENTITY = "CardSettings";

    private final QuestionnaireFieldRepo fieldRepo;
    private final QuestionnaireAnswerRepo answerRepo;
    private final CardSettingsRepo cardSettingsRepo;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public QuestionnaireSettingsDTO getSettings() {
        Set<Long> withAnswers = answerRepo.findFieldIdsWithAnswers();
        List<QuestionnaireFieldDTO> fields = fieldRepo.findAllByOrderByArchivedAscDisplayOrderAscIdAsc().stream()
                .map(field -> new QuestionnaireFieldDTO(field, withAnswers.contains(field.getId())))
                .toList();
        return new QuestionnaireSettingsDTO(cardSettingsRepo.getSettings().getQuestionnaireEnglishEnabled(), fields);
    }

    @Override
    @Transactional
    public QuestionnaireSettingsDTO updateSettings(QuestionnaireSettingsUpdateDTO settings) {
        if (settings == null) {
            throw new ValidationException("Brak listy pól ankiety.");
        }
        if (settings.getEnglishEnabled() == null) {
            throw new ValidationException("Brak ustawienia wersji angielskiej ankiety.");
        }
        List<QuestionnaireFieldUpdateDTO> requested = settings.getFields();
        if (requested == null) {
            throw new ValidationException("Brak listy pól ankiety.");
        }
        if (requested.size() > MAX_FIELDS) {
            throw new ValidationException("Ankieta może mieć maksymalnie " + MAX_FIELDS
                    + " elementów (pól i tytułów sekcji).");
        }

        Map<Long, QuestionnaireField> existing = fieldRepo.findAll().stream()
                .collect(Collectors.toMap(QuestionnaireField::getId, Function.identity()));
        Set<Long> withAnswers = answerRepo.findFieldIdsWithAnswers();

        validate(requested, existing);

        Set<Long> keptIds = requested.stream()
                .map(QuestionnaireFieldUpdateDTO::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (QuestionnaireFieldUpdateDTO item : requested) {
            if (item.getId() == null) {
                continue;
            }
            QuestionnaireField field = existing.get(item.getId());
            if (field.getType() != item.getType() && withAnswers.contains(field.getId())) {
                throw new ConflictException(TYPE_CHANGE_CONFLICT);
            }
        }

        List<QuestionnaireField> removed = existing.values().stream()
                .filter(field -> !keptIds.contains(field.getId()))
                .toList();
        if (removed.stream().anyMatch(field -> withAnswers.contains(field.getId()))) {
            throw new ConflictException(REMOVE_CONFLICT);
        }

        deleteFields(removed);
        saveFields(requested, existing, withAnswers);
        saveEnglishEnabled(settings.getEnglishEnabled());

        return getSettings();
    }

    private void validate(List<QuestionnaireFieldUpdateDTO> requested, Map<Long, QuestionnaireField> existing) {
        Set<Long> seenIds = new HashSet<>();
        Set<String> fieldLabels = new HashSet<>();
        Set<String> sectionLabels = new HashSet<>();
        Set<String> fieldLabelsEn = new HashSet<>();
        Set<String> sectionLabelsEn = new HashSet<>();
        String currentSection = null;

        for (QuestionnaireFieldUpdateDTO item : requested) {
            if (item == null) {
                throw new ValidationException("Nieprawidłowe pole ankiety.");
            }
            boolean section = item.getType() == QuestionnaireFieldType.SECTION;
            String label = item.getLabel() == null ? "" : item.getLabel().trim();
            if (label.isEmpty()) {
                throw new ValidationException(section
                        ? "Tytuł sekcji nie może być pusty."
                        : "Nazwa pola nie może być pusta.");
            }
            if (label.length() > MAX_LABEL_LENGTH) {
                throw new ValidationException((section ? "Tytuł sekcji" : "Nazwa pola")
                        + " może mieć maksymalnie " + MAX_LABEL_LENGTH + " znaków.");
            }
            String labelEn = normalizeLabelEn(item.getLabelEn());
            if (labelEn != null && labelEn.length() > MAX_LABEL_LENGTH) {
                throw new ValidationException("Nazwa angielska może mieć maksymalnie " + MAX_LABEL_LENGTH + " znaków.");
            }
            if (item.getType() == null) {
                throw new ValidationException("Pole \"" + label + "\" musi mieć określony typ.");
            }
            boolean archived = Boolean.TRUE.equals(item.getArchived());
            if (section && archived) {
                throw new ValidationException(SECTION_ARCHIVED);
            }
            if (item.getId() != null) {
                if (!existing.containsKey(item.getId())) {
                    throw new ValidationException("Pole ankiety o id " + item.getId() + " nie istnieje.");
                }
                if (!seenIds.add(item.getId())) {
                    throw new ValidationException("Pole ankiety o id " + item.getId() + " występuje na liście więcej niż raz.");
                }
            }
            if (archived) {
                continue;
            }
            if (section) {
                if (!sectionLabels.add(label.toLowerCase(Locale.ROOT))) {
                    throw new ValidationException("Tytuł sekcji \"" + label + "\" występuje w ankiecie więcej niż raz.");
                }
                if (labelEn != null && !sectionLabelsEn.add(labelEn.toLowerCase(Locale.ROOT))) {
                    throw new ValidationException("Nazwa angielska \"" + labelEn + "\" występuje w ankiecie więcej niż raz.");
                }
                currentSection = label;
                fieldLabels.clear();
                fieldLabelsEn.clear();
                continue;
            }
            if (!fieldLabels.add(label.toLowerCase(Locale.ROOT))) {
                throw new ValidationException("Pole \"" + label + "\" występuje więcej niż raz "
                        + sectionScope(currentSection) + ".");
            }
            if (labelEn != null && !fieldLabelsEn.add(labelEn.toLowerCase(Locale.ROOT))) {
                throw new ValidationException("Nazwa angielska \"" + labelEn + "\" występuje więcej niż raz "
                        + sectionScope(currentSection) + ".");
            }
        }
    }

    private String sectionScope(String sectionTitle) {
        return sectionTitle == null
                ? "przed pierwszym tytułem sekcji"
                : "w sekcji \"" + sectionTitle + "\"";
    }

    private String normalizeLabelEn(String labelEn) {
        if (labelEn == null) {
            return null;
        }
        String trimmed = labelEn.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void deleteFields(List<QuestionnaireField> removed) {
        if (removed.isEmpty()) {
            return;
        }
        for (QuestionnaireField field : removed) {
            QuestionnaireFieldDTO snapshot = new QuestionnaireFieldDTO(field, false);
            fieldRepo.delete(field);
            auditLogService.logDelete(AUDIT_ENTITY, field.getId(), field.getLabel(), snapshot);
        }
        try {
            fieldRepo.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(REMOVE_CONFLICT, e);
        }
    }

    private void saveFields(List<QuestionnaireFieldUpdateDTO> requested, Map<Long, QuestionnaireField> existing,
                            Set<Long> withAnswers) {
        LocalDateTime now = LocalDateTime.now();

        for (int index = 0; index < requested.size(); index++) {
            QuestionnaireFieldUpdateDTO item = requested.get(index);
            String label = item.getLabel().trim();
            String labelEn = normalizeLabelEn(item.getLabelEn());
            boolean archived = Boolean.TRUE.equals(item.getArchived());

            if (item.getId() == null) {
                QuestionnaireField saved = fieldRepo.save(QuestionnaireField.builder()
                        .label(label)
                        .labelEn(labelEn)
                        .type(item.getType())
                        .displayOrder(index)
                        .archived(archived)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
                auditLogService.logCreate(AUDIT_ENTITY, saved.getId(), saved.getLabel(),
                        new QuestionnaireFieldDTO(saved, false));
                continue;
            }

            QuestionnaireField field = existing.get(item.getId());
            boolean hasAnswers = withAnswers.contains(field.getId());
            boolean changed = !label.equals(field.getLabel())
                    || !Objects.equals(labelEn, field.getLabelEn())
                    || field.getType() != item.getType()
                    || !Objects.equals(field.getDisplayOrder(), index)
                    || !Objects.equals(field.getArchived(), archived);
            if (!changed) {
                continue;
            }

            QuestionnaireFieldDTO oldSnapshot = new QuestionnaireFieldDTO(field, hasAnswers);
            field.setLabel(label);
            field.setLabelEn(labelEn);
            field.setType(item.getType());
            field.setDisplayOrder(index);
            field.setArchived(archived);
            field.setUpdatedAt(now);
            QuestionnaireField saved = fieldRepo.save(field);
            auditLogService.logUpdate(AUDIT_ENTITY, saved.getId(), saved.getLabel(),
                    oldSnapshot, new QuestionnaireFieldDTO(saved, hasAnswers));
        }
    }

    private void saveEnglishEnabled(Boolean englishEnabled) {
        CardSettings cardSettings = cardSettingsRepo.getSettings();
        if (englishEnabled.equals(cardSettings.getQuestionnaireEnglishEnabled())) {
            return;
        }
        CardSettingsDTO oldSnapshot = new CardSettingsDTO(cardSettings);
        cardSettings.setQuestionnaireEnglishEnabled(englishEnabled);
        cardSettings.setUpdatedAt(LocalDateTime.now());
        CardSettings saved = cardSettingsRepo.save(cardSettings);
        auditLogService.logUpdate(SETTINGS_AUDIT_ENTITY, saved.getId(), null, oldSnapshot, new CardSettingsDTO(saved));
    }
}

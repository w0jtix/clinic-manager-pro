package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.NewTherapyEntryDTO;
import com.clinic.clinicmanager.DTO.TherapyEntryDTO;
import com.clinic.clinicmanager.DTO.TherapyEntryRevisionDTO;
import com.clinic.clinicmanager.DTO.TherapyEntryUpdateDTO;
import com.clinic.clinicmanager.DTO.TherapyTimelineDTO;
import com.clinic.clinicmanager.DTO.TimelineItemDTO;
import com.clinic.clinicmanager.DTO.VisitSummaryDTO;
import com.clinic.clinicmanager.DTO.VisitSummaryProductDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.TherapyEntryConflictException;
import com.clinic.clinicmanager.exceptions.TherapyEntryDayTakenException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientCard;
import com.clinic.clinicmanager.model.TherapyEntry;
import com.clinic.clinicmanager.model.TherapyEntryRevision;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.Visit;
import com.clinic.clinicmanager.model.constants.CardAccessAction;
import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.model.constants.TimelineItemType;
import com.clinic.clinicmanager.repo.ClientCardRepo;
import com.clinic.clinicmanager.repo.ClientRepo;
import com.clinic.clinicmanager.repo.TherapyEntryRepo;
import com.clinic.clinicmanager.repo.TherapyEntryRevisionRepo;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.repo.VisitRepo;
import com.clinic.clinicmanager.service.CardAccessLogService;
import com.clinic.clinicmanager.service.ClientCardService;
import com.clinic.clinicmanager.service.TherapyEntryService;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TherapyEntryServiceImpl implements TherapyEntryService {

    static final String RESOURCE_TYPE = "THERAPY_ENTRY";
    static final String REVISION_RESOURCE_TYPE = "THERAPY_ENTRY_REVISION";
    static final String TIMELINE_RESOURCE_TYPE = "THERAPY_TIMELINE";
    static final int MAX_CONTENT_LENGTH = 5000;
    static final int MAX_DAYS = 100;
    static final LocalDate TIMELINE_END = LocalDate.of(9999, 12, 31);
    static final String UNKNOWN_USER_NAME = "Nieznany użytkownik";
    private static final String OWN_ENTRY_ONLY_MESSAGE = "Możesz edytować tylko własne wpisy.";
    private static final String OWN_ENTRY_INVALIDATE_MESSAGE = "Możesz oznaczać jako błędne tylko własne wpisy.";

    private final ClientRepo clientRepo;
    private final ClientCardRepo clientCardRepo;
    private final ClientCardService clientCardService;
    private final TherapyEntryRepo entryRepo;
    private final TherapyEntryRevisionRepo revisionRepo;
    private final VisitRepo visitRepo;
    private final UserRepo userRepo;
    private final CardAccessLogService cardAccessLogService;
    private final Clock businessClock;

    @Override
    @Transactional
    public TimelineItemDTO createEntry(Long clientId, NewTherapyEntryDTO dto) {
        findEditableClient(clientId);
        Long userId = requireCurrentUserId(OWN_ENTRY_ONLY_MESSAGE);
        if (dto == null) {
            throw new ValidationException("Brak danych wpisu.");
        }
        LocalDate entryDate = validateDate(dto.getEntryDate());
        String content = normalizeContent(dto.getContent());

        ClientCard card = clientCardService.getOrCreateCard(clientId);
        clientCardRepo.lockCard(card.getId());
        entryRepo.findValidByCardIdAndEntryDate(card.getId(), entryDate).ifPresent(existing -> {
            throw new TherapyEntryDayTakenException(existing.getId());
        });

        TherapyEntry entry;
        try {
            entry = entryRepo.saveAndFlush(TherapyEntry.builder()
                    .card(card)
                    .entryDate(entryDate)
                    .content(content)
                    .authorUserId(userId)
                    .createdAt(LocalDateTime.now())
                    .version(0L)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new TherapyEntryDayTakenException(null, e);
        }

        cardAccessLogService.log(CardAccessAction.CREATE_THERAPY_ENTRY, clientId, RESOURCE_TYPE, entry.getId());
        return buildEntryItem(clientId, entry, userId);
    }

    @Override
    @Transactional
    public TimelineItemDTO updateEntry(Long clientId, Long entryId, TherapyEntryUpdateDTO dto) {
        findEditableClient(clientId);
        Long userId = requireCurrentUserId(OWN_ENTRY_ONLY_MESSAGE);
        if (dto == null || dto.getExpectedVersion() == null) {
            throw new ValidationException("Brak wersji wpisu.");
        }
        LocalDate entryDate = validateDate(dto.getEntryDate());
        String content = normalizeContent(dto.getContent());

        ClientCard card = clientCardRepo.findByClientId(clientId).orElseThrow(() -> entryNotFound(entryId));
        clientCardRepo.lockCard(card.getId());
        TherapyEntry entry = entryRepo.findByIdAndCardId(entryId, card.getId())
                .orElseThrow(() -> entryNotFound(entryId));
        if (!entry.getAuthorUserId().equals(userId)) {
            throw new AccessDeniedException(OWN_ENTRY_ONLY_MESSAGE);
        }
        if (entry.isInvalid()) {
            throw new ConflictException("Błędnego wpisu nie można edytować.");
        }
        if (!entry.getVersion().equals(dto.getExpectedVersion())) {
            throw new TherapyEntryConflictException(entry.getVersion());
        }
        if (entry.getEntryDate().equals(entryDate) && entry.getContent().equals(content)) {
            return buildEntryItem(clientId, entry, userId);
        }
        if (!entry.getEntryDate().equals(entryDate)) {
            entryRepo.findValidByCardIdAndEntryDate(card.getId(), entryDate)
                    .filter(other -> !other.getId().equals(entry.getId()))
                    .ifPresent(other -> {
                        throw new TherapyEntryDayTakenException(other.getId());
                    });
        }

        LocalDateTime now = LocalDateTime.now();
        TherapyEntryRevision revision = revisionRepo.save(TherapyEntryRevision.builder()
                .entry(entry)
                .previousEntryDate(entry.getEntryDate())
                .previousContent(entry.getContent())
                .changedAt(now)
                .changedByUserId(userId)
                .build());
        entry.setEntryDate(entryDate);
        entry.setContent(content);
        entry.setUpdatedAt(now);
        entry.setVersion(entry.getVersion() + 1);

        TherapyEntry saved;
        try {
            saved = entryRepo.saveAndFlush(entry);
        } catch (DataIntegrityViolationException e) {
            throw new TherapyEntryDayTakenException(null, e);
        }

        cardAccessLogService.log(CardAccessAction.UPDATE_THERAPY_ENTRY, clientId, REVISION_RESOURCE_TYPE,
                revision.getId());
        return buildEntryItem(clientId, saved, userId);
    }

    @Override
    @Transactional
    public TimelineItemDTO invalidateEntry(Long clientId, Long entryId) {
        findEditableClient(clientId);
        Long userId = requireCurrentUserId(OWN_ENTRY_INVALIDATE_MESSAGE);

        ClientCard card = clientCardRepo.findByClientId(clientId).orElseThrow(() -> entryNotFound(entryId));
        clientCardRepo.lockCard(card.getId());
        TherapyEntry entry = entryRepo.findByIdAndCardId(entryId, card.getId())
                .orElseThrow(() -> entryNotFound(entryId));
        if (!entry.getAuthorUserId().equals(userId)) {
            throw new AccessDeniedException(OWN_ENTRY_INVALIDATE_MESSAGE);
        }
        if (entry.isInvalid()) {
            throw new ConflictException("Wpis jest już oznaczony jako błędny.");
        }

        entry.invalidate(LocalDateTime.now(), userId);
        TherapyEntry saved = entryRepo.saveAndFlush(entry);

        cardAccessLogService.log(CardAccessAction.INVALIDATE_THERAPY_ENTRY, clientId, RESOURCE_TYPE, saved.getId());
        return buildEntryItem(clientId, saved, userId);
    }

    @Override
    @Transactional
    public List<TherapyEntryRevisionDTO> getRevisions(Long clientId, Long entryId) {
        findVisibleClient(clientId);
        ClientCard card = clientCardRepo.findByClientId(clientId).orElseThrow(() -> entryNotFound(entryId));
        TherapyEntry entry = entryRepo.findByIdAndCardId(entryId, card.getId())
                .orElseThrow(() -> entryNotFound(entryId));

        List<TherapyEntryRevision> revisions = revisionRepo.findByEntryIdOrderByChangedAtDescIdDesc(entry.getId());
        Map<Long, String> names = resolveNames(revisions.stream()
                .map(TherapyEntryRevision::getChangedByUserId)
                .collect(Collectors.toSet()));

        cardAccessLogService.log(CardAccessAction.VIEW_THERAPY_ENTRY_HISTORY, clientId, RESOURCE_TYPE, entry.getId());
        return revisions.stream()
                .map(revision -> new TherapyEntryRevisionDTO(revision, name(names, revision.getChangedByUserId())))
                .toList();
    }

    @Override
    @Transactional
    public TherapyTimelineDTO getTimeline(Long clientId, LocalDate beforeDate, int days) {
        findVisibleClient(clientId);
        if (days < 1 || days > MAX_DAYS) {
            throw new ValidationException("Liczba dni musi być z zakresu 1-" + MAX_DAYS + ".");
        }

        LocalDate before = beforeDate != null ? beforeDate : TIMELINE_END;
        PageRequest limit = PageRequest.of(0, days + 1);
        Optional<ClientCard> card = clientCardRepo.findByClientId(clientId);

        TreeSet<LocalDate> candidateDays = new TreeSet<>(Comparator.reverseOrder());
        card.ifPresent(c -> candidateDays.addAll(entryRepo.findEntryDatesBefore(c.getId(), before, limit)));
        candidateDays.addAll(visitRepo.findVisitDatesByClientIdBefore(clientId, before, limit));

        List<LocalDate> pageDays = candidateDays.stream().limit(days).toList();
        LocalDate nextBeforeDate = candidateDays.size() > days ? pageDays.get(pageDays.size() - 1) : null;

        List<TherapyEntry> entries = card.isEmpty() || pageDays.isEmpty()
                ? List.of()
                : entryRepo.findByCardIdAndEntryDateIn(card.get().getId(), pageDays);
        List<TimelineItemDTO> items = buildItems(clientId, pageDays, entries);

        cardAccessLogService.log(CardAccessAction.VIEW_THERAPY_ENTRIES, clientId, TIMELINE_RESOURCE_TYPE,
                card.map(ClientCard::getId).orElse(null));
        return new TherapyTimelineDTO(items, nextBeforeDate);
    }

    private List<TimelineItemDTO> buildItems(Long clientId, List<LocalDate> days, List<TherapyEntry> entries) {
        Long currentUserId = SessionUtils.getUserIdFromSession();
        Map<LocalDate, List<VisitSummaryDTO>> visitsByDay = loadVisits(clientId, days);
        Map<Long, String> names = resolveNames(entries.stream()
                .map(TherapyEntry::getAuthorUserId)
                .collect(Collectors.toSet()));
        Map<LocalDate, List<TherapyEntry>> entriesByDay = entries.stream()
                .collect(Collectors.groupingBy(TherapyEntry::getEntryDate));

        List<TimelineItemDTO> items = new ArrayList<>();
        for (LocalDate day : days) {
            List<TherapyEntry> dayEntries = entriesByDay.getOrDefault(day, List.of()).stream()
                    .sorted(Comparator.comparing(TherapyEntry::isInvalid)
                            .thenComparing(TherapyEntry::getCreatedAt)
                            .thenComparing(TherapyEntry::getId))
                    .toList();
            List<VisitSummaryDTO> dayVisits = visitsByDay.getOrDefault(day, List.of());
            List<VisitSummaryDTO> attended = dayVisits.stream()
                    .filter(visit -> !visit.getAbsence())
                    .toList();

            dayEntries.forEach(entry -> items.add(new TimelineItemDTO(TimelineItemType.ENTRY, day,
                    new TherapyEntryDTO(entry, name(names, entry.getAuthorUserId()), currentUserId), attended)));
            boolean hasValidEntry = dayEntries.stream().anyMatch(entry -> !entry.isInvalid());
            if (!hasValidEntry && !attended.isEmpty()) {
                items.add(new TimelineItemDTO(TimelineItemType.VISIT, day, null, attended));
            }
            dayVisits.stream()
                    .filter(VisitSummaryDTO::getAbsence)
                    .forEach(visit -> items.add(new TimelineItemDTO(TimelineItemType.ABSENCE, day, null, List.of(visit))));
        }
        return items;
    }

    private TimelineItemDTO buildEntryItem(Long clientId, TherapyEntry entry, Long currentUserId) {
        LocalDate day = entry.getEntryDate();
        List<VisitSummaryDTO> attended = loadVisits(clientId, List.of(day)).getOrDefault(day, List.of()).stream()
                .filter(visit -> !visit.getAbsence())
                .toList();
        String authorName = name(resolveNames(Set.of(entry.getAuthorUserId())), entry.getAuthorUserId());
        return new TimelineItemDTO(TimelineItemType.ENTRY, day,
                new TherapyEntryDTO(entry, authorName, currentUserId), attended);
    }

    private Map<LocalDate, List<VisitSummaryDTO>> loadVisits(Long clientId, Collection<LocalDate> days) {
        if (days.isEmpty()) {
            return Map.of();
        }
        List<Visit> visits = visitRepo.findWithEmployeeAndItemsByClientIdAndDateIn(clientId, days);
        if (visits.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<VisitSummaryProductDTO>> productsByVisit = visitRepo
                .findProductQuantitiesByVisitIdIn(visits.stream().map(Visit::getId).toList()).stream()
                .collect(Collectors.groupingBy(row -> row.getVisitId(), LinkedHashMap::new,
                        Collectors.mapping(row -> new VisitSummaryProductDTO(row.getName(), row.getQuantity(),
                                row.getColor()),
                                Collectors.toList())));

        return visits.stream()
                .sorted(Comparator.comparing(Visit::getId))
                .collect(Collectors.groupingBy(Visit::getDate,
                        Collectors.mapping(visit -> new VisitSummaryDTO(visit,
                                productsByVisit.getOrDefault(visit.getId(), List.of())), Collectors.toList())));
    }

    private Map<Long, String> resolveNames(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepo.findAllWithEmployeeByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId,
                        user -> user.getEmployee() != null ? user.getEmployee().getName() : user.getUsername(),
                        (first, second) -> first));
    }

    private static String name(Map<Long, String> names, Long userId) {
        String name = userId == null ? null : names.get(userId);
        return name != null ? name : UNKNOWN_USER_NAME;
    }

    private LocalDate validateDate(LocalDate entryDate) {
        if (entryDate == null) {
            throw new ValidationException("Data wpisu jest wymagana.");
        }
        if (entryDate.isAfter(LocalDate.now(businessClock))) {
            throw new ValidationException("Data wpisu nie może być z przyszłości.");
        }
        return entryDate;
    }

    private static String normalizeContent(String content) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new ValidationException("Treść wpisu nie może być pusta.");
        }
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw new ValidationException("Treść wpisu może mieć maksymalnie " + MAX_CONTENT_LENGTH + " znaków.");
        }
        return text;
    }

    private static Long requireCurrentUserId(String message) {
        Long userId = SessionUtils.getUserIdFromSession();
        if (userId == null) {
            throw new AccessDeniedException(message);
        }
        return userId;
    }

    private void findVisibleClient(Long clientId) {
        Client client = findClient(clientId);
        if (Boolean.TRUE.equals(client.getIsDeleted()) && !SessionUtils.hasRole(AppRoleHierarchy.MASTER)) {
            throw new ResourceNotFoundException("Client not found with given id: " + clientId);
        }
    }

    private void findEditableClient(Long clientId) {
        Client client = findClient(clientId);
        if (Boolean.TRUE.equals(client.getIsDeleted())) {
            throw new ConflictException("Klient jest usunięty. Przywróć klienta przed edycją karty.");
        }
    }

    private Client findClient(Long clientId) {
        return clientRepo.findOneById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with given id: " + clientId));
    }

    private static ResourceNotFoundException entryNotFound(Long entryId) {
        return new ResourceNotFoundException("Therapy entry not found with given id: " + entryId);
    }
}

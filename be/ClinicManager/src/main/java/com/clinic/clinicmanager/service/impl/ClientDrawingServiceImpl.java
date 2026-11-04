package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.ClientDrawingDTO;
import com.clinic.clinicmanager.DTO.DrawItem;
import com.clinic.clinicmanager.DTO.DrawingGroupDTO;
import com.clinic.clinicmanager.DTO.DrawingLayerDTO;
import com.clinic.clinicmanager.DTO.DrawingSheetDTO;
import com.clinic.clinicmanager.DTO.DrawingTemplateImageRefDTO;
import com.clinic.clinicmanager.DTO.DrawingTemplateRefDTO;
import com.clinic.clinicmanager.DTO.DrawingUserDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.DrawingConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientCard;
import com.clinic.clinicmanager.model.DrawingLayer;
import com.clinic.clinicmanager.model.DrawingSheet;
import com.clinic.clinicmanager.model.DrawingTemplate;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.CardAccessAction;
import com.clinic.clinicmanager.model.constants.DrawingSheetStatus;
import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.repo.ClientCardRepo;
import com.clinic.clinicmanager.repo.ClientRepo;
import com.clinic.clinicmanager.repo.DrawingLayerRepo;
import com.clinic.clinicmanager.repo.DrawingSheetRepo;
import com.clinic.clinicmanager.repo.DrawingTemplateImageRepo;
import com.clinic.clinicmanager.repo.DrawingTemplateRepo;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.repo.projection.DrawingTemplateImageInfoProjection;
import com.clinic.clinicmanager.service.CardAccessLogService;
import com.clinic.clinicmanager.service.ClientCardService;
import com.clinic.clinicmanager.service.ClientDrawingService;
import com.clinic.clinicmanager.service.FileStorage;
import com.clinic.clinicmanager.utils.CardEncryption;
import com.clinic.clinicmanager.utils.DrawingColors;
import com.clinic.clinicmanager.utils.DrawingItems;
import com.clinic.clinicmanager.utils.ImageUtils;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientDrawingServiceImpl implements ClientDrawingService {

    static final String DRAWING_RESOURCE_TYPE = "DRAWING";
    static final String SHEET_RESOURCE_TYPE = "DRAWING_SHEET";
    static final String LAYER_RESOURCE_TYPE = "DRAWING_LAYER";
    static final int DEFAULT_WIDTH = 1536;
    static final int DEFAULT_HEIGHT = 2048;
    static final String FILE_KEY_PREFIX = "drawings/";
    static final String FILE_KEY_SUFFIX = ".bin";
    static final String UNKNOWN_USER_NAME = "Nieznany użytkownik";
    static final String EMPTY_DRAWING_MESSAGE = "Rysunek jest pusty.";
    private static final String CHANGED_ELSEWHERE_MESSAGE =
            "Bieżąca kartka została zmieniona na innym urządzeniu. Odśwież rysunek.";
    private static final String OWN_LAYER_ONLY_MESSAGE = "Możesz zmieniać tylko własne zaznaczenia.";
    private static final String READ_ONLY_SHEET_MESSAGE = "Szablon jest zarchiwizowany. Kartka jest tylko do podglądu.";

    private final ClientRepo clientRepo;
    private final ClientCardRepo clientCardRepo;
    private final ClientCardService clientCardService;
    private final DrawingSheetRepo sheetRepo;
    private final DrawingLayerRepo layerRepo;
    private final DrawingTemplateRepo templateRepo;
    private final DrawingTemplateImageRepo templateImageRepo;
    private final UserRepo userRepo;
    private final FileStorage fileStorage;
    private final CardEncryption cardEncryption;
    private final CardAccessLogService cardAccessLogService;

    @Override
    @Transactional
    public ClientDrawingDTO getDrawing(Long clientId) {
        findVisibleClient(clientId);
        Optional<ClientCard> card = clientCardRepo.findByClientId(clientId);
        List<DrawingSheet> sheets = card
                .map(c -> sheetRepo.findByCardIdOrderBySheetNumberDesc(c.getId()))
                .orElseGet(List::of);

        cardAccessLogService.log(CardAccessAction.VIEW_DRAWING, clientId, DRAWING_RESOURCE_TYPE,
                card.map(ClientCard::getId).orElse(null));
        return buildState(sheets);
    }

    @Override
    @Transactional
    public byte[] getLayerItems(Long clientId, Long sheetId, Long layerId) {
        findVisibleClient(clientId);
        DrawingLayer layer = layerRepo.findByIdAndSheetIdAndSheetCardClientId(layerId, sheetId, clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Drawing layer not found with given id: " + layerId));

        return cardEncryption.decryptBytes(fileStorage.get(layer.getFileKey()));
    }

    @Override
    @Transactional
    public void logSheetView(Long clientId, Long sheetId) {
        findVisibleClient(clientId);
        DrawingSheet sheet = sheetRepo.findByIdAndCardClientId(sheetId, clientId)
                .orElseThrow(() -> sheetNotFound(sheetId));

        cardAccessLogService.log(CardAccessAction.VIEW_DRAWING_SHEET, clientId, SHEET_RESOURCE_TYPE, sheet.getId());
    }

    @Override
    @Transactional
    public ClientDrawingDTO createSheet(Long clientId, Long templateId, List<DrawItem> items, Long closeSheetId) {
        findEditableClient(clientId);
        Long userId = requireCurrentUserId();
        if (templateId == null) {
            throw new ValidationException("Nie wybrano szablonu rysunku.");
        }

        DrawingTemplate template = templateRepo.lockSharedById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException("Drawing template not found with given id: " + templateId));
        if (template.isArchived()) {
            throw new ConflictException(DrawingTemplateServiceImpl.ARCHIVED_MESSAGE);
        }
        Optional<DrawingTemplateImageInfoProjection> templateImage = template.getCurrentImageId() == null
                ? Optional.empty()
                : templateImageRepo.findInfoById(template.getCurrentImageId());
        int width = templateImage.map(DrawingTemplateImageInfoProjection::getWidth).orElse(DEFAULT_WIDTH);
        int height = templateImage.map(DrawingTemplateImageInfoProjection::getHeight).orElse(DEFAULT_HEIGHT);
        byte[] content = items == null || items.isEmpty() ? null : DrawingItems.normalize(items, width, height);

        ClientCard card = clientCardService.getOrCreateCard(clientId);
        clientCardRepo.lockCard(card.getId());
        Optional<DrawingSheet> open = sheetRepo.lockByCardIdAndTemplateIdAndStatus(card.getId(), templateId,
                DrawingSheetStatus.OPEN);
        LocalDateTime now = LocalDateTime.now();

        if (open.isEmpty()) {
            if (closeSheetId != null) {
                throw new ConflictException(CHANGED_ELSEWHERE_MESSAGE);
            }
            if (content == null) {
                throw new ValidationException(EMPTY_DRAWING_MESSAGE);
            }
        } else {
            DrawingSheet current = open.get();
            if (!current.getId().equals(closeSheetId)) {
                throw new ConflictException(CHANGED_ELSEWHERE_MESSAGE);
            }
            if (!layerRepo.existsBySheetId(current.getId())) {
                throw new ValidationException("Bieżąca kartka jest pusta.");
            }
            current.close(now, userId);
            sheetRepo.saveAndFlush(current);
        }

        DrawingSheet sheet;
        try {
            sheet = sheetRepo.saveAndFlush(DrawingSheet.builder()
                    .card(card)
                    .templateId(templateId)
                    .templateImageId(templateImage.map(DrawingTemplateImageInfoProjection::getId).orElse(null))
                    .sheetNumber(sheetRepo.findMaxSheetNumber(card.getId(), templateId) + 1)
                    .width(width)
                    .height(height)
                    .status(DrawingSheetStatus.OPEN)
                    .createdAt(now)
                    .createdByUserId(userId)
                    .build());
            if (content != null) {
                DrawingLayer layer = DrawingLayer.builder()
                        .sheet(sheet)
                        .authorUserId(userId)
                        .version(1L)
                        .createdAt(now)
                        .build();
                applyContent(layer, content, now, userId);
                layerRepo.saveAndFlush(layer);
            }
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(CHANGED_ELSEWHERE_MESSAGE, e);
        }

        cardAccessLogService.log(CardAccessAction.CREATE_DRAWING_SHEET, clientId, SHEET_RESOURCE_TYPE, sheet.getId());
        return buildState(sheetRepo.findByCardIdOrderBySheetNumberDesc(card.getId()));
    }

    @Override
    @Transactional
    public ClientDrawingDTO updateLayerItems(Long clientId, Long sheetId, Long authorUserId, List<DrawItem> items,
                                             Long expectedVersion) {
        findEditableClient(clientId);
        Long userId = requireCurrentUserId();
        if (!userId.equals(authorUserId)) {
            throw new AccessDeniedException(OWN_LAYER_ONLY_MESSAGE);
        }
        if (expectedVersion == null) {
            throw new ValidationException("Brak wersji rysunku.");
        }

        ClientCard card = clientCardRepo.findByClientId(clientId)
                .orElseThrow(() -> sheetNotFound(sheetId));
        DrawingSheet sheet = sheetRepo.lockByIdAndCardId(sheetId, card.getId())
                .orElseThrow(() -> sheetNotFound(sheetId));
        if (!sheet.isOpen()) {
            throw new ConflictException("Kartka jest zamknięta.");
        }
        boolean archivedTemplate = templateRepo.findById(sheet.getTemplateId())
                .map(DrawingTemplate::isArchived)
                .orElse(true);
        if (archivedTemplate) {
            throw new ConflictException(READ_ONLY_SHEET_MESSAGE);
        }

        Optional<DrawingLayer> existing = layerRepo.findBySheetIdAndAuthorUserId(sheet.getId(), authorUserId);
        long currentVersion = existing.map(DrawingLayer::getVersion).orElse(0L);
        if (currentVersion != expectedVersion) {
            throw new DrawingConflictException(currentVersion);
        }

        if (items != null && items.isEmpty()) {
            existing.ifPresent(layer -> deleteLayer(clientId, layer));
            return buildState(sheetRepo.findByCardIdOrderBySheetNumberDesc(card.getId()));
        }

        byte[] content = DrawingItems.normalize(items, sheet.getWidth(), sheet.getHeight());
        if (existing.isPresent() && ImageUtils.sha256Hex(content).equals(existing.get().getContentSha256())) {
            return buildState(sheetRepo.findByCardIdOrderBySheetNumberDesc(card.getId()));
        }

        LocalDateTime now = LocalDateTime.now();
        DrawingLayer layer = existing.orElseGet(() -> DrawingLayer.builder()
                .sheet(sheet)
                .authorUserId(authorUserId)
                .version(0L)
                .createdAt(now)
                .build());
        String previousKey = layer.getFileKey();
        applyContent(layer, content, now, userId);
        layer.setVersion(currentVersion + 1);
        try {
            layer = layerRepo.saveAndFlush(layer);
        } catch (DataIntegrityViolationException e) {
            throw new DrawingConflictException(1L);
        }
        if (previousKey != null) {
            deleteAfterCommit(previousKey);
        }

        cardAccessLogService.log(CardAccessAction.UPDATE_DRAWING, clientId, LAYER_RESOURCE_TYPE, layer.getId());
        return buildState(sheetRepo.findByCardIdOrderBySheetNumberDesc(card.getId()));
    }

    private void deleteLayer(Long clientId, DrawingLayer layer) {
        layerRepo.delete(layer);
        layerRepo.flush();
        deleteAfterCommit(layer.getFileKey());
        cardAccessLogService.log(CardAccessAction.UPDATE_DRAWING, clientId, LAYER_RESOURCE_TYPE, layer.getId());
    }

    private void applyContent(DrawingLayer layer, byte[] content, LocalDateTime at, Long userId) {
        layer.setFileKey(storeEncrypted(content));
        layer.setSizeBytes(content.length);
        layer.setContentSha256(ImageUtils.sha256Hex(content));
        layer.setUpdatedAt(at);
        layer.setUpdatedByUserId(userId);
    }

    private String storeEncrypted(byte[] content) {
        String key = FILE_KEY_PREFIX + UUID.randomUUID() + FILE_KEY_SUFFIX;
        byte[] encrypted = cardEncryption.encryptBytes(content);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteFile(key);
                }
            }
        });
        fileStorage.put(key, encrypted);
        return key;
    }

    private void deleteAfterCommit(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteFile(key);
            }
        });
    }

    private void deleteFile(String key) {
        try {
            fileStorage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Failed to delete drawing file {}: {}", key, e.getMessage());
        }
    }

    private ClientDrawingDTO buildState(List<DrawingSheet> sheets) {
        Long currentUserId = SessionUtils.getUserIdFromSession();
        List<DrawingLayer> layers = sheets.isEmpty()
                ? List.of()
                : layerRepo.findBySheetIdInOrderByCreatedAtAscIdAsc(sheets.stream().map(DrawingSheet::getId).toList());

        Set<Long> userIds = new HashSet<>();
        if (currentUserId != null) {
            userIds.add(currentUserId);
        }
        layers.forEach(layer -> {
            userIds.add(layer.getAuthorUserId());
            userIds.add(layer.getUpdatedByUserId());
        });
        Map<Long, DrawingAuthor> authors = resolveAuthors(userIds);

        Map<Long, List<DrawingLayerDTO>> layersBySheet = layers.stream()
                .collect(Collectors.groupingBy(layer -> layer.getSheet().getId(),
                        Collectors.mapping(layer -> toLayerDTO(layer, authors), Collectors.toList())));
        Map<Long, List<DrawingSheet>> sheetsByTemplate = sheets.stream()
                .collect(Collectors.groupingBy(DrawingSheet::getTemplateId, LinkedHashMap::new, Collectors.toList()));

        List<DrawingTemplate> templates = groupTemplates(sheetsByTemplate.keySet());
        Map<Long, DrawingTemplateImageRefDTO> images = templateImages(templates);
        List<DrawingGroupDTO> groups = templates.stream()
                .map(template -> toGroup(template, images.get(template.getCurrentImageId()),
                        sheetsByTemplate.getOrDefault(template.getId(), List.of()), layersBySheet))
                .toList();

        DrawingAuthor current = author(authors, currentUserId);
        return new ClientDrawingDTO(new DrawingUserDTO(currentUserId, current.name(), current.color()), groups);
    }

    private List<DrawingTemplate> groupTemplates(Set<Long> usedTemplateIds) {
        List<DrawingTemplate> templates = new ArrayList<>(templateRepo.findByArchivedFalseOrderByDisplayOrderAscIdAsc());
        Set<Long> missing = new HashSet<>(usedTemplateIds);
        templates.forEach(template -> missing.remove(template.getId()));
        if (!missing.isEmpty()) {
            templates.addAll(templateRepo.findAllById(missing).stream()
                    .sorted(Comparator.comparing(DrawingTemplate::getName, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(DrawingTemplate::getId))
                    .toList());
        }
        return templates;
    }

    private Map<Long, DrawingTemplateImageRefDTO> templateImages(List<DrawingTemplate> templates) {
        Set<Long> imageIds = templates.stream()
                .map(DrawingTemplate::getCurrentImageId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (imageIds.isEmpty()) {
            return new HashMap<>();
        }
        return templateImageRepo.findInfoByIdIn(imageIds).stream()
                .collect(Collectors.toMap(DrawingTemplateImageInfoProjection::getId, DrawingTemplateImageRefDTO::new));
    }

    private static DrawingGroupDTO toGroup(DrawingTemplate template, DrawingTemplateImageRefDTO image,
                                           List<DrawingSheet> sheets, Map<Long, List<DrawingLayerDTO>> layersBySheet) {
        DrawingSheetDTO currentSheet = sheets.stream()
                .filter(DrawingSheet::isOpen)
                .findFirst()
                .map(sheet -> new DrawingSheetDTO(sheet, layersBySheet.getOrDefault(sheet.getId(), List.of())))
                .orElse(null);
        List<DrawingSheetDTO> closedSheets = sheets.stream()
                .filter(sheet -> !sheet.isOpen())
                .map(sheet -> new DrawingSheetDTO(sheet, layersBySheet.getOrDefault(sheet.getId(), List.of())))
                .toList();
        return new DrawingGroupDTO(new DrawingTemplateRefDTO(template, image), currentSheet, closedSheets);
    }

    private DrawingLayerDTO toLayerDTO(DrawingLayer layer, Map<Long, DrawingAuthor> authors) {
        DrawingAuthor author = author(authors, layer.getAuthorUserId());
        DrawingAuthor updatedBy = author(authors, layer.getUpdatedByUserId());
        return new DrawingLayerDTO(layer.getId(), layer.getAuthorUserId(), author.name(), author.color(),
                layer.getVersion(), layer.getUpdatedAt(), layer.getUpdatedByUserId(), updatedBy.name());
    }

    private Map<Long, DrawingAuthor> resolveAuthors(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepo.findAllWithEmployeeByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, this::toAuthor, (first, second) -> first));
    }

    private DrawingAuthor toAuthor(User user) {
        Employee employee = user.getEmployee();
        if (employee == null) {
            return new DrawingAuthor(user.getUsername(), DrawingColors.DEFAULT_COLOR);
        }
        String color = employee.getDrawingColor() != null ? employee.getDrawingColor() : DrawingColors.DEFAULT_COLOR;
        return new DrawingAuthor(employee.getName(), color);
    }

    private static DrawingAuthor author(Map<Long, DrawingAuthor> authors, Long userId) {
        DrawingAuthor author = userId == null ? null : authors.get(userId);
        return author != null ? author : new DrawingAuthor(UNKNOWN_USER_NAME, DrawingColors.DEFAULT_COLOR);
    }

    private static Long requireCurrentUserId() {
        Long userId = SessionUtils.getUserIdFromSession();
        if (userId == null) {
            throw new AccessDeniedException(OWN_LAYER_ONLY_MESSAGE);
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

    private static ResourceNotFoundException sheetNotFound(Long sheetId) {
        return new ResourceNotFoundException("Drawing sheet not found with given id: " + sheetId);
    }

    record DrawingAuthor(String name, String color) {
    }
}

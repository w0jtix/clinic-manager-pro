package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.DrawingTemplateImageDTO;
import com.clinic.clinicmanager.DTO.DrawingTemplateSettingsDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.DrawingTemplate;
import com.clinic.clinicmanager.model.DrawingTemplateImage;
import com.clinic.clinicmanager.model.constants.DrawingTemplateDeleteResult;
import com.clinic.clinicmanager.repo.DrawingSheetRepo;
import com.clinic.clinicmanager.repo.DrawingTemplateImageRepo;
import com.clinic.clinicmanager.repo.DrawingTemplateRepo;
import com.clinic.clinicmanager.repo.projection.DrawingTemplateImageInfoProjection;
import com.clinic.clinicmanager.repo.projection.DrawingTemplateUsageProjection;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.DrawingTemplateService;
import com.clinic.clinicmanager.utils.ImageUtils;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
public class DrawingTemplateServiceImpl implements DrawingTemplateService {

    static final String ENTITY_TYPE = "DrawingTemplate";
    static final int MAX_ACTIVE_TEMPLATES = 10;
    static final int MAX_NAME_LENGTH = 60;
    static final long MAX_UPLOAD_SIZE_BYTES = 2L * 1024L * 1024L;
    static final int MAX_UPLOAD_SIDE_PX = 6000;
    static final int MAX_STORED_SIDE_PX = 2048;
    static final String ARCHIVED_MESSAGE = "Szablon jest zarchiwizowany.";
    static final String NAME_REQUIRED_MESSAGE = "Nazwa szablonu jest wymagana (maksymalnie 60 znaków).";
    static final String DUPLICATE_NAME_MESSAGE = "Szablon o tej nazwie już istnieje.";
    static final String DUPLICATE_ARCHIVED_NAME_MESSAGE = "Szablon o tej nazwie już istnieje.";
    static final String LIMIT_MESSAGE = "Można mieć maksymalnie 10 aktywnych szablonów.";
    private static final long TEMPLATES_LOCK_KEY = 0x50434454504C54L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/png", "image/jpeg");

    private final DrawingTemplateRepo templateRepo;
    private final DrawingTemplateImageRepo imageRepo;
    private final DrawingSheetRepo sheetRepo;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public List<DrawingTemplateSettingsDTO> getTemplates() {
        List<DrawingTemplate> templates = new ArrayList<>(templateRepo.findByArchivedFalseOrderByDisplayOrderAscIdAsc());
        templates.addAll(templateRepo.findByArchivedTrueOrderByArchivedAtDescIdDesc());

        Map<Long, DrawingTemplateImageInfoProjection> images = imagesOf(templates);
        Map<Long, Long> clientCounts = sheetRepo.countClientsPerTemplate().stream()
                .collect(Collectors.toMap(DrawingTemplateUsageProjection::getTemplateId,
                        DrawingTemplateUsageProjection::getClientCount));

        return templates.stream()
                .map(template -> new DrawingTemplateSettingsDTO(template,
                        imageDTO(images.get(template.getCurrentImageId())),
                        clientCounts.getOrDefault(template.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public DrawingTemplateSettingsDTO createTemplate(String name, MultipartFile file) {
        String normalizedName = normalizeName(name);
        PreparedImage image = hasFile(file) ? prepareImage(file) : null;

        templateRepo.acquireTransactionLock(TEMPLATES_LOCK_KEY);
        requireActiveSlot();
        if (templateRepo.existsByArchivedFalseAndNameIgnoreCase(normalizedName)) {
            throw new ConflictException(DUPLICATE_NAME_MESSAGE);
        }
        if (templateRepo.existsByArchivedTrueAndNameIgnoreCase(normalizedName)) {
            throw new ConflictException(DUPLICATE_ARCHIVED_NAME_MESSAGE);
        }

        LocalDateTime now = LocalDateTime.now();
        Long userId = SessionUtils.getUserIdFromSession();
        DrawingTemplate template = saveTemplate(DrawingTemplate.builder()
                .name(normalizedName)
                .displayOrder(templateRepo.findMaxActiveDisplayOrder() + 1)
                .archived(false)
                .createdAt(now)
                .createdByUserId(userId)
                .updatedAt(now)
                .build());

        DrawingTemplateImageDTO imageDTO = null;
        if (image != null) {
            DrawingTemplateImage savedImage = saveImage(template.getId(), image, now, userId);
            template.setCurrentImageId(savedImage.getId());
            template = saveTemplate(template);
            imageDTO = imageDTO(savedImage);
        }

        auditLogService.logCreate(ENTITY_TYPE, template.getId(), template.getName(), audit(template, imageDTO));
        return new DrawingTemplateSettingsDTO(template, imageDTO, 0L);
    }

    @Override
    @Transactional
    public DrawingTemplateSettingsDTO updateTemplate(Long id, String name, MultipartFile file, boolean removeImage) {
        String normalizedName = normalizeName(name);
        boolean replaceImage = hasFile(file);
        if (replaceImage && removeImage) {
            throw new ValidationException("Nie można jednocześnie wgrać nowego obrazu i usunąć obecnego.");
        }
        PreparedImage image = replaceImage ? prepareImage(file) : null;

        templateRepo.acquireTransactionLock(TEMPLATES_LOCK_KEY);
        DrawingTemplate template = lockTemplate(id);
        if (template.isArchived()) {
            throw new ConflictException(ARCHIVED_MESSAGE);
        }
        if (templateRepo.existsByArchivedFalseAndNameIgnoreCaseAndIdNot(normalizedName, id)) {
            throw new ConflictException(DUPLICATE_NAME_MESSAGE);
        }
        if (templateRepo.existsByArchivedTrueAndNameIgnoreCaseAndIdNot(normalizedName, id)) {
            throw new ConflictException(DUPLICATE_ARCHIVED_NAME_MESSAGE);
        }

        Long previousImageId = template.getCurrentImageId();
        DrawingTemplateImageDTO previousImage = currentImage(template);
        DrawingTemplateAudit before = audit(template, previousImage);

        LocalDateTime now = LocalDateTime.now();
        DrawingTemplateImageDTO currentImage = previousImage;
        if (image != null) {
            currentImage = imageDTO(saveImage(id, image, now, SessionUtils.getUserIdFromSession()));
        } else if (removeImage) {
            currentImage = null;
        }
        Long currentImageId = currentImage == null ? null : currentImage.getId();

        if (normalizedName.equals(template.getName()) && Objects.equals(currentImageId, previousImageId)) {
            return new DrawingTemplateSettingsDTO(template, currentImage, sheetRepo.countClientsByTemplateId(id));
        }

        template.setName(normalizedName);
        template.setCurrentImageId(currentImageId);
        template.setUpdatedAt(now);
        template = saveTemplate(template);

        if (!Objects.equals(currentImageId, previousImageId)) {
            sheetRepo.lockOpenSheetIdsByTemplateId(id);
            sheetRepo.rebaseBlankOpenSheets(id, currentImageId,
                    currentImage == null ? ClientDrawingServiceImpl.DEFAULT_WIDTH : currentImage.getWidth(),
                    currentImage == null ? ClientDrawingServiceImpl.DEFAULT_HEIGHT : currentImage.getHeight());
        }

        if (previousImageId != null && !previousImageId.equals(currentImageId)
                && !sheetRepo.existsByTemplateImageId(previousImageId)) {
            imageRepo.deleteImageById(previousImageId);
        }

        auditLogService.logUpdate(ENTITY_TYPE, id, template.getName(), before, audit(template, currentImage));
        return new DrawingTemplateSettingsDTO(template, currentImage, sheetRepo.countClientsByTemplateId(id));
    }

    @Override
    @Transactional
    public DrawingTemplateDeleteResult deleteTemplate(Long id) {
        templateRepo.acquireTransactionLock(TEMPLATES_LOCK_KEY);
        DrawingTemplate template = lockTemplate(id);
        if (template.isArchived()) {
            throw new ConflictException(ARCHIVED_MESSAGE);
        }
        DrawingTemplateAudit before = audit(template, currentImage(template));

        if (sheetRepo.existsByTemplateId(id)) {
            template.archive(LocalDateTime.now());
            template = saveTemplate(template);
            auditLogService.logUpdate(ENTITY_TYPE, id, template.getName(), before, before.withArchived(true));
            return DrawingTemplateDeleteResult.ARCHIVED;
        }

        try {
            templateRepo.delete(template);
            templateRepo.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Szablon został w tym czasie użyty w karcie klienta. Spróbuj ponownie.", e);
        }
        auditLogService.logDelete(ENTITY_TYPE, id, template.getName(), before);
        return DrawingTemplateDeleteResult.DELETED;
    }

    @Override
    @Transactional
    public DrawingTemplateSettingsDTO restoreTemplate(Long id) {
        templateRepo.acquireTransactionLock(TEMPLATES_LOCK_KEY);
        DrawingTemplate template = lockTemplate(id);
        if (!template.isArchived()) {
            throw new ConflictException("Szablon nie jest zarchiwizowany.");
        }
        requireActiveSlot();
        if (templateRepo.existsByArchivedFalseAndNameIgnoreCase(template.getName())) {
            throw new ConflictException(DUPLICATE_NAME_MESSAGE);
        }

        DrawingTemplateImageDTO image = currentImage(template);
        DrawingTemplateAudit before = audit(template, image);
        template.restore(templateRepo.findMaxActiveDisplayOrder() + 1, LocalDateTime.now());
        template = saveTemplate(template);

        auditLogService.logUpdate(ENTITY_TYPE, id, template.getName(), before, audit(template, image));
        return new DrawingTemplateSettingsDTO(template, image, sheetRepo.countClientsByTemplateId(id));
    }

    @Override
    @Transactional
    public List<DrawingTemplateSettingsDTO> reorderTemplates(List<Long> ids) {
        templateRepo.acquireTransactionLock(TEMPLATES_LOCK_KEY);
        List<DrawingTemplate> active = templateRepo.findByArchivedFalseOrderByDisplayOrderAscIdAsc();
        Map<Long, DrawingTemplate> byId = active.stream()
                .collect(Collectors.toMap(DrawingTemplate::getId, Function.identity()));
        if (ids == null || ids.size() != active.size() || !byId.keySet().equals(new HashSet<>(ids))) {
            throw new ValidationException("Lista szablonów do ustawienia kolejności jest nieaktualna. Odśwież stronę.");
        }

        Map<Long, DrawingTemplateImageInfoProjection> images = imagesOf(active);
        LocalDateTime now = LocalDateTime.now();
        List<DrawingTemplate> changed = new ArrayList<>();
        List<DrawingTemplateAudit> before = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            DrawingTemplate template = byId.get(ids.get(i));
            int displayOrder = i + 1;
            if (template.getDisplayOrder() != displayOrder) {
                before.add(audit(template, imageDTO(images.get(template.getCurrentImageId()))));
                template.setDisplayOrder(displayOrder);
                template.setUpdatedAt(now);
                changed.add(template);
            }
        }

        if (!changed.isEmpty()) {
            templateRepo.saveAllAndFlush(changed);
            for (int i = 0; i < changed.size(); i++) {
                DrawingTemplate template = changed.get(i);
                DrawingTemplateAudit old = before.get(i);
                auditLogService.logUpdate(ENTITY_TYPE, template.getId(), template.getName(), old,
                        old.withDisplayOrder(template.getDisplayOrder()));
            }
        }
        return getTemplates();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] getTemplateImage(Long imageId) {
        return imageRepo.findImageById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Drawing template image not found with given id: " + imageId));
    }

    private void requireActiveSlot() {
        if (templateRepo.countByArchivedFalse() >= MAX_ACTIVE_TEMPLATES) {
            throw new ConflictException(LIMIT_MESSAGE);
        }
    }

    private DrawingTemplate lockTemplate(Long id) {
        return templateRepo.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Drawing template not found with given id: " + id));
    }

    private DrawingTemplate saveTemplate(DrawingTemplate template) {
        try {
            return templateRepo.saveAndFlush(template);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(DUPLICATE_NAME_MESSAGE, e);
        }
    }

    private DrawingTemplateImage saveImage(Long templateId, PreparedImage image, LocalDateTime at, Long userId) {
        return imageRepo.saveAndFlush(DrawingTemplateImage.builder()
                .templateId(templateId)
                .image(image.png())
                .width(image.width())
                .height(image.height())
                .sizeBytes(image.png().length)
                .createdAt(at)
                .createdByUserId(userId)
                .build());
    }

    private DrawingTemplateImageDTO currentImage(DrawingTemplate template) {
        if (template.getCurrentImageId() == null) {
            return null;
        }
        return imageRepo.findInfoById(template.getCurrentImageId())
                .map(DrawingTemplateServiceImpl::imageDTO)
                .orElse(null);
    }

    private Map<Long, DrawingTemplateImageInfoProjection> imagesOf(List<DrawingTemplate> templates) {
        Set<Long> imageIds = templates.stream()
                .map(DrawingTemplate::getCurrentImageId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (imageIds.isEmpty()) {
            return new HashMap<>();
        }
        return imageRepo.findInfoByIdIn(imageIds).stream()
                .collect(Collectors.toMap(DrawingTemplateImageInfoProjection::getId, Function.identity()));
    }

    private static DrawingTemplateImageDTO imageDTO(DrawingTemplateImageInfoProjection image) {
        return image == null ? null : new DrawingTemplateImageDTO(image);
    }

    private static DrawingTemplateImageDTO imageDTO(DrawingTemplateImage image) {
        return new DrawingTemplateImageDTO(image.getId(), image.getWidth(), image.getHeight(), image.getSizeBytes());
    }

    private static DrawingTemplateAudit audit(DrawingTemplate template, DrawingTemplateImageDTO image) {
        return new DrawingTemplateAudit(template.getId(), template.getName(), template.getDisplayOrder(),
                template.isArchived(),
                image == null ? null : image.getId(),
                image == null ? null : image.getWidth(),
                image == null ? null : image.getHeight(),
                image == null ? null : image.getSizeBytes());
    }

    private static boolean hasFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private static String normalizeName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(NAME_REQUIRED_MESSAGE);
        }
        return trimmed;
    }

    private PreparedImage prepareImage(MultipartFile file) {
        BufferedImage normalized = ImageUtils.scaleToFit(readTemplate(file), MAX_STORED_SIDE_PX);
        return new PreparedImage(ImageUtils.encodePng(normalized), normalized.getWidth(), normalized.getHeight());
    }

    private BufferedImage readTemplate(MultipartFile file) {
        String contentType = file.getContentType() == null ? null : file.getContentType().toLowerCase();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ValidationException("Dozwolone formaty szablonu to PNG i JPEG.");
        }
        if (file.getSize() > MAX_UPLOAD_SIZE_BYTES) {
            throw new ValidationException("Plik szablonu jest za duży (maksymalnie 2 MB).");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new ValidationException("Nie udało się odczytać pliku.", e);
        }
        if (!ImageUtils.isPng(bytes) && !ImageUtils.isJpeg(bytes)) {
            throw new ValidationException("Plik nie jest poprawnym obrazem PNG ani JPEG.");
        }

        Dimension dimensions = ImageUtils.readDimensions(bytes);
        if (dimensions == null) {
            throw new ValidationException("Plik nie jest poprawnym obrazem PNG ani JPEG.");
        }
        if (Math.max(dimensions.width, dimensions.height) > MAX_UPLOAD_SIDE_PX) {
            throw new ValidationException("Szablon jest za duży (dłuższy bok maksymalnie " + MAX_UPLOAD_SIDE_PX + " px).");
        }

        BufferedImage image = ImageUtils.decode(bytes);
        if (image == null) {
            throw new ValidationException("Plik nie jest poprawnym obrazem PNG ani JPEG.");
        }
        return image;
    }

    record PreparedImage(byte[] png, int width, int height) {
    }

    record DrawingTemplateAudit(Long id, String name, Integer displayOrder, boolean archived, Long imageId,
                                Integer imageWidth, Integer imageHeight, Integer imageSizeBytes) {

        DrawingTemplateAudit withArchived(boolean value) {
            return new DrawingTemplateAudit(id, name, displayOrder, value, imageId, imageWidth, imageHeight,
                    imageSizeBytes);
        }

        DrawingTemplateAudit withDisplayOrder(Integer value) {
            return new DrawingTemplateAudit(id, name, value, archived, imageId, imageWidth, imageHeight,
                    imageSizeBytes);
        }
    }
}

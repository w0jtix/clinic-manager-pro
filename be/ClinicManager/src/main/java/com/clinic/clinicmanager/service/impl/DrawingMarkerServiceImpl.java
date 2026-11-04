package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.DrawingMarkerDTO;
import com.clinic.clinicmanager.DTO.request.DrawingMarkerRequest;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.DrawingMarker;
import com.clinic.clinicmanager.model.constants.DrawingMarkerShape;
import com.clinic.clinicmanager.repo.DrawingMarkerRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.DrawingMarkerService;
import com.clinic.clinicmanager.utils.DrawingColors;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DrawingMarkerServiceImpl implements DrawingMarkerService {

    static final String ENTITY_TYPE = "DrawingMarker";
    static final int MAX_MARKERS = 30;
    static final int MAX_NAME_LENGTH = 40;
    static final String NAME_REQUIRED_MESSAGE = "Nazwa oznaczenia jest wymagana (maksymalnie 40 znaków).";
    static final String SHAPE_REQUIRED_MESSAGE = "Wybierz kształt oznaczenia.";
    static final String INVALID_COLOR_MESSAGE = "Nieprawidłowy kolor oznaczenia (format #RRGGBB).";
    static final String DUPLICATE_NAME_MESSAGE = "Oznaczenie o tej nazwie już istnieje.";
    static final String LIMIT_MESSAGE = "Osiągnięto limit 30 oznaczeń.";
    static final String STALE_ORDER_MESSAGE = "Lista oznaczeń do ustawienia kolejności jest nieaktualna. Odśwież stronę.";
    private static final long MARKERS_LOCK_KEY = 0x50434444524D4BL;

    private final DrawingMarkerRepo markerRepo;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public List<DrawingMarkerDTO> getMarkers() {
        return markerRepo.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(DrawingMarkerDTO::new)
                .toList();
    }

    @Override
    @Transactional
    public DrawingMarkerDTO createMarker(DrawingMarkerRequest request) {
        MarkerValues values = validate(request);

        markerRepo.acquireTransactionLock(MARKERS_LOCK_KEY);
        if (markerRepo.count() >= MAX_MARKERS) {
            throw new ValidationException(LIMIT_MESSAGE);
        }
        if (markerRepo.existsByNameIgnoreCase(values.name())) {
            throw new ConflictException(DUPLICATE_NAME_MESSAGE);
        }

        LocalDateTime now = LocalDateTime.now();
        DrawingMarker marker = saveMarker(DrawingMarker.builder()
                .name(values.name())
                .shape(values.shape())
                .color(values.color())
                .filled(values.filled())
                .displayOrder(markerRepo.findMaxDisplayOrder() + 1)
                .createdAt(now)
                .createdByUserId(SessionUtils.getUserIdFromSession())
                .updatedAt(now)
                .build());

        auditLogService.logCreate(ENTITY_TYPE, marker.getId(), marker.getName(), audit(marker));
        return new DrawingMarkerDTO(marker);
    }

    @Override
    @Transactional
    public DrawingMarkerDTO updateMarker(Long id, DrawingMarkerRequest request) {
        MarkerValues values = validate(request);

        markerRepo.acquireTransactionLock(MARKERS_LOCK_KEY);
        DrawingMarker marker = lockMarker(id);
        if (markerRepo.existsByNameIgnoreCaseAndIdNot(values.name(), id)) {
            throw new ConflictException(DUPLICATE_NAME_MESSAGE);
        }

        if (values.name().equals(marker.getName()) && values.shape() == marker.getShape()
                && values.color().equals(marker.getColor()) && values.filled() == marker.isFilled()) {
            return new DrawingMarkerDTO(marker);
        }

        DrawingMarkerAudit before = audit(marker);
        marker.setName(values.name());
        marker.setShape(values.shape());
        marker.setColor(values.color());
        marker.setFilled(values.filled());
        marker.setUpdatedAt(LocalDateTime.now());
        marker = saveMarker(marker);

        auditLogService.logUpdate(ENTITY_TYPE, id, marker.getName(), before, audit(marker));
        return new DrawingMarkerDTO(marker);
    }

    @Override
    @Transactional
    public void deleteMarker(Long id) {
        markerRepo.acquireTransactionLock(MARKERS_LOCK_KEY);
        DrawingMarker marker = lockMarker(id);
        DrawingMarkerAudit before = audit(marker);

        markerRepo.delete(marker);
        markerRepo.flush();
        auditLogService.logDelete(ENTITY_TYPE, id, marker.getName(), before);
    }

    @Override
    @Transactional
    public List<DrawingMarkerDTO> reorderMarkers(List<Long> ids) {
        markerRepo.acquireTransactionLock(MARKERS_LOCK_KEY);
        List<DrawingMarker> markers = markerRepo.findAllByOrderByDisplayOrderAscIdAsc();
        Map<Long, DrawingMarker> byId = markers.stream()
                .collect(Collectors.toMap(DrawingMarker::getId, Function.identity()));
        if (ids == null || ids.size() != markers.size() || !byId.keySet().equals(new HashSet<>(ids))) {
            throw new ValidationException(STALE_ORDER_MESSAGE);
        }

        LocalDateTime now = LocalDateTime.now();
        List<DrawingMarker> changed = new ArrayList<>();
        List<DrawingMarkerAudit> before = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            DrawingMarker marker = byId.get(ids.get(i));
            int displayOrder = i + 1;
            if (marker.getDisplayOrder() != displayOrder) {
                before.add(audit(marker));
                marker.setDisplayOrder(displayOrder);
                marker.setUpdatedAt(now);
                changed.add(marker);
            }
        }

        if (!changed.isEmpty()) {
            markerRepo.saveAllAndFlush(changed);
            for (int i = 0; i < changed.size(); i++) {
                DrawingMarker marker = changed.get(i);
                DrawingMarkerAudit old = before.get(i);
                auditLogService.logUpdate(ENTITY_TYPE, marker.getId(), marker.getName(), old,
                        old.withDisplayOrder(marker.getDisplayOrder()));
            }
        }
        return getMarkers();
    }

    private DrawingMarker lockMarker(Long id) {
        return markerRepo.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Drawing marker not found with given id: " + id));
    }

    private DrawingMarker saveMarker(DrawingMarker marker) {
        try {
            return markerRepo.saveAndFlush(marker);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(DUPLICATE_NAME_MESSAGE, e);
        }
    }

    private static MarkerValues validate(DrawingMarkerRequest request) {
        if (request == null) {
            throw new ValidationException(NAME_REQUIRED_MESSAGE);
        }
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(NAME_REQUIRED_MESSAGE);
        }
        if (request.getShape() == null) {
            throw new ValidationException(SHAPE_REQUIRED_MESSAGE);
        }
        String color = DrawingColors.normalizeHex(request.getColor());
        if (color == null) {
            throw new ValidationException(INVALID_COLOR_MESSAGE);
        }
        return new MarkerValues(name, request.getShape(), color, Boolean.TRUE.equals(request.getFilled()));
    }

    private static DrawingMarkerAudit audit(DrawingMarker marker) {
        return new DrawingMarkerAudit(marker.getId(), marker.getName(), marker.getShape(), marker.getColor(),
                marker.isFilled(), marker.getDisplayOrder());
    }

    record MarkerValues(String name, DrawingMarkerShape shape, String color, boolean filled) {
    }

    record DrawingMarkerAudit(Long id, String name, DrawingMarkerShape shape, String color, boolean filled,
                              Integer displayOrder) {

        DrawingMarkerAudit withDisplayOrder(Integer value) {
            return new DrawingMarkerAudit(id, name, shape, color, filled, value);
        }
    }
}

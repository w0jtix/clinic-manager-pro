package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.SoftDeletableCategory;
import com.clinic.clinicmanager.repo.SoftDeletableCategoryRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.utils.CategoryRules;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

public abstract class AbstractSoftDeletableCategoryService<E extends SoftDeletableCategory, D> {
    private static final String HIDDEN_NAME_TAKEN =
            "Istnieje ukryta kategoria o tej nazwie. Dodaj ją przez „Nowa kategoria”, żeby ją przywrócić.";
    private static final String COLOR_INDEX_SUFFIX = "category_color_active";

    private final SoftDeletableCategoryRepo<E> categoryRepo;
    private final AuditLogService auditLogService;
    private final String auditEntity;

    protected AbstractSoftDeletableCategoryService(SoftDeletableCategoryRepo<E> categoryRepo,
                                                   AuditLogService auditLogService,
                                                   String auditEntity) {
        this.categoryRepo = categoryRepo;
        this.auditLogService = auditLogService;
        this.auditEntity = auditEntity;
    }

    protected abstract D toDTO(E category);

    protected abstract void markRestored(D category);

    protected abstract E buildCategory(String name, String color);

    protected abstract long countActiveItems(Long categoryId);

    protected abstract long countAllItems(Long categoryId);

    protected abstract String itemsAssignedMessage(long activeItemsCount);

    protected void lockCategoryWrites() {
    }

    protected void ensureDeletable(E category) {
    }

    protected D create(String rawName, String rawColor) {
        try {
            String name = CategoryRules.normalizeName(rawName);
            String color = CategoryRules.normalizeColor(rawColor);
            lockCategoryWrites();

            List<E> sameName = categoryRepo.findAllByCategoryName(name);
            if (sameName.stream().anyMatch(match -> !match.getIsDeleted())) {
                throw new ConflictException(CategoryRules.NAME_TAKEN);
            }
            ensureColorAvailable(color, null);

            if (!sameName.isEmpty()) {
                return restoreHidden(pickHiddenToRestore(sameName, name), name, color);
            }

            E saved = saveCategory(buildCategory(name, color));
            D savedDTO = toDTO(saved);
            auditLogService.logCreate(auditEntity, saved.getId(), saved.getName(), savedDTO);
            return savedDTO;
        } catch (ConflictException | ValidationException | ResourceNotFoundException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new CreationException("Failed to create Category.", e);
        }
    }

    protected D update(Long id, String rawName, String rawColor) {
        try {
            lockCategoryWrites();
            E existing = findActiveCategory(id);
            String oldName = existing.getName();
            D oldSnapshot = toDTO(existing);

            String name = CategoryRules.normalizeName(rawName);
            String color = CategoryRules.normalizeColor(rawColor);
            ensureNameAvailable(name, id);
            ensureColorAvailable(color, id);

            existing.setName(name);
            existing.setColor(color);
            D savedDTO = toDTO(saveCategory(existing));
            auditUpdate(id, oldName, oldSnapshot, savedDTO);
            return savedDTO;
        } catch (ResourceNotFoundException | ConflictException | ValidationException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update Category.", e);
        }
    }

    protected void delete(Long id) {
        try {
            lockCategoryWrites();
            E category = findActiveCategory(id);
            D snapshot = toDTO(category);
            ensureDeletable(category);

            long activeItemsCount = countActiveItems(id);
            if (activeItemsCount > 0) {
                throw new ConflictException(itemsAssignedMessage(activeItemsCount));
            }

            if (countAllItems(id) > 0) {
                category.softDelete();
                categoryRepo.save(category);
            } else {
                categoryRepo.deleteById(id);
            }
            auditLogService.logDelete(auditEntity, id, category.getName(), snapshot);
        } catch (ResourceNotFoundException | ConflictException | ValidationException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new DeletionException("Failed to delete Category.", e);
        }
    }

    protected void auditUpdate(Long id, String oldName, D oldSnapshot, D newSnapshot) {
        auditLogService.logUpdate(auditEntity, id, oldName, oldSnapshot, newSnapshot);
    }

    private D restoreHidden(E hidden, String name, String color) {
        String oldName = hidden.getName();
        D oldSnapshot = toDTO(hidden);
        hidden.setName(name);
        hidden.setColor(color);
        hidden.restore();
        E saved = saveCategory(hidden);
        D savedDTO = toDTO(saved);
        auditUpdate(saved.getId(), oldName, oldSnapshot, savedDTO);
        markRestored(savedDTO);
        return savedDTO;
    }

    private E saveCategory(E category) {
        try {
            return categoryRepo.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            throw CategoryRules.uniqueViolationConflict(e, COLOR_INDEX_SUFFIX);
        }
    }

    private E findActiveCategory(Long id) {
        return categoryRepo.findById(id)
                .filter(category -> !category.getIsDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono kategorii o id: " + id));
    }

    private void ensureNameAvailable(String name, Long currentId) {
        List<E> otherMatches = categoryRepo.findAllByCategoryName(name).stream()
                .filter(match -> !match.getId().equals(currentId))
                .toList();
        if (otherMatches.stream().anyMatch(match -> !match.getIsDeleted())) {
            throw new ConflictException(CategoryRules.NAME_TAKEN);
        }
        if (!otherMatches.isEmpty()) {
            throw new ConflictException(HIDDEN_NAME_TAKEN);
        }
    }

    private void ensureColorAvailable(String color, Long currentId) {
        boolean taken = currentId == null
                ? categoryRepo.existsByColorAndIsDeletedFalse(color)
                : categoryRepo.existsByColorAndIsDeletedFalseAndIdNot(color, currentId);
        if (taken) {
            throw new ConflictException(CategoryRules.COLOR_TAKEN);
        }
    }

    private E pickHiddenToRestore(List<E> hiddenMatches, String name) {
        return hiddenMatches.stream()
                .filter(match -> match.getName().equals(name))
                .findFirst()
                .orElse(hiddenMatches.getFirst());
    }
}

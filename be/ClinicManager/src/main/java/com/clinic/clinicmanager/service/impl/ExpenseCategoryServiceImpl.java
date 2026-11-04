package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.CategoryUsageDTO;
import com.clinic.clinicmanager.DTO.ExpenseCategoryDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.ExpenseCategory;
import com.clinic.clinicmanager.repo.ExpenseCategoryRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.ExpenseCategoryService;
import com.clinic.clinicmanager.utils.CategoryRules;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseCategoryServiceImpl implements ExpenseCategoryService {
    private static final String AUDIT_ENTITY = "ExpenseCategory";
    private static final String COLOR_INDEX = "uq_expense_category_color";
    private static final String SYSTEM_CATEGORY = "Kategorii systemowej nie można usunąć.";
    private static final String INTERNAL_CATEGORY = "Tej kategorii nie można edytować.";
    private static final String CATEGORY_IN_USE = "Kategoria jest używana w wydatkach. Nie można jej usunąć.";
    private static final String UNKNOWN_CATEGORY = "Nieznana kategoria wydatku.";
    private static final String CUSTOM_CODE_PREFIX = "C";

    private final ExpenseCategoryRepo expenseCategoryRepo;
    private final AuditLogService auditLogService;

    @Override
    public List<ExpenseCategoryDTO> getCategories() {
        return expenseCategoryRepo.findAllByOrderByIdAsc()
                .stream()
                .map(ExpenseCategoryDTO::new)
                .toList();
    }

    @Override
    public List<CategoryUsageDTO> getCategoryUsage() {
        return expenseCategoryRepo.findCategoryUsage();
    }

    @Override
    @Transactional
    public ExpenseCategoryDTO createCategory(ExpenseCategoryDTO category) {
        try {
            String name = CategoryRules.normalizeName(category.getName());
            String color = CategoryRules.normalizeColor(category.getColor());
            ensureNameAvailable(name, null);
            ensureColorAvailable(color, null);

            ExpenseCategory newCategory = ExpenseCategory.builder()
                    .code(generateCode())
                    .name(name)
                    .color(color)
                    .isSystem(false)
                    .isInternal(false)
                    .build();
            ExpenseCategoryDTO savedDTO = new ExpenseCategoryDTO(saveCategory(newCategory));
            auditLogService.logCreate(AUDIT_ENTITY, savedDTO.getId(), savedDTO.getName(), savedDTO);
            return savedDTO;
        } catch (ConflictException | ValidationException | ResourceNotFoundException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new CreationException("Failed to create expense category.", e);
        }
    }

    @Override
    @Transactional
    public ExpenseCategoryDTO updateCategory(Long id, ExpenseCategoryDTO category) {
        try {
            ExpenseCategory existing = findCategory(id);
            if (Boolean.TRUE.equals(existing.getIsInternal())) {
                throw new ConflictException(INTERNAL_CATEGORY);
            }
            ExpenseCategoryDTO oldCategorySnapshot = new ExpenseCategoryDTO(existing);

            String name = CategoryRules.normalizeName(category.getName());
            String color = CategoryRules.normalizeColor(category.getColor());
            ensureNameAvailable(name, id);
            ensureColorAvailable(color, id);

            existing.setName(name);
            existing.setColor(color);
            ExpenseCategoryDTO savedDTO = new ExpenseCategoryDTO(saveCategory(existing));
            auditLogService.logUpdate(AUDIT_ENTITY, id, oldCategorySnapshot.getName(), oldCategorySnapshot, savedDTO);
            return savedDTO;
        } catch (ResourceNotFoundException | ConflictException | ValidationException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update expense category.", e);
        }
    }

    @Override
    @Transactional
    public void deleteCategoryById(Long id) {
        try {
            ExpenseCategory category = findCategory(id);
            if (Boolean.TRUE.equals(category.getIsSystem()) || Boolean.TRUE.equals(category.getIsInternal())) {
                throw new ConflictException(SYSTEM_CATEGORY);
            }
            if (expenseCategoryRepo.countUsageByCode(category.getCode()) > 0) {
                throw new ConflictException(CATEGORY_IN_USE);
            }
            ExpenseCategoryDTO categorySnapshot = new ExpenseCategoryDTO(category);

            try {
                expenseCategoryRepo.delete(category);
                expenseCategoryRepo.flush();
            } catch (DataIntegrityViolationException e) {
                throw new ConflictException(CATEGORY_IN_USE, e);
            }
            auditLogService.logDelete(AUDIT_ENTITY, id, categorySnapshot.getName(), categorySnapshot);
        } catch (ResourceNotFoundException | ConflictException | ValidationException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new DeletionException("Failed to delete expense category.", e);
        }
    }

    @Override
    public void requireExistingCode(String code) {
        if (code == null || !expenseCategoryRepo.existsByCode(code)) {
            throw new ValidationException(UNKNOWN_CATEGORY);
        }
    }

    private ExpenseCategory saveCategory(ExpenseCategory category) {
        try {
            return expenseCategoryRepo.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            throw CategoryRules.uniqueViolationConflict(e, COLOR_INDEX);
        }
    }

    private ExpenseCategory findCategory(Long id) {
        return expenseCategoryRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Nie znaleziono kategorii o id: " + id));
    }

    private String generateCode() {
        return CUSTOM_CODE_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    private void ensureNameAvailable(String name, Long currentId) {
        boolean taken = expenseCategoryRepo.findAllByCategoryName(name).stream()
                .anyMatch(match -> !match.getId().equals(currentId));
        if (taken) {
            throw new ConflictException(CategoryRules.NAME_TAKEN);
        }
    }

    private void ensureColorAvailable(String color, Long currentId) {
        boolean taken = currentId == null
                ? expenseCategoryRepo.existsByColor(color)
                : expenseCategoryRepo.existsByColorAndIdNot(color, currentId);
        if (taken) {
            throw new ConflictException(CategoryRules.COLOR_TAKEN);
        }
    }
}

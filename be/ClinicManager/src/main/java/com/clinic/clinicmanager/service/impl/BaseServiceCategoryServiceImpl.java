package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.BaseServiceCategoryDTO;
import com.clinic.clinicmanager.DTO.CategoryUsageDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.BaseServiceCategory;
import com.clinic.clinicmanager.repo.BaseServiceCategoryRepo;
import com.clinic.clinicmanager.repo.BaseServiceRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.BaseServiceCategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BaseServiceCategoryServiceImpl extends AbstractSoftDeletableCategoryService<BaseServiceCategory, BaseServiceCategoryDTO>
        implements BaseServiceCategoryService {
    private static final String AUDIT_ENTITY = "BaseServiceCategory";

    private final BaseServiceCategoryRepo serviceCategoryRepo;
    private final BaseServiceRepo baseServiceRepo;

    public BaseServiceCategoryServiceImpl(BaseServiceCategoryRepo serviceCategoryRepo, BaseServiceRepo baseServiceRepo,
                                          AuditLogService auditLogService) {
        super(serviceCategoryRepo, auditLogService, AUDIT_ENTITY);
        this.serviceCategoryRepo = serviceCategoryRepo;
        this.baseServiceRepo = baseServiceRepo;
    }

    @Override
    public BaseServiceCategoryDTO getCategoryById(Long id) {
        return new BaseServiceCategoryDTO(serviceCategoryRepo.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with given id: " + id)));
    }

    @Override
    public List<BaseServiceCategoryDTO> getCategories() {
        return serviceCategoryRepo.findAll()
                .stream()
                .filter(category -> !category.getIsDeleted())
                .map(BaseServiceCategoryDTO::new)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryUsageDTO> getCategoryUsage() {
        return serviceCategoryRepo.findActiveCategoryUsage();
    }

    @Override
    @Transactional
    public BaseServiceCategoryDTO createCategory(BaseServiceCategoryDTO category) {
        return create(category.getName(), category.getColor());
    }

    @Override
    @Transactional
    public BaseServiceCategoryDTO updateCategory(Long id, BaseServiceCategoryDTO category) {
        return update(id, category.getName(), category.getColor());
    }

    @Override
    @Transactional
    public void deleteCategoryById(Long id) {
        delete(id);
    }

    @Override
    protected BaseServiceCategoryDTO toDTO(BaseServiceCategory category) {
        return new BaseServiceCategoryDTO(category);
    }

    @Override
    protected void markRestored(BaseServiceCategoryDTO category) {
        category.setRestored(true);
    }

    @Override
    protected BaseServiceCategory buildCategory(String name, String color) {
        return BaseServiceCategory.builder()
                .name(name)
                .color(color)
                .isDeleted(false)
                .build();
    }

    @Override
    protected long countActiveItems(Long categoryId) {
        return baseServiceRepo.countByCategoryIdAndIsDeletedFalse(categoryId);
    }

    @Override
    protected long countAllItems(Long categoryId) {
        return baseServiceRepo.countByCategoryId(categoryId);
    }

    @Override
    protected String itemsAssignedMessage(long activeItemsCount) {
        return "Kategoria ma przypisane usługi (" + activeItemsCount + "). Najpierw przenieś je do innej kategorii.";
    }
}

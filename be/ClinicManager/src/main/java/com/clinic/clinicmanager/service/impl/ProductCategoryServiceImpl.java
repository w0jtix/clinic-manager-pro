package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.CategoryUsageDTO;
import com.clinic.clinicmanager.DTO.ProductCategoryDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.repo.ProductCategoryRepo;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.ProductCategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductCategoryServiceImpl extends AbstractSoftDeletableCategoryService<ProductCategory, ProductCategoryDTO>
        implements ProductCategoryService {
    private static final String AUDIT_ENTITY = "ProductCategory";
    private static final String SYSTEM_CATEGORY = "Kategorii systemowej nie można usunąć.";
    private static final long WRITE_LOCK_KEY = 0x50435052444354L;
    private static final List<String> FALLBACK_COLORS = List.of(
            "239,68,68", "249,115,22", "234,179,8", "34,197,94", "20,184,166",
            "59,130,246", "99,102,241", "168,85,247", "236,72,153", "120,113,108");
    private static final int GENERATED_COLOR_START = 0x808080;
    private static final int GENERATED_COLOR_STEP = 0x2F4B1D;

    private final ProductCategoryRepo productCategoryRepo;
    private final ProductRepo productRepo;

    public ProductCategoryServiceImpl(ProductCategoryRepo productCategoryRepo, ProductRepo productRepo,
                                      AuditLogService auditLogService) {
        super(productCategoryRepo, auditLogService, AUDIT_ENTITY);
        this.productCategoryRepo = productCategoryRepo;
        this.productRepo = productRepo;
    }

    @Override
    public ProductCategoryDTO getCategoryById(Long id){
        return new ProductCategoryDTO(productCategoryRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with given id: " + id)));
    }

    @Override
    public List<ProductCategoryDTO> getCategories() {
        return productCategoryRepo.findAllByIsDeletedFalse()
                .stream()
                .map(ProductCategoryDTO::new)
                .collect(Collectors.toList());
    }

    @Override
    public List<CategoryUsageDTO> getCategoryUsage() {
        return productCategoryRepo.findActiveCategoryUsage();
    }

    @Override
    @Transactional
    public ProductCategoryDTO createCategory(ProductCategoryDTO category) {
        return create(category.getName(), category.getColor());
    }

    @Override
    @Transactional
    public ProductCategoryDTO updateCategory(Long id, ProductCategoryDTO category) {
        return update(id, category.getName(), category.getColor());
    }

    @Override
    @Transactional
    public void deleteCategoryById(Long id) {
        delete(id);
    }

    @Override
    @Transactional
    public void restoreIfHidden(ProductCategory category) {
        if (category == null || !Boolean.TRUE.equals(category.getIsDeleted())) {
            return;
        }
        lockCategoryWrites();
        productCategoryRepo.findByIdAndIsDeletedTrue(category.getId()).ifPresent(this::restoreWithFreeColor);
    }

    @Override
    protected ProductCategoryDTO toDTO(ProductCategory category) {
        return new ProductCategoryDTO(category);
    }

    @Override
    protected void markRestored(ProductCategoryDTO category) {
        category.setRestored(true);
    }

    @Override
    protected ProductCategory buildCategory(String name, String color) {
        return ProductCategory.builder()
                .name(name)
                .color(color)
                .isSystem(false)
                .isDeleted(false)
                .build();
    }

    @Override
    protected long countActiveItems(Long categoryId) {
        return productRepo.countByCategoryIdAndIsDeletedFalse(categoryId);
    }

    @Override
    protected long countAllItems(Long categoryId) {
        return productRepo.countByCategoryId(categoryId);
    }

    @Override
    protected String itemsAssignedMessage(long activeItemsCount) {
        return "Kategoria ma przypisane produkty (" + activeItemsCount + "). Najpierw przenieś je do innej kategorii.";
    }

    @Override
    protected void lockCategoryWrites() {
        productCategoryRepo.acquireTransactionLock(WRITE_LOCK_KEY);
    }

    @Override
    protected void ensureDeletable(ProductCategory category) {
        if (Boolean.TRUE.equals(category.getIsSystem())) {
            throw new ConflictException(SYSTEM_CATEGORY);
        }
    }

    private void restoreWithFreeColor(ProductCategory hidden) {
        ProductCategoryDTO oldSnapshot = new ProductCategoryDTO(hidden);
        if (productCategoryRepo.existsByColorAndIsDeletedFalse(hidden.getColor())) {
            hidden.setColor(pickFreeColor());
        }
        hidden.restore();
        ProductCategoryDTO savedDTO = new ProductCategoryDTO(productCategoryRepo.saveAndFlush(hidden));
        auditUpdate(savedDTO.getId(), oldSnapshot.getName(), oldSnapshot, savedDTO);
    }

    private String pickFreeColor() {
        Set<String> taken = new HashSet<>(productCategoryRepo.findActiveColors());
        for (String color : FALLBACK_COLORS) {
            if (!taken.contains(color)) {
                return color;
            }
        }
        int value = GENERATED_COLOR_START;
        while (true) {
            String candidate = ((value >> 16) & 0xFF) + "," + ((value >> 8) & 0xFF) + "," + (value & 0xFF);
            if (!taken.contains(candidate)) {
                return candidate;
            }
            value = (value + GENERATED_COLOR_STEP) & 0xFFFFFF;
        }
    }
}

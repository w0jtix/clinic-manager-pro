package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.BrandDTO;
import com.clinic.clinicmanager.DTO.ProductCategoryDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.repo.ProductCategoryRepo;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.ProductCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductCategoryServiceImpl implements ProductCategoryService {
    private final ProductCategoryRepo productCategoryRepo;
    private final ProductRepo productRepo;
    private final AuditLogService auditLogService;

    @Override
    public ProductCategoryDTO getCategoryById(Long id){
        return new ProductCategoryDTO(productCategoryRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with given id: " + id)));
    }

    @Override
    public List<ProductCategoryDTO> getCategories() {
        return productCategoryRepo.findAll()
                .stream()
                .map(ProductCategoryDTO::new)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProductCategoryDTO createCategory(ProductCategoryDTO category) {
        try{
            if(categoryAlreadyExists(category)) {
                throw new ConflictException("Category already exists: " + category.getName());
            }
            ProductCategoryDTO savedDTO = new ProductCategoryDTO(productCategoryRepo.save(category.toEntity()));
            auditLogService.logCreate("ProductCategory", savedDTO.getId(), savedDTO.getName(), savedDTO);
            return savedDTO;
        } catch (ConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new CreationException("Failed to create Category. Reason: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public ProductCategoryDTO updateCategory(Long id, ProductCategoryDTO category) {
        try{
            ProductCategoryDTO oldCategorySnapshot = getCategoryById(id);

            checkForDuplicatesExcludingCurrent(category, id);
            category.setId(id);
            ProductCategoryDTO savedDTO = new ProductCategoryDTO(productCategoryRepo.save(category.toEntity()));
            auditLogService.logUpdate("ProductCategory", id, oldCategorySnapshot.getName(), oldCategorySnapshot, savedDTO);
            return savedDTO;
        } catch (ResourceNotFoundException | ConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update Category, Reason: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void deleteCategoryById(Long id) {
        try{
            ProductCategoryDTO categorySnapshot = getCategoryById(id);
            if (productRepo.existsByCategoryId(id)) {
                throw new ConflictException("Cannot delete category: products are assigned to it.");
            }
            productCategoryRepo.deleteById(id);
            auditLogService.logDelete("ProductCategory", id, categorySnapshot.getName(), categorySnapshot);
        } catch (ResourceNotFoundException | ConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new DeletionException("Failed to delete Category, Reason: " + e.getMessage(), e);
        }
    }

    private boolean categoryAlreadyExists(ProductCategoryDTO categoryDTO) {
        return productCategoryRepo.findByCategoryName(categoryDTO.getName()).isPresent();
    }

    private void checkForDuplicatesExcludingCurrent(ProductCategoryDTO productCategoryDTO, Long currentId) {
        Optional<ProductCategory> duplicate = productCategoryRepo.findByCategoryName(
                productCategoryDTO.getName()
        );

        if (duplicate.isPresent() && !duplicate.get().getId().equals(currentId)) {
            throw new ConflictException("Category with provided details already exists.");
        }
    }
}

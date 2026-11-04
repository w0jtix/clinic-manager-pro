package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.ProductDTO;
import com.clinic.clinicmanager.DTO.request.ProductFilterDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import com.clinic.clinicmanager.repo.ProductCategoryRepo;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.ProductReferenceService;
import com.clinic.clinicmanager.service.ProductService;
import com.clinic.clinicmanager.service.SupplyHistoryService;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {
    private final ProductRepo productRepo;
    private final ProductCategoryRepo productCategoryRepo;
    private final ProductReferenceService productReferenceService;
    private final AuditLogService auditLogService;
    private final SupplyHistoryService supplyHistoryService;

    @Override
    public ProductDTO getProductById(Long id) {
        return new ProductDTO(productRepo.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with given id: " + id)));
    }

    @Override
    public List<ProductDTO> getProducts(ProductFilterDTO filter) {
        if(isNull(filter)) {
            filter = new ProductFilterDTO();
        }
        return  productRepo.findAllWithFilters(
                        filter.getProductIds(),
                        filter.getCategoryIds(),
                        filter.getBrandIds(),
                        filter.getKeyword(),
                        filter.getIncludeZero(),
                        filter.getIsDeleted(),
                        stockStatusOf(filter))
                .stream()
                .map(ProductDTO::new)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ProductDTO> getProducts(ProductFilterDTO filter, int page, int size) {
        if(isNull(filter)) {
            filter = new ProductFilterDTO();
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("brand.name")));

        Page<Product> products = productRepo.findAllWithFilters(
                filter.getProductIds(),
                filter.getCategoryIds(),
                filter.getBrandIds(),
                filter.getKeyword(),
                filter.getIncludeZero(),
                filter.getIsDeleted(),
                stockStatusOf(filter),
                pageable);

        return products.map(ProductDTO::new);
    }

    @Override
    @Transactional
    public ProductDTO createProduct(ProductDTO product) {
        boolean isCompany = SessionUtils.hasRole(AppRoleHierarchy.COMPANY);
        if (isCompany) {
            validateLowStockThreshold(product.getLowStockThreshold());
        }

        try{
            if (!isCompany) {
                product.setFallbackNetPurchasePrice(null);
                product.setFallbackVatRate(null);
                product.setLowStockThreshold(null);
            }

            Optional<Product> existingProduct  = productRepo
                    .findByName(
                            product.getName()
                    );

            if (existingProduct.isPresent()) {
                Product existing = existingProduct.get();

                if (existing.getIsDeleted()) {
                    ensureCategoryNotHidden(existing.getCategory());
                    ProductDTO restoredDTO = restoreProduct(existing, product);
                    auditLogService.logCreate("Product", restoredDTO.getId(), restoredDTO.getName(), restoredDTO);
                    return restoredDTO;
                } else {
                    throw new ConflictException("Product already exists: " + product.getName());
                }
            }
            ensureCategoryAssignable(product, null, false);
            Product savedProduct = productRepo.save(product.toEntity());
            supplyHistoryService.record(savedProduct, supplyOf(savedProduct), SupplyChangeSource.PRODUCT_CREATED,
                    savedProduct.getId(), "Nowy produkt");

            ProductDTO savedDTO = new ProductDTO(savedProduct);
            auditLogService.logCreate("Product", savedDTO.getId(), savedDTO.getName(), savedDTO);
            return savedDTO;
        } catch (ConflictException | ResourceNotFoundException | ValidationException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new CreationException("Failed to create Product.", e);
        }
    }

    @Override
    @Transactional
    public List<ProductDTO> createProducts(List<ProductDTO> products) {
        return products.stream()
                .map(this::createProduct)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProductDTO updateProduct(Long id, ProductDTO product) {
        boolean isCompany = SessionUtils.hasRole(AppRoleHierarchy.COMPANY);
        if (isCompany) {
            validateLowStockThreshold(product.getLowStockThreshold());
        }

        try {
            ProductDTO oldProductSnapshot = getProductById(id);

            if (!isCompany || isNull(product.getLowStockThreshold())) {
                product.setLowStockThreshold(oldProductSnapshot.getLowStockThreshold());
            }

            if (!isCompany) {
                product.setFallbackNetPurchasePrice(oldProductSnapshot.getFallbackNetPurchasePrice());
                product.setFallbackVatRate(oldProductSnapshot.getFallbackVatRate());
            }

            checkForDuplicatesExcludingCurrent(product, id);
            ensureCategoryAssignable(product, oldProductSnapshot.getCategory().getId(),
                    Boolean.TRUE.equals(oldProductSnapshot.getIsDeleted()) && Boolean.TRUE.equals(product.getIsDeleted()));
            product.setId(id);
            Product savedProduct = productRepo.save(product.toEntity());

            int supplyBefore = oldProductSnapshot.getSupply() != null ? oldProductSnapshot.getSupply() : 0;
            supplyHistoryService.record(savedProduct, supplyOf(savedProduct) - supplyBefore,
                    SupplyChangeSource.PRODUCT_FORM, savedProduct.getId(), "Edycja produktu");

            ProductDTO savedDTO = new ProductDTO(savedProduct);
            auditLogService.logUpdate("Product", id, oldProductSnapshot.getName(), oldProductSnapshot, savedDTO);
            return savedDTO;
        } catch (ResourceNotFoundException | ConflictException | ValidationException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update Product.", e);
        }
    }

    @Override
    @Transactional
    public void deleteProductById(Long id) {
        try {
            Product product = productRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

            if (product.getIsDeleted()) {
                throw new DeletionException("Product is already softDeleted.");
            }

            ProductDTO productSnapshot = new ProductDTO(product);
            if (productReferenceService.hasAnyReferences(id)) {
                int supplyBefore = supplyOf(product);
                product.softDelete();
                productRepo.save(product);
                supplyHistoryService.record(product, -supplyBefore, SupplyChangeSource.PRODUCT_DELETED,
                        product.getId(), "Usunięcie produktu");
            } else {
                productRepo.deleteById(id);
            }
            auditLogService.logDelete("Product", id, productSnapshot.getName(), productSnapshot);
        } catch (ResourceNotFoundException | DeletionException | ConflictException | ValidationException
                 | AccessDeniedException | CreationException | UpdateException e) {
            throw e;
        } catch (Exception e) {
            throw new DeletionException("Failed to delete Product.", e);
        }
    }

    private void checkForDuplicatesExcludingCurrent(ProductDTO productDTO, Long currentId) {
        Optional<Product> duplicate = productRepo.findByName(
                productDTO.getName()
        );

        if (duplicate.isPresent() && !duplicate.get().getId().equals(currentId)) {
            if (duplicate.get().getIsDeleted()) {
                throw new ConflictException("Product with provided details already exists and is SoftDeleted.");
            } else {
                throw new ConflictException("Product with provided details already exists.");
            }
        }
    }

    private void ensureCategoryAssignable(ProductDTO product, Long currentCategoryId, boolean archived) {
        if (product.getCategory() == null || product.getCategory().getId() == null) {
            return;
        }
        Long targetCategoryId = product.getCategory().getId();
        if (archived && targetCategoryId.equals(currentCategoryId)) {
            return;
        }
        productCategoryRepo.findByIdAndIsDeletedTrue(targetCategoryId)
                .ifPresent(this::ensureCategoryNotHidden);
    }

    private void ensureCategoryNotHidden(ProductCategory category) {
        if (category != null && Boolean.TRUE.equals(category.getIsDeleted())) {
            throw new ConflictException("Kategoria „" + category.getName()
                    + "” jest ukryta. Najpierw przywróć ją.");
        }
    }

    private ProductDTO restoreProduct (Product deletedProduct, ProductDTO newProductData) {
        int supplyBefore = supplyOf(deletedProduct);

        deletedProduct.restore(newProductData.getSupply());
        deletedProduct.setDescription(newProductData.getDescription());
        Product savedProduct = productRepo.save(deletedProduct);

        supplyHistoryService.record(savedProduct, supplyOf(savedProduct) - supplyBefore,
                SupplyChangeSource.PRODUCT_RESTORED, savedProduct.getId(), "Przywrócenie produktu");

        return new ProductDTO(savedProduct);
    }

    private String stockStatusOf(ProductFilterDTO filter) {
        return filter.getStockStatus() != null ? filter.getStockStatus().name() : null;
    }

    private void validateLowStockThreshold(Integer lowStockThreshold) {
        if (lowStockThreshold != null && lowStockThreshold < 0) {
            throw new CreationException("Próg niskiego stanu magazynowego nie może być ujemny.");
        }
    }

    private int supplyOf(Product product) {
        return product.getSupply() != null ? product.getSupply() : 0;
    }

}

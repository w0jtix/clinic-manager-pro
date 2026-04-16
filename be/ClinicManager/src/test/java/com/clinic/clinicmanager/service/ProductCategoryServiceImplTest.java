package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ProductCategoryDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.repo.ProductCategoryRepo;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.service.impl.ProductCategoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceImplTest {

    @Mock ProductCategoryRepo productCategoryRepo;
    @Mock ProductRepo productRepo;
    @Mock AuditLogService auditLogService;

    @InjectMocks
    ProductCategoryServiceImpl productCategoryService;

    @Test
    void getCategoryById_shouldReturnDTO_whenCategoryExists() {
        ProductCategory category = ProductCategory.builder().id(1L).name("Produkty").color("#FF0000").build();
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(category));

        ProductCategoryDTO result = productCategoryService.getCategoryById(1L);

        assertEquals(1L, result.getId());
        assertEquals("Produkty", result.getName());
    }

    @Test
    void getCategoryById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(productCategoryRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productCategoryService.getCategoryById(99L));
    }

    @Test
    void getCategories_shouldReturnMappedList() {
        ProductCategory c1 = ProductCategory.builder().id(1L).name("Produkty").color("#FF0000").build();
        ProductCategory c2 = ProductCategory.builder().id(2L).name("Narzędzia").color("#00FF00").build();
        when(productCategoryRepo.findAll()).thenReturn(List.of(c1, c2));

        List<ProductCategoryDTO> result = productCategoryService.getCategories();

        assertEquals(2, result.size());
    }

    @Test
    void createCategory_shouldSaveAndReturnDTO_whenCategoryIsNew() {
        ProductCategoryDTO inputDTO = new ProductCategoryDTO(null, "Nowa", "#000000");
        ProductCategory saved = ProductCategory.builder().id(3L).name("Nowa").color("#000000").build();

        when(productCategoryRepo.findByCategoryName("Nowa")).thenReturn(Optional.empty());
        when(productCategoryRepo.save(any(ProductCategory.class))).thenReturn(saved);

        ProductCategoryDTO result = productCategoryService.createCategory(inputDTO);

        assertEquals(3L, result.getId());
        assertEquals("Nowa", result.getName());
        verify(auditLogService).logCreate(eq("ProductCategory"), eq(3L), eq("Nowa"), any());
    }

    @Test
    void createCategory_shouldThrowConflictException_whenCategoryAlreadyExists() {
        ProductCategoryDTO inputDTO = new ProductCategoryDTO(null, "Produkty", "#FF0000");
        ProductCategory existing = ProductCategory.builder().id(1L).name("Produkty").color("#FF0000").build();

        when(productCategoryRepo.findByCategoryName("Produkty")).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class, () -> productCategoryService.createCategory(inputDTO));
        verify(productCategoryRepo, never()).save(any());
    }

    @Test
    void updateCategory_shouldUpdateAndReturnDTO_whenNoConflict() {
        ProductCategory existing = ProductCategory.builder().id(1L).name("Stara").color("#FF0000").build();
        ProductCategory updated = ProductCategory.builder().id(1L).name("Nowa").color("#00FF00").build();
        ProductCategoryDTO updateDTO = new ProductCategoryDTO(null, "Nowa", "#00FF00");

        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(productCategoryRepo.findByCategoryName("Nowa")).thenReturn(Optional.empty());
        when(productCategoryRepo.save(any(ProductCategory.class))).thenReturn(updated);

        ProductCategoryDTO result = productCategoryService.updateCategory(1L, updateDTO);

        assertEquals(1L, result.getId());
        assertEquals("Nowa", result.getName());
        verify(auditLogService).logUpdate(eq("ProductCategory"), eq(1L), eq("Stara"), any(), any());
    }

    @Test
    void updateCategory_shouldThrowResourceNotFoundException_whenNotFound() {
        when(productCategoryRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productCategoryService.updateCategory(99L, new ProductCategoryDTO(null, "X", "#000000")));
    }

    @Test
    void updateCategory_shouldThrowConflictException_whenNameTakenByOtherCategory() {
        ProductCategory current = ProductCategory.builder().id(1L).name("Produkty").color("#FF0000").build();
        ProductCategory other = ProductCategory.builder().id(2L).name("Narzędzia").color("#00FF00").build();

        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(current));
        when(productCategoryRepo.findByCategoryName("Narzędzia")).thenReturn(Optional.of(other));

        assertThrows(ConflictException.class,
                () -> productCategoryService.updateCategory(1L, new ProductCategoryDTO(null, "Narzędzia", "#00FF00")));
    }

    @Test
    void updateCategory_shouldNotThrowConflict_whenNameBelongsToCurrentCategory() {
        ProductCategory current = ProductCategory.builder().id(1L).name("Produkty").color("#FF0000").build();
        ProductCategory updated = ProductCategory.builder().id(1L).name("Produkty").color("#0000FF").build();
        ProductCategoryDTO updateDTO = new ProductCategoryDTO(null, "Produkty", "#0000FF");

        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(current));
        when(productCategoryRepo.findByCategoryName("Produkty")).thenReturn(Optional.of(current));
        when(productCategoryRepo.save(any(ProductCategory.class))).thenReturn(updated);

        assertDoesNotThrow(() -> productCategoryService.updateCategory(1L, updateDTO));
    }

    @Test
    void deleteCategoryById_shouldDelete_whenCategoryExistsAndNoProducts() {
        ProductCategory category = ProductCategory.builder().id(1L).name("Produkty").color("#FF0000").build();
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(category));
        when(productRepo.existsByCategoryId(1L)).thenReturn(false);

        productCategoryService.deleteCategoryById(1L);

        verify(productCategoryRepo).deleteById(1L);
        verify(auditLogService).logDelete(eq("ProductCategory"), eq(1L), eq("Produkty"), any());
    }

    @Test
    void deleteCategoryById_shouldThrowConflictException_whenProductsAssigned() {
        ProductCategory category = ProductCategory.builder().id(1L).name("Produkty").color("#FF0000").build();
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(category));
        when(productRepo.existsByCategoryId(1L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> productCategoryService.deleteCategoryById(1L));
        verify(productCategoryRepo, never()).deleteById(any());
    }

    @Test
    void deleteCategoryById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(productCategoryRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productCategoryService.deleteCategoryById(99L));
        verify(productCategoryRepo, never()).deleteById(any());
    }
}

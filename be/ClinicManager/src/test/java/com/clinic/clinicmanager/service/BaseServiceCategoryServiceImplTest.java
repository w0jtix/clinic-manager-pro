package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.BaseServiceCategoryDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.BaseServiceCategory;
import com.clinic.clinicmanager.repo.BaseServiceCategoryRepo;
import com.clinic.clinicmanager.repo.BaseServiceRepo;
import com.clinic.clinicmanager.service.impl.BaseServiceCategoryServiceImpl;
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
class BaseServiceCategoryServiceImplTest {

    @Mock BaseServiceCategoryRepo serviceCategoryRepo;
    @Mock BaseServiceRepo baseServiceRepo;
    @Mock AuditLogService auditLogService;

    @InjectMocks
    BaseServiceCategoryServiceImpl categoryService;

    private BaseServiceCategory activeCategory(Long id, String name) {
        return BaseServiceCategory.builder().id(id).name(name).color("#fff").isDeleted(false).build();
    }

    @Test
    void getCategoryById_shouldReturnDTO_whenExists() {
        when(serviceCategoryRepo.findOneById(1L)).thenReturn(Optional.of(activeCategory(1L, "Klasyczna")));

        BaseServiceCategoryDTO result = categoryService.getCategoryById(1L);

        assertEquals(1L, result.getId());
        assertEquals("Klasyczna", result.getName());
    }

    @Test
    void getCategoryById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(serviceCategoryRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.getCategoryById(99L));
    }

    @Test
    void getCategories_shouldReturnOnlyActiveCategories() {
        BaseServiceCategory active = activeCategory(1L, "Klasyczna");
        BaseServiceCategory deleted = BaseServiceCategory.builder().id(2L).name("Stara").color("#000").isDeleted(true).build();

        when(serviceCategoryRepo.findAll()).thenReturn(List.of(active, deleted));

        List<BaseServiceCategoryDTO> result = categoryService.getCategories();

        assertEquals(1, result.size());
        assertEquals("Klasyczna", result.getFirst().getName());
    }

    @Test
    void createCategory_shouldSaveAndReturnDTO_whenNameIsUnique() {
        BaseServiceCategoryDTO input = new BaseServiceCategoryDTO(null, "Klasyczna", "#fff", false);
        BaseServiceCategory saved = activeCategory(1L, "Klasyczna");

        when(serviceCategoryRepo.existsByName("Klasyczna")).thenReturn(false);
        when(serviceCategoryRepo.save(any())).thenReturn(saved);

        BaseServiceCategoryDTO result = categoryService.createCategory(input);

        assertEquals(1L, result.getId());
        verify(auditLogService).logCreate(eq("BaseServiceCategory"), eq(1L), anyString(), any());
    }

    @Test
    void createCategory_shouldThrowConflictException_whenNameAlreadyExists() {
        when(serviceCategoryRepo.existsByName("Klasyczna")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> categoryService.createCategory(new BaseServiceCategoryDTO(null, "Klasyczna", "#fff", false)));

        verify(serviceCategoryRepo, never()).save(any());
    }

    @Test
    void updateCategory_shouldSaveAndReturnDTO_whenNoConflict() {
        BaseServiceCategory existing = activeCategory(1L, "Stara");
        BaseServiceCategory updated = activeCategory(1L, "Nowa");
        BaseServiceCategoryDTO input = new BaseServiceCategoryDTO(null, "Nowa", "#fff", false);

        when(serviceCategoryRepo.findOneById(1L)).thenReturn(Optional.of(existing));
        when(serviceCategoryRepo.findByName("Nowa")).thenReturn(Optional.empty());
        when(serviceCategoryRepo.save(any())).thenReturn(updated);

        BaseServiceCategoryDTO result = categoryService.updateCategory(1L, input);

        assertEquals("Nowa", result.getName());
    }

    @Test
    void updateCategory_shouldThrowResourceNotFoundException_whenNotFound() {
        when(serviceCategoryRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.updateCategory(99L, new BaseServiceCategoryDTO()));
    }

    @Test
    void updateCategory_shouldThrowConflictException_whenNameTakenByAnotherCategory() {
        BaseServiceCategory existing = activeCategory(1L, "Stara");
        BaseServiceCategory other = activeCategory(2L, "Klasyczna");

        when(serviceCategoryRepo.findOneById(1L)).thenReturn(Optional.of(existing));
        when(serviceCategoryRepo.findByName("Klasyczna")).thenReturn(Optional.of(other));

        assertThrows(ConflictException.class,
                () -> categoryService.updateCategory(1L, new BaseServiceCategoryDTO(null, "Klasyczna", "#fff", false)));
    }

    @Test
    void updateCategory_shouldNotThrowConflict_whenNameBelongsToSameCategory() {
        BaseServiceCategory existing = activeCategory(1L, "Klasyczna");
        BaseServiceCategory saved = activeCategory(1L, "Klasyczna");
        BaseServiceCategoryDTO input = new BaseServiceCategoryDTO(null, "Klasyczna", "#00f", false);

        when(serviceCategoryRepo.findOneById(1L)).thenReturn(Optional.of(existing));
        when(serviceCategoryRepo.findByName("Klasyczna")).thenReturn(Optional.of(existing));
        when(serviceCategoryRepo.save(any())).thenReturn(saved);

        assertDoesNotThrow(() -> categoryService.updateCategory(1L, input));
    }

    @Test
    void deleteCategoryById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(serviceCategoryRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.deleteCategoryById(99L));
    }

    @Test
    void deleteCategoryById_shouldThrowDeletionException_whenAlreadySoftDeleted() {
        BaseServiceCategory deleted = BaseServiceCategory.builder().id(1L).name("Stara").color("#fff").isDeleted(true).build();
        when(serviceCategoryRepo.findOneById(1L)).thenReturn(Optional.of(deleted));

        assertThrows(DeletionException.class, () -> categoryService.deleteCategoryById(1L));
    }

    @Test
    void deleteCategoryById_shouldThrowDeletionException_whenHasActiveServices() {
        when(serviceCategoryRepo.findOneById(1L)).thenReturn(Optional.of(activeCategory(1L, "Klasyczna")));
        when(baseServiceRepo.countByCategoryIdAndIsDeletedFalse(1L)).thenReturn(3L);

        assertThrows(DeletionException.class, () -> categoryService.deleteCategoryById(1L));

        verify(serviceCategoryRepo, never()).deleteById(any());
    }

    @Test
    void deleteCategoryById_shouldSoftDelete_whenHasOnlySoftDeletedServices() {
        BaseServiceCategory category = activeCategory(1L, "Klasyczna");

        when(serviceCategoryRepo.findOneById(1L)).thenReturn(Optional.of(category));
        when(baseServiceRepo.countByCategoryIdAndIsDeletedFalse(1L)).thenReturn(0L);
        when(baseServiceRepo.countByCategoryId(1L)).thenReturn(2L);

        categoryService.deleteCategoryById(1L);

        assertTrue(category.getIsDeleted());
        verify(serviceCategoryRepo).save(category);
        verify(serviceCategoryRepo, never()).deleteById(any());
    }

    @Test
    void deleteCategoryById_shouldPhysicallyDelete_whenNoServicesAtAll() {
        when(serviceCategoryRepo.findOneById(1L)).thenReturn(Optional.of(activeCategory(1L, "Klasyczna")));
        when(baseServiceRepo.countByCategoryIdAndIsDeletedFalse(1L)).thenReturn(0L);
        when(baseServiceRepo.countByCategoryId(1L)).thenReturn(0L);

        categoryService.deleteCategoryById(1L);

        verify(serviceCategoryRepo).deleteById(1L);
        verify(serviceCategoryRepo, never()).save(any());
    }
}

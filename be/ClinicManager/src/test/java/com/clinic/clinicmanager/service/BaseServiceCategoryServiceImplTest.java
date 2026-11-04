package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.BaseServiceCategoryDTO;
import com.clinic.clinicmanager.DTO.CategoryUsageDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.BaseServiceCategory;
import com.clinic.clinicmanager.repo.BaseServiceCategoryRepo;
import com.clinic.clinicmanager.repo.BaseServiceRepo;
import com.clinic.clinicmanager.service.impl.BaseServiceCategoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BaseServiceCategoryServiceImplTest {

    private static final String NAME_TAKEN = "Kategoria o tej nazwie już istnieje.";
    private static final String HIDDEN_NAME_TAKEN =
            "Istnieje ukryta kategoria o tej nazwie. Dodaj ją przez „Nowa kategoria”, żeby ją przywrócić.";
    private static final String NAME_TOO_LONG = "Nazwa kategorii może mieć maksymalnie 50 znaków.";
    private static final String COLOR_INVALID = "Niepoprawny format koloru.";

    @Mock BaseServiceCategoryRepo serviceCategoryRepo;
    @Mock BaseServiceRepo baseServiceRepo;
    @Mock AuditLogService auditLogService;

    @InjectMocks
    BaseServiceCategoryServiceImpl categoryService;

    private BaseServiceCategory activeCategory(Long id, String name) {
        return BaseServiceCategory.builder().id(id).name(name).color("255,255,255").isDeleted(false).build();
    }

    private BaseServiceCategory hiddenCategory(Long id, String name, String color) {
        return BaseServiceCategory.builder().id(id).name(name).color(color).isDeleted(true).build();
    }

    private void stubSaveReturningArgument() {
        when(serviceCategoryRepo.saveAndFlush(any(BaseServiceCategory.class))).thenAnswer(inv -> {
            BaseServiceCategory saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(10L);
            }
            return saved;
        });
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
        BaseServiceCategory deleted = hiddenCategory(2L, "Stara", "#000");

        when(serviceCategoryRepo.findAll()).thenReturn(List.of(active, deleted));

        List<BaseServiceCategoryDTO> result = categoryService.getCategories();

        assertEquals(1, result.size());
        assertEquals("Klasyczna", result.getFirst().getName());
    }

    @Test
    void getCategoryUsage_shouldReturnRepoResult() {
        when(serviceCategoryRepo.findActiveCategoryUsage()).thenReturn(List.of(new CategoryUsageDTO(1L, 7L)));

        List<CategoryUsageDTO> result = categoryService.getCategoryUsage();

        assertEquals(1, result.size());
        assertEquals(1L, result.getFirst().getCategoryId());
        assertEquals(7L, result.getFirst().getCount());
    }

    @Test
    void createCategory_shouldSaveAndReturnDTO_whenNameIsUnique() {
        BaseServiceCategoryDTO input = new BaseServiceCategoryDTO(null, " Klasyczna ", "255,255,255", true);
        when(serviceCategoryRepo.findAllByCategoryName("Klasyczna")).thenReturn(List.of());
        when(serviceCategoryRepo.existsByColorAndIsDeletedFalse("255,255,255")).thenReturn(false);
        stubSaveReturningArgument();

        BaseServiceCategoryDTO result = categoryService.createCategory(input);

        ArgumentCaptor<BaseServiceCategory> captor = ArgumentCaptor.forClass(BaseServiceCategory.class);
        verify(serviceCategoryRepo).saveAndFlush(captor.capture());
        assertEquals("Klasyczna", captor.getValue().getName());
        assertFalse(captor.getValue().getIsDeleted());
        assertEquals(10L, result.getId());
        assertFalse(result.getIsDeleted());
        assertNull(result.getRestored());
        verify(auditLogService).logCreate(eq("BaseServiceCategory"), eq(10L), eq("Klasyczna"), any());
    }

    @Test
    void createCategory_shouldThrowConflict_whenNameMatchesActiveCategoryIgnoringCase() {
        when(serviceCategoryRepo.findAllByCategoryName("klasyczna")).thenReturn(List.of(activeCategory(1L, "Klasyczna")));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> categoryService.createCategory(new BaseServiceCategoryDTO(null, "klasyczna ", "170,187,204", false)));

        assertEquals(NAME_TAKEN, ex.getMessage());
        verify(serviceCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void createCategory_shouldRestoreHiddenCategory_whenNameMatchesHiddenCategory() {
        BaseServiceCategory hidden = hiddenCategory(5L, "Masaże", "17,17,17");
        when(serviceCategoryRepo.findAllByCategoryName("MASAŻE")).thenReturn(List.of(hidden));
        when(serviceCategoryRepo.existsByColorAndIsDeletedFalse("34,34,34")).thenReturn(false);
        stubSaveReturningArgument();

        BaseServiceCategoryDTO result = categoryService.createCategory(new BaseServiceCategoryDTO(null, "MASAŻE", "34,34,34", false));

        assertEquals(5L, result.getId());
        assertTrue(result.getRestored());
        assertEquals("34,34,34", result.getColor());
        assertEquals("MASAŻE", result.getName());
        assertFalse(result.getIsDeleted());
        assertFalse(hidden.getIsDeleted());
        verify(serviceCategoryRepo).saveAndFlush(hidden);
        verify(auditLogService).logUpdate(eq("BaseServiceCategory"), eq(5L), eq("Masaże"), any(), any());
        verify(auditLogService, never()).logCreate(any(), any(), any(), any());
        verifyNoInteractions(baseServiceRepo);
    }

    @Test
    void createCategory_shouldThrowConflict_whenColorUsedByOtherActiveCategory() {
        when(serviceCategoryRepo.findAllByCategoryName("Nowa")).thenReturn(List.of());
        when(serviceCategoryRepo.existsByColorAndIsDeletedFalse("255,255,255")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> categoryService.createCategory(new BaseServiceCategoryDTO(null, "Nowa", "255,255,255", false)));

        assertEquals("Ten kolor ma już inna kategoria.", ex.getMessage());
        verify(serviceCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void createCategory_shouldSucceed_whenColorUsedOnlyByHiddenCategory() {
        when(serviceCategoryRepo.findAllByCategoryName("Nowa")).thenReturn(List.of());
        when(serviceCategoryRepo.existsByColorAndIsDeletedFalse("17,17,17")).thenReturn(false);
        stubSaveReturningArgument();

        BaseServiceCategoryDTO result = categoryService.createCategory(new BaseServiceCategoryDTO(null, "Nowa", "17,17,17", false));

        assertEquals("17,17,17", result.getColor());
        assertNull(result.getRestored());
    }

    @Test
    void createCategory_shouldThrowValidationException_whenNameBlank() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> categoryService.createCategory(new BaseServiceCategoryDTO(null, "  ", "255,255,255", false)));

        assertEquals("Nazwa kategorii jest wymagana.", ex.getMessage());
        verify(serviceCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void createCategory_shouldThrowValidation_whenTrimmedNameLongerThan50() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> categoryService.createCategory(new BaseServiceCategoryDTO(null, " " + "m".repeat(51) + " ", "1,2,3", false)));

        assertEquals(NAME_TOO_LONG, ex.getMessage());
        verify(serviceCategoryRepo, never()).findAllByCategoryName(any());
        verify(serviceCategoryRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void createCategory_shouldAcceptNameOfExactly50CharsAfterTrim() {
        String name = "m".repeat(50);
        when(serviceCategoryRepo.findAllByCategoryName(name)).thenReturn(List.of());
        when(serviceCategoryRepo.existsByColorAndIsDeletedFalse("1,2,3")).thenReturn(false);
        stubSaveReturningArgument();

        BaseServiceCategoryDTO result = categoryService.createCategory(new BaseServiceCategoryDTO(null, "  " + name, "1,2,3", false));

        assertEquals(name, result.getName());
        verify(auditLogService).logCreate(eq("BaseServiceCategory"), eq(10L), eq(name), any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"300,0,0", "256,0,0", "abc", "#fff", "1,2", "1,2,3,4"})
    void createCategory_shouldThrowValidation_whenColorFormatInvalid(String color) {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> categoryService.createCategory(new BaseServiceCategoryDTO(null, "Nowa", color, false)));

        assertEquals(COLOR_INVALID, ex.getMessage());
        verify(serviceCategoryRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void createCategory_shouldSaveColorWithoutWhitespace() {
        when(serviceCategoryRepo.findAllByCategoryName("Nowa")).thenReturn(List.of());
        when(serviceCategoryRepo.existsByColorAndIsDeletedFalse("1,2,3")).thenReturn(false);
        stubSaveReturningArgument();

        BaseServiceCategoryDTO result = categoryService.createCategory(new BaseServiceCategoryDTO(null, "Nowa", " 1, 2, 3 ", false));

        ArgumentCaptor<BaseServiceCategory> captor = ArgumentCaptor.forClass(BaseServiceCategory.class);
        verify(serviceCategoryRepo).saveAndFlush(captor.capture());
        assertEquals("1,2,3", captor.getValue().getColor());
        assertEquals("1,2,3", result.getColor());
    }

    @Test
    void updateCategory_shouldSaveAndReturnDTO_whenNoConflict() {
        BaseServiceCategory existing = activeCategory(1L, "Stara");
        when(serviceCategoryRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(serviceCategoryRepo.findAllByCategoryName("Nowa")).thenReturn(List.of());
        when(serviceCategoryRepo.existsByColorAndIsDeletedFalseAndIdNot("255,255,255", 1L)).thenReturn(false);
        stubSaveReturningArgument();

        BaseServiceCategoryDTO result = categoryService.updateCategory(1L, new BaseServiceCategoryDTO(null, "Nowa", "255,255,255", false));

        assertEquals("Nowa", result.getName());
        verify(auditLogService).logUpdate(eq("BaseServiceCategory"), eq(1L), eq("Stara"), any(), any());
    }

    @Test
    void updateCategory_shouldIgnoreIsDeletedFromRequest() {
        BaseServiceCategory existing = activeCategory(1L, "Klasyczna");
        when(serviceCategoryRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(serviceCategoryRepo.findAllByCategoryName("Klasyczna")).thenReturn(List.of(existing));
        when(serviceCategoryRepo.existsByColorAndIsDeletedFalseAndIdNot("0,0,255", 1L)).thenReturn(false);
        stubSaveReturningArgument();

        BaseServiceCategoryDTO result = categoryService.updateCategory(1L, new BaseServiceCategoryDTO(99L, "Klasyczna", "0,0,255", true));

        ArgumentCaptor<BaseServiceCategory> captor = ArgumentCaptor.forClass(BaseServiceCategory.class);
        verify(serviceCategoryRepo).saveAndFlush(captor.capture());
        assertFalse(captor.getValue().getIsDeleted());
        assertEquals(1L, captor.getValue().getId());
        assertFalse(result.getIsDeleted());
        assertEquals("0,0,255", result.getColor());
    }

    @Test
    void updateCategory_shouldThrowResourceNotFound_whenCategoryHidden() {
        when(serviceCategoryRepo.findById(5L)).thenReturn(Optional.of(hiddenCategory(5L, "Masaże", "17,17,17")));

        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.updateCategory(5L, new BaseServiceCategoryDTO(null, "Masaże", "17,17,17", false)));

        verify(serviceCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void updateCategory_shouldThrowResourceNotFoundException_whenNotFound() {
        when(serviceCategoryRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> categoryService.updateCategory(99L, new BaseServiceCategoryDTO()));
    }

    @Test
    void updateCategory_shouldThrowConflictException_whenNameTakenByAnotherCategory() {
        when(serviceCategoryRepo.findById(1L)).thenReturn(Optional.of(activeCategory(1L, "Stara")));
        when(serviceCategoryRepo.findAllByCategoryName("Klasyczna")).thenReturn(List.of(activeCategory(2L, "Klasyczna")));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> categoryService.updateCategory(1L, new BaseServiceCategoryDTO(null, "Klasyczna", "255,255,255", false)));

        assertEquals(NAME_TAKEN, ex.getMessage());
    }

    @Test
    void updateCategory_shouldThrowConflictException_whenNameTakenByHiddenCategory() {
        when(serviceCategoryRepo.findById(1L)).thenReturn(Optional.of(activeCategory(1L, "Stara")));
        when(serviceCategoryRepo.findAllByCategoryName("Masaże")).thenReturn(List.of(hiddenCategory(5L, "Masaże", "17,17,17")));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> categoryService.updateCategory(1L, new BaseServiceCategoryDTO(null, "Masaże", "255,255,255", false)));

        assertEquals(HIDDEN_NAME_TAKEN, ex.getMessage());
        verify(serviceCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void updateCategory_shouldThrowHiddenNameConflict_whenNameMatchesHiddenCategoryWithDifferentCase() {
        BaseServiceCategory hidden = hiddenCategory(5L, "Masaże", "17,17,17");
        when(serviceCategoryRepo.findById(1L)).thenReturn(Optional.of(activeCategory(1L, "Stara")));
        when(serviceCategoryRepo.findAllByCategoryName("masaże")).thenReturn(List.of(hidden));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> categoryService.updateCategory(1L, new BaseServiceCategoryDTO(null, " masaże", "255,255,255", false)));

        assertEquals(HIDDEN_NAME_TAKEN, ex.getMessage());
        assertTrue(hidden.getIsDeleted());
        verify(serviceCategoryRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void updateCategory_shouldThrowConflict_whenColorUsedByOtherActiveCategory() {
        BaseServiceCategory existing = activeCategory(1L, "Klasyczna");
        when(serviceCategoryRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(serviceCategoryRepo.findAllByCategoryName("Klasyczna")).thenReturn(List.of(existing));
        when(serviceCategoryRepo.existsByColorAndIsDeletedFalseAndIdNot("0,0,0", 1L)).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> categoryService.updateCategory(1L, new BaseServiceCategoryDTO(null, "Klasyczna", "0,0,0", false)));

        assertEquals("Ten kolor ma już inna kategoria.", ex.getMessage());
        verify(serviceCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void updateCategory_shouldNotThrowConflict_whenNameBelongsToSameCategory() {
        BaseServiceCategory existing = activeCategory(1L, "Klasyczna");
        when(serviceCategoryRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(serviceCategoryRepo.findAllByCategoryName("Klasyczna")).thenReturn(List.of(existing));
        when(serviceCategoryRepo.existsByColorAndIsDeletedFalseAndIdNot("0,0,255", 1L)).thenReturn(false);
        stubSaveReturningArgument();

        assertDoesNotThrow(() -> categoryService.updateCategory(1L, new BaseServiceCategoryDTO(null, "Klasyczna", "0,0,255", false)));
    }

    @Test
    void updateCategory_shouldThrowValidation_whenTrimmedNameLongerThan50() {
        BaseServiceCategory existing = activeCategory(1L, "Klasyczna");
        when(serviceCategoryRepo.findById(1L)).thenReturn(Optional.of(existing));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> categoryService.updateCategory(1L, new BaseServiceCategoryDTO(null, "k".repeat(51), "0,0,255", false)));

        assertEquals(NAME_TOO_LONG, ex.getMessage());
        assertEquals("Klasyczna", existing.getName());
        verify(serviceCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void updateCategory_shouldThrowValidation_whenColorInvalid() {
        BaseServiceCategory existing = activeCategory(1L, "Klasyczna");
        when(serviceCategoryRepo.findById(1L)).thenReturn(Optional.of(existing));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> categoryService.updateCategory(1L, new BaseServiceCategoryDTO(null, "Klasyczna", "0,0,256", false)));

        assertEquals(COLOR_INVALID, ex.getMessage());
        assertEquals("255,255,255", existing.getColor());
        verify(serviceCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void deleteCategoryById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(serviceCategoryRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> categoryService.deleteCategoryById(99L));
    }

    @Test
    void deleteCategoryById_shouldThrowResourceNotFound_whenAlreadySoftDeleted() {
        when(serviceCategoryRepo.findById(1L)).thenReturn(Optional.of(hiddenCategory(1L, "Stara", "#fff")));

        assertThrows(ResourceNotFoundException.class, () -> categoryService.deleteCategoryById(1L));

        verify(serviceCategoryRepo, never()).deleteById(any());
        verify(serviceCategoryRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void deleteCategoryById_shouldThrowConflictWithCount_whenHasActiveServices() {
        when(serviceCategoryRepo.findById(1L)).thenReturn(Optional.of(activeCategory(1L, "Klasyczna")));
        when(baseServiceRepo.countByCategoryIdAndIsDeletedFalse(1L)).thenReturn(3L);

        ConflictException ex = assertThrows(ConflictException.class, () -> categoryService.deleteCategoryById(1L));

        assertEquals("Kategoria ma przypisane usługi (3). Najpierw przenieś je do innej kategorii.", ex.getMessage());
        verify(serviceCategoryRepo, never()).deleteById(any());
        verify(serviceCategoryRepo, never()).save(any());
    }

    @Test
    void deleteCategoryById_shouldSoftDelete_whenHasOnlySoftDeletedServices() {
        BaseServiceCategory category = activeCategory(1L, "Klasyczna");

        when(serviceCategoryRepo.findById(1L)).thenReturn(Optional.of(category));
        when(baseServiceRepo.countByCategoryIdAndIsDeletedFalse(1L)).thenReturn(0L);
        when(baseServiceRepo.countByCategoryId(1L)).thenReturn(2L);

        categoryService.deleteCategoryById(1L);

        assertTrue(category.getIsDeleted());
        verify(serviceCategoryRepo).save(category);
        verify(serviceCategoryRepo, never()).deleteById(any());
        verify(auditLogService).logDelete(eq("BaseServiceCategory"), eq(1L), eq("Klasyczna"), any());
    }

    @Test
    void deleteCategoryById_shouldPhysicallyDelete_whenNoServicesAtAll() {
        when(serviceCategoryRepo.findById(1L)).thenReturn(Optional.of(activeCategory(1L, "Klasyczna")));
        when(baseServiceRepo.countByCategoryIdAndIsDeletedFalse(1L)).thenReturn(0L);
        when(baseServiceRepo.countByCategoryId(1L)).thenReturn(0L);

        categoryService.deleteCategoryById(1L);

        verify(serviceCategoryRepo).deleteById(1L);
        verify(serviceCategoryRepo, never()).save(any());
        verify(auditLogService).logDelete(eq("BaseServiceCategory"), eq(1L), eq("Klasyczna"), any());
    }

    @Test
    void createCategory_shouldRestoreLowestIdHiddenCategory_whenTwoHiddenDifferOnlyByCase() {
        BaseServiceCategory first = hiddenCategory(5L, "masaże", "17,17,17");
        BaseServiceCategory second = hiddenCategory(7L, "MASAŻE", "51,51,51");
        when(serviceCategoryRepo.findAllByCategoryName("Masaże")).thenReturn(List.of(first, second));
        when(serviceCategoryRepo.existsByColorAndIsDeletedFalse("34,34,34")).thenReturn(false);
        stubSaveReturningArgument();

        BaseServiceCategoryDTO result = assertDoesNotThrow(() -> categoryService.createCategory(
                new BaseServiceCategoryDTO(null, "Masaże", "34,34,34", false)));

        assertEquals(5L, result.getId());
        assertTrue(result.getRestored());
        assertFalse(first.getIsDeleted());
        assertTrue(second.getIsDeleted());
        verify(serviceCategoryRepo, times(1)).saveAndFlush(any(BaseServiceCategory.class));
    }

    @Test
    void createCategory_shouldThrowConflict_whenActiveAndHiddenDifferOnlyByCase() {
        when(serviceCategoryRepo.findAllByCategoryName("masaże")).thenReturn(List.of(
                hiddenCategory(5L, "MASAŻE", "17,17,17"), activeCategory(6L, "Masaże")));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> categoryService.createCategory(new BaseServiceCategoryDTO(null, "masaże", "34,34,34", false)));

        assertEquals(NAME_TAKEN, ex.getMessage());
        verify(serviceCategoryRepo, never()).saveAndFlush(any());
    }

    private DataIntegrityViolationException uniqueViolation(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"" + constraint + "\""));
    }

    @Test
    void createCategory_shouldThrowConflictNameTaken_whenNameIndexViolatedInRace() {
        when(serviceCategoryRepo.findAllByCategoryName("Nowa")).thenReturn(List.of());
        when(serviceCategoryRepo.existsByColorAndIsDeletedFalse("17,34,51")).thenReturn(false);
        when(serviceCategoryRepo.saveAndFlush(any(BaseServiceCategory.class)))
                .thenThrow(uniqueViolation("uq_service_category_name_ci"));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> categoryService.createCategory(new BaseServiceCategoryDTO(null, "Nowa", "17,34,51", false)));

        assertEquals(NAME_TAKEN, ex.getMessage());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void createCategory_shouldThrowConflictColorTaken_whenColorIndexViolatedInRace() {
        when(serviceCategoryRepo.findAllByCategoryName("Nowa")).thenReturn(List.of());
        when(serviceCategoryRepo.existsByColorAndIsDeletedFalse("17,34,51")).thenReturn(false);
        when(serviceCategoryRepo.saveAndFlush(any(BaseServiceCategory.class)))
                .thenThrow(uniqueViolation("uq_service_category_color_active"));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> categoryService.createCategory(new BaseServiceCategoryDTO(null, "Nowa", "17,34,51", false)));

        assertEquals("Ten kolor ma już inna kategoria.", ex.getMessage());
        verifyNoInteractions(auditLogService);
    }
}

package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.CategoryUsageDTO;
import com.clinic.clinicmanager.DTO.ProductCategoryDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.repo.ProductCategoryRepo;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.service.impl.ProductCategoryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCategoryServiceImplTest {

    private static final String NAME_TAKEN = "Kategoria o tej nazwie już istnieje.";
    private static final String HIDDEN_NAME_TAKEN =
            "Istnieje ukryta kategoria o tej nazwie. Dodaj ją przez „Nowa kategoria”, żeby ją przywrócić.";
    private static final String NAME_TOO_LONG = "Nazwa kategorii może mieć maksymalnie 50 znaków.";
    private static final String COLOR_INVALID = "Niepoprawny format koloru.";

    @Mock ProductCategoryRepo productCategoryRepo;
    @Mock ProductRepo productRepo;
    @Mock AuditLogService auditLogService;

    @InjectMocks
    ProductCategoryServiceImpl productCategoryService;

    private ProductCategory category(Long id, String name, String color) {
        return ProductCategory.builder().id(id).name(name).color(color).build();
    }

    private ProductCategory systemCategory(Long id, String name, String color) {
        return ProductCategory.builder().id(id).name(name).color(color).isSystem(true).build();
    }

    private ProductCategory hiddenCategory(Long id, String name, String color) {
        return ProductCategory.builder().id(id).name(name).color(color).isDeleted(true).build();
    }

    private void stubSaveReturningArgument() {
        when(productCategoryRepo.saveAndFlush(any(ProductCategory.class))).thenAnswer(inv -> {
            ProductCategory saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(10L);
            }
            return saved;
        });
    }

    @Test
    void getCategoryById_shouldReturnDTO_whenCategoryExists() {
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(systemCategory(1L, "Produkty", "#FF0000")));

        ProductCategoryDTO result = productCategoryService.getCategoryById(1L);

        assertEquals(1L, result.getId());
        assertEquals("Produkty", result.getName());
        assertTrue(result.getSystem());
    }

    @Test
    void getCategoryById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(productCategoryRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productCategoryService.getCategoryById(99L));
    }

    @Test
    void getCategories_shouldReturnOnlyActiveCategoriesWithSystemFlag() {
        ProductCategory c1 = systemCategory(1L, "Produkty", "#FF0000");
        ProductCategory c2 = category(2L, "Narzędzia", "#00FF00");
        when(productCategoryRepo.findAllByIsDeletedFalse()).thenReturn(List.of(c1, c2));

        List<ProductCategoryDTO> result = productCategoryService.getCategories();

        assertEquals(2, result.size());
        assertTrue(result.get(0).getSystem());
        assertFalse(result.get(1).getSystem());
        verify(productCategoryRepo, never()).findAll();
    }

    @Test
    void getCategoryUsage_shouldReturnRepoResult() {
        List<CategoryUsageDTO> usage = List.of(new CategoryUsageDTO(1L, 4L), new CategoryUsageDTO(2L, 0L));
        when(productCategoryRepo.findActiveCategoryUsage()).thenReturn(usage);

        List<CategoryUsageDTO> result = productCategoryService.getCategoryUsage();

        assertEquals(2, result.size());
        assertEquals(4L, result.get(0).getCount());
        assertEquals(0L, result.get(1).getCount());
    }

    @Test
    void createCategory_shouldSaveNonSystemCategory_whenNameIsNew() {
        ProductCategoryDTO input = new ProductCategoryDTO(null, "  Nowa  ", "0,0,0");
        input.setSystem(true);
        when(productCategoryRepo.findAllByCategoryName("Nowa")).thenReturn(List.of());
        when(productCategoryRepo.existsByColorAndIsDeletedFalse("0,0,0")).thenReturn(false);
        stubSaveReturningArgument();

        ProductCategoryDTO result = productCategoryService.createCategory(input);

        ArgumentCaptor<ProductCategory> captor = ArgumentCaptor.forClass(ProductCategory.class);
        verify(productCategoryRepo).saveAndFlush(captor.capture());
        assertEquals("Nowa", captor.getValue().getName());
        assertFalse(captor.getValue().getIsSystem());
        assertFalse(captor.getValue().getIsDeleted());
        assertEquals(10L, result.getId());
        assertEquals("Nowa", result.getName());
        assertFalse(result.getSystem());
        assertNull(result.getRestored());
        verify(auditLogService).logCreate(eq("ProductCategory"), eq(10L), eq("Nowa"), any());
    }

    @Test
    void createCategory_shouldThrowConflict_whenNameMatchesActiveCategoryIgnoringCaseAndSpaces() {
        when(productCategoryRepo.findAllByCategoryName("produkty"))
                .thenReturn(List.of(systemCategory(1L, "Produkty", "255,0,0")));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> productCategoryService.createCategory(new ProductCategoryDTO(null, "produkty ", "18,52,86")));

        assertEquals(NAME_TAKEN, ex.getMessage());
        verify(productCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void createCategory_shouldRestoreHiddenCategory_whenNameMatchesHiddenCategory() {
        ProductCategory hidden = hiddenCategory(5L, "Jednorazowe", "170,170,170");
        when(productCategoryRepo.findAllByCategoryName("jednorazowe")).thenReturn(List.of(hidden));
        when(productCategoryRepo.existsByColorAndIsDeletedFalse("187,187,187")).thenReturn(false);
        stubSaveReturningArgument();

        ProductCategoryDTO result = productCategoryService.createCategory(
                new ProductCategoryDTO(null, "jednorazowe", "187,187,187"));

        assertEquals(5L, result.getId());
        assertTrue(result.getRestored());
        assertEquals("187,187,187", result.getColor());
        assertEquals("jednorazowe", result.getName());
        assertFalse(hidden.getIsDeleted());
        assertFalse(hidden.getIsSystem());
        verify(productCategoryRepo).saveAndFlush(hidden);
        verify(auditLogService).logUpdate(eq("ProductCategory"), eq(5L), eq("Jednorazowe"), any(), any());
        verify(auditLogService, never()).logCreate(any(), any(), any(), any());
        verifyNoInteractions(productRepo);
    }

    @Test
    void createCategory_shouldThrowConflict_whenColorUsedByOtherActiveCategory() {
        when(productCategoryRepo.findAllByCategoryName("Nowa")).thenReturn(List.of());
        when(productCategoryRepo.existsByColorAndIsDeletedFalse("255,0,0")).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> productCategoryService.createCategory(new ProductCategoryDTO(null, "Nowa", "255,0,0")));

        assertEquals("Ten kolor ma już inna kategoria.", ex.getMessage());
        verify(productCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void createCategory_shouldSucceed_whenColorUsedOnlyByHiddenCategory() {
        when(productCategoryRepo.findAllByCategoryName("Nowa")).thenReturn(List.of());
        when(productCategoryRepo.existsByColorAndIsDeletedFalse("170,170,170")).thenReturn(false);
        stubSaveReturningArgument();

        ProductCategoryDTO result = productCategoryService.createCategory(new ProductCategoryDTO(null, "Nowa", "170,170,170"));

        assertEquals("170,170,170", result.getColor());
        assertNull(result.getRestored());
        verify(productCategoryRepo).saveAndFlush(any(ProductCategory.class));
    }

    @Test
    void createCategory_shouldThrowValidationException_whenNameBlank() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> productCategoryService.createCategory(new ProductCategoryDTO(null, "   ", "0,0,0")));

        assertEquals("Nazwa kategorii jest wymagana.", ex.getMessage());
        verify(productCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void createCategory_shouldThrowValidationException_whenNameNull() {
        assertThrows(ValidationException.class,
                () -> productCategoryService.createCategory(new ProductCategoryDTO(null, null, "0,0,0")));

        verify(productCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void createCategory_shouldThrowValidation_whenTrimmedNameLongerThan50() {
        String name = "  " + "a".repeat(51) + "  ";

        ValidationException ex = assertThrows(ValidationException.class,
                () -> productCategoryService.createCategory(new ProductCategoryDTO(null, name, "1,2,3")));

        assertEquals(NAME_TOO_LONG, ex.getMessage());
        verify(productCategoryRepo, never()).findAllByCategoryName(any());
        verify(productCategoryRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void createCategory_shouldAcceptNameOfExactly50CharsAfterTrim() {
        String name = "b".repeat(50);
        when(productCategoryRepo.findAllByCategoryName(name)).thenReturn(List.of());
        when(productCategoryRepo.existsByColorAndIsDeletedFalse("1,2,3")).thenReturn(false);
        stubSaveReturningArgument();

        ProductCategoryDTO result = productCategoryService.createCategory(new ProductCategoryDTO(null, " " + name + " ", "1,2,3"));

        assertEquals(name, result.getName());
        assertEquals(50, result.getName().length());
        verify(auditLogService).logCreate(eq("ProductCategory"), eq(10L), eq(name), any());
    }

    @Test
    void createCategory_shouldSaveColorWithoutWhitespace() {
        when(productCategoryRepo.findAllByCategoryName("Nowa")).thenReturn(List.of());
        when(productCategoryRepo.existsByColorAndIsDeletedFalse("10,20,30")).thenReturn(false);
        stubSaveReturningArgument();

        ProductCategoryDTO result = productCategoryService.createCategory(new ProductCategoryDTO(null, "Nowa", " 10, 20 ,30 "));

        ArgumentCaptor<ProductCategory> captor = ArgumentCaptor.forClass(ProductCategory.class);
        verify(productCategoryRepo).saveAndFlush(captor.capture());
        assertEquals("10,20,30", captor.getValue().getColor());
        assertEquals("10,20,30", result.getColor());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"256,0,0", "abc", "#ff0000", "1,2", "1,2,3,4", "-1,2,3", "1,,3"})
    void createCategory_shouldThrowValidation_whenColorFormatInvalid(String color) {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> productCategoryService.createCategory(new ProductCategoryDTO(null, "Nowa", color)));

        assertEquals(COLOR_INVALID, ex.getMessage());
        verify(productCategoryRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void createCategory_shouldRestoreHiddenCategoryWithNormalizedColor() {
        ProductCategory hidden = hiddenCategory(5L, "Jednorazowe", "170,170,170");
        when(productCategoryRepo.findAllByCategoryName("Jednorazowe")).thenReturn(List.of(hidden));
        when(productCategoryRepo.existsByColorAndIsDeletedFalse("7,8,9")).thenReturn(false);
        stubSaveReturningArgument();

        ProductCategoryDTO result = productCategoryService.createCategory(new ProductCategoryDTO(null, "Jednorazowe", " 7, 8, 9 "));

        assertTrue(result.getRestored());
        assertEquals("7,8,9", hidden.getColor());
        assertEquals("7,8,9", result.getColor());
    }

    @Test
    void createCategory_shouldAcquireWriteLockBeforeReadingCategories() {
        when(productCategoryRepo.findAllByCategoryName("Nowa")).thenReturn(List.of());
        when(productCategoryRepo.existsByColorAndIsDeletedFalse("1,2,3")).thenReturn(false);
        stubSaveReturningArgument();

        productCategoryService.createCategory(new ProductCategoryDTO(null, "Nowa", "1,2,3"));

        InOrder inOrder = inOrder(productCategoryRepo);
        inOrder.verify(productCategoryRepo).acquireTransactionLock(anyLong());
        inOrder.verify(productCategoryRepo).findAllByCategoryName("Nowa");
        inOrder.verify(productCategoryRepo).existsByColorAndIsDeletedFalse("1,2,3");
        inOrder.verify(productCategoryRepo).saveAndFlush(any(ProductCategory.class));
    }

    @Test
    void updateCategory_shouldRenameSystemCategoryAndKeepSystemFlag() {
        ProductCategory existing = systemCategory(1L, "Produkty", "255,0,0");
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(productCategoryRepo.findAllByCategoryName("Towary")).thenReturn(List.of());
        when(productCategoryRepo.existsByColorAndIsDeletedFalseAndIdNot("255,0,0", 1L)).thenReturn(false);
        stubSaveReturningArgument();

        ProductCategoryDTO result = productCategoryService.updateCategory(1L, new ProductCategoryDTO(null, " Towary ", "255,0,0"));

        assertEquals("Towary", result.getName());
        assertTrue(result.getSystem());
        assertTrue(existing.getIsSystem());
        verify(auditLogService).logUpdate(eq("ProductCategory"), eq(1L), eq("Produkty"), any(), any());
    }

    @Test
    void updateCategory_shouldIgnoreSystemFlagFromRequest() {
        ProductCategory existing = systemCategory(1L, "Produkty", "255,0,0");
        ProductCategoryDTO input = new ProductCategoryDTO(99L, "Produkty", "0,255,0");
        input.setSystem(false);
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(productCategoryRepo.findAllByCategoryName("Produkty")).thenReturn(List.of(existing));
        when(productCategoryRepo.existsByColorAndIsDeletedFalseAndIdNot("0,255,0", 1L)).thenReturn(false);
        stubSaveReturningArgument();

        ProductCategoryDTO result = productCategoryService.updateCategory(1L, input);

        ArgumentCaptor<ProductCategory> captor = ArgumentCaptor.forClass(ProductCategory.class);
        verify(productCategoryRepo).saveAndFlush(captor.capture());
        assertTrue(captor.getValue().getIsSystem());
        assertFalse(captor.getValue().getIsDeleted());
        assertEquals(1L, captor.getValue().getId());
        assertTrue(result.getSystem());
        assertEquals("0,255,0", result.getColor());
    }

    @Test
    void updateCategory_shouldThrowResourceNotFound_whenCategoryHidden() {
        when(productCategoryRepo.findById(5L)).thenReturn(Optional.of(hiddenCategory(5L, "Jednorazowe", "170,170,170")));

        assertThrows(ResourceNotFoundException.class,
                () -> productCategoryService.updateCategory(5L, new ProductCategoryDTO(null, "Jednorazowe", "170,170,170")));

        verify(productCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void updateCategory_shouldThrowResourceNotFoundException_whenNotFound() {
        when(productCategoryRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> productCategoryService.updateCategory(99L, new ProductCategoryDTO(null, "X", "0,0,0")));
    }

    @Test
    void updateCategory_shouldThrowConflict_whenNameTakenByOtherActiveCategory() {
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(systemCategory(1L, "Produkty", "255,0,0")));
        when(productCategoryRepo.findAllByCategoryName("narzędzia")).thenReturn(List.of(category(2L, "Narzędzia", "0,255,0")));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> productCategoryService.updateCategory(1L, new ProductCategoryDTO(null, "narzędzia", "255,0,0")));

        assertEquals(NAME_TAKEN, ex.getMessage());
        verify(productCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void updateCategory_shouldThrowConflict_whenNameTakenByHiddenCategory() {
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(category(1L, "Kremy", "255,0,0")));
        when(productCategoryRepo.findAllByCategoryName("Jednorazowe"))
                .thenReturn(List.of(hiddenCategory(5L, "Jednorazowe", "170,170,170")));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> productCategoryService.updateCategory(1L, new ProductCategoryDTO(null, "Jednorazowe", "255,0,0")));

        assertEquals(HIDDEN_NAME_TAKEN, ex.getMessage());
        verify(productCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void updateCategory_shouldThrowHiddenNameConflict_whenNameMatchesHiddenCategoryWithDifferentCase() {
        ProductCategory hidden = hiddenCategory(5L, "Jednorazowe", "170,170,170");
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(category(1L, "Kremy", "255,0,0")));
        when(productCategoryRepo.findAllByCategoryName("JEDNORAZOWE")).thenReturn(List.of(hidden));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> productCategoryService.updateCategory(1L, new ProductCategoryDTO(null, " JEDNORAZOWE ", "255,0,0")));

        assertEquals(HIDDEN_NAME_TAKEN, ex.getMessage());
        assertTrue(hidden.getIsDeleted());
        assertEquals("Jednorazowe", hidden.getName());
        verify(productCategoryRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void updateCategory_shouldThrowConflict_whenColorUsedByOtherActiveCategory() {
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(category(1L, "Kremy", "255,0,0")));
        when(productCategoryRepo.findAllByCategoryName("Kremy")).thenReturn(List.of(category(1L, "Kremy", "255,0,0")));
        when(productCategoryRepo.existsByColorAndIsDeletedFalseAndIdNot("0,255,0", 1L)).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> productCategoryService.updateCategory(1L, new ProductCategoryDTO(null, "Kremy", "0,255,0")));

        assertEquals("Ten kolor ma już inna kategoria.", ex.getMessage());
        verify(productCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void updateCategory_shouldThrowValidationException_whenNameBlank() {
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(category(1L, "Kremy", "255,0,0")));

        assertThrows(ValidationException.class,
                () -> productCategoryService.updateCategory(1L, new ProductCategoryDTO(null, " ", "255,0,0")));

        verify(productCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void updateCategory_shouldThrowValidation_whenTrimmedNameLongerThan50() {
        ProductCategory existing = category(1L, "Kremy", "255,0,0");
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(existing));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> productCategoryService.updateCategory(1L, new ProductCategoryDTO(null, "c".repeat(51), "255,0,0")));

        assertEquals(NAME_TOO_LONG, ex.getMessage());
        assertEquals("Kremy", existing.getName());
        verify(productCategoryRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void updateCategory_shouldThrowValidation_whenColorInvalid() {
        ProductCategory existing = category(1L, "Kremy", "255,0,0");
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(existing));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> productCategoryService.updateCategory(1L, new ProductCategoryDTO(null, "Kremy", "x")));

        assertEquals(COLOR_INVALID, ex.getMessage());
        assertEquals("255,0,0", existing.getColor());
        verify(productCategoryRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void updateCategory_shouldSaveColorWithoutWhitespace() {
        ProductCategory existing = category(1L, "Kremy", "255,0,0");
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(productCategoryRepo.findAllByCategoryName("Kremy")).thenReturn(List.of(existing));
        when(productCategoryRepo.existsByColorAndIsDeletedFalseAndIdNot("4,5,6", 1L)).thenReturn(false);
        stubSaveReturningArgument();

        ProductCategoryDTO result = productCategoryService.updateCategory(1L, new ProductCategoryDTO(null, "Kremy", "4 , 5 , 6"));

        assertEquals("4,5,6", existing.getColor());
        assertEquals("4,5,6", result.getColor());
    }

    @Test
    void updateCategory_shouldAcquireWriteLockBeforeReadingCategory() {
        ProductCategory existing = category(1L, "Kremy", "255,0,0");
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(productCategoryRepo.findAllByCategoryName("Kremy")).thenReturn(List.of(existing));
        when(productCategoryRepo.existsByColorAndIsDeletedFalseAndIdNot("255,0,0", 1L)).thenReturn(false);
        stubSaveReturningArgument();

        productCategoryService.updateCategory(1L, new ProductCategoryDTO(null, "Kremy", "255,0,0"));

        InOrder inOrder = inOrder(productCategoryRepo);
        inOrder.verify(productCategoryRepo).acquireTransactionLock(anyLong());
        inOrder.verify(productCategoryRepo).findById(1L);
        inOrder.verify(productCategoryRepo).saveAndFlush(existing);
    }

    @Test
    void deleteCategoryById_shouldThrowConflict_whenSystemCategory() {
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(systemCategory(1L, "Produkty", "#FF0000")));

        ConflictException ex = assertThrows(ConflictException.class, () -> productCategoryService.deleteCategoryById(1L));

        assertEquals("Kategorii systemowej nie można usunąć.", ex.getMessage());
        verify(productCategoryRepo, never()).deleteById(any());
        verify(productCategoryRepo, never()).save(any());
        verifyNoInteractions(productRepo);
    }

    @Test
    void deleteCategoryById_shouldThrowConflictWithCount_whenActiveProductsAssigned() {
        when(productCategoryRepo.findById(2L)).thenReturn(Optional.of(category(2L, "Narzędzia", "#00FF00")));
        when(productRepo.countByCategoryIdAndIsDeletedFalse(2L)).thenReturn(3L);

        ConflictException ex = assertThrows(ConflictException.class, () -> productCategoryService.deleteCategoryById(2L));

        assertEquals("Kategoria ma przypisane produkty (3). Najpierw przenieś je do innej kategorii.", ex.getMessage());
        verify(productCategoryRepo, never()).deleteById(any());
        verify(productCategoryRepo, never()).save(any());
    }

    @Test
    void deleteCategoryById_shouldSoftDelete_whenOnlyArchivedProductsAssigned() {
        ProductCategory category = category(2L, "Narzędzia", "#00FF00");
        when(productCategoryRepo.findById(2L)).thenReturn(Optional.of(category));
        when(productRepo.countByCategoryIdAndIsDeletedFalse(2L)).thenReturn(0L);
        when(productRepo.countByCategoryId(2L)).thenReturn(2L);

        productCategoryService.deleteCategoryById(2L);

        assertTrue(category.getIsDeleted());
        verify(productCategoryRepo).save(category);
        verify(productCategoryRepo, never()).deleteById(any());
        verify(auditLogService).logDelete(eq("ProductCategory"), eq(2L), eq("Narzędzia"), any());
    }

    @Test
    void deleteCategoryById_shouldDeletePermanently_whenNoProducts() {
        ProductCategory category = category(2L, "Narzędzia", "#00FF00");
        when(productCategoryRepo.findById(2L)).thenReturn(Optional.of(category));
        when(productRepo.countByCategoryIdAndIsDeletedFalse(2L)).thenReturn(0L);
        when(productRepo.countByCategoryId(2L)).thenReturn(0L);

        productCategoryService.deleteCategoryById(2L);

        verify(productCategoryRepo).deleteById(2L);
        verify(productCategoryRepo, never()).save(any());
        assertFalse(category.getIsDeleted());
        verify(auditLogService).logDelete(eq("ProductCategory"), eq(2L), eq("Narzędzia"), any());
    }

    @Test
    void deleteCategoryById_shouldThrowResourceNotFound_whenCategoryHidden() {
        when(productCategoryRepo.findById(5L)).thenReturn(Optional.of(hiddenCategory(5L, "Jednorazowe", "#AAAAAA")));

        assertThrows(ResourceNotFoundException.class, () -> productCategoryService.deleteCategoryById(5L));

        verify(productCategoryRepo, never()).deleteById(any());
        verify(productCategoryRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void deleteCategoryById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(productCategoryRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> productCategoryService.deleteCategoryById(99L));
        verify(productCategoryRepo, never()).deleteById(any());
    }

    @Test
    void deleteCategoryById_shouldAcquireWriteLockBeforeReadingCategory() {
        ProductCategory category = category(2L, "Narzędzia", "#00FF00");
        when(productCategoryRepo.findById(2L)).thenReturn(Optional.of(category));
        when(productRepo.countByCategoryIdAndIsDeletedFalse(2L)).thenReturn(0L);
        when(productRepo.countByCategoryId(2L)).thenReturn(1L);

        productCategoryService.deleteCategoryById(2L);

        InOrder inOrder = inOrder(productCategoryRepo);
        inOrder.verify(productCategoryRepo).acquireTransactionLock(anyLong());
        inOrder.verify(productCategoryRepo).findById(2L);
        inOrder.verify(productCategoryRepo).save(category);
    }

    @Test
    void createCategory_shouldRestoreLowestIdHiddenCategory_whenTwoHiddenDifferOnlyByCase() {
        ProductCategory first = hiddenCategory(5L, "jednorazowe", "170,170,170");
        ProductCategory second = hiddenCategory(7L, "JEDNORAZOWE", "204,204,204");
        when(productCategoryRepo.findAllByCategoryName("Jednorazowe")).thenReturn(List.of(first, second));
        when(productCategoryRepo.existsByColorAndIsDeletedFalse("187,187,187")).thenReturn(false);
        stubSaveReturningArgument();

        ProductCategoryDTO result = assertDoesNotThrow(() -> productCategoryService.createCategory(
                new ProductCategoryDTO(null, "Jednorazowe", "187,187,187")));

        assertEquals(5L, result.getId());
        assertTrue(result.getRestored());
        assertFalse(first.getIsDeleted());
        assertTrue(second.getIsDeleted());
        verify(productCategoryRepo, times(1)).saveAndFlush(any(ProductCategory.class));
    }

    @Test
    void createCategory_shouldRestoreExactNameMatch_whenTwoHiddenDifferOnlyByCase() {
        ProductCategory first = hiddenCategory(5L, "jednorazowe", "170,170,170");
        ProductCategory second = hiddenCategory(7L, "JEDNORAZOWE", "204,204,204");
        when(productCategoryRepo.findAllByCategoryName("JEDNORAZOWE")).thenReturn(List.of(first, second));
        when(productCategoryRepo.existsByColorAndIsDeletedFalse("187,187,187")).thenReturn(false);
        stubSaveReturningArgument();

        ProductCategoryDTO result = productCategoryService.createCategory(new ProductCategoryDTO(null, "JEDNORAZOWE", "187,187,187"));

        assertEquals(7L, result.getId());
        assertFalse(second.getIsDeleted());
        assertTrue(first.getIsDeleted());
    }

    @Test
    void createCategory_shouldThrowConflict_whenActiveAndHiddenDifferOnlyByCase() {
        when(productCategoryRepo.findAllByCategoryName("kremy")).thenReturn(List.of(
                hiddenCategory(5L, "KREMY", "170,170,170"), category(6L, "Kremy", "204,204,204")));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> productCategoryService.createCategory(new ProductCategoryDTO(null, "kremy", "187,187,187")));

        assertEquals(NAME_TAKEN, ex.getMessage());
        verify(productCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void updateCategory_shouldThrowConflict_whenOtherMatchExistsBesideCurrentCategory() {
        ProductCategory current = category(1L, "Kremy", "255,0,0");
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(current));
        when(productCategoryRepo.findAllByCategoryName("KREMY")).thenReturn(List.of(current, hiddenCategory(5L, "KREMY", "170,170,170")));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> productCategoryService.updateCategory(1L, new ProductCategoryDTO(null, "KREMY", "255,0,0")));

        assertEquals(HIDDEN_NAME_TAKEN, ex.getMessage());
        verify(productCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void updateCategory_shouldThrowActiveNameConflict_whenBothActiveAndHiddenOtherCategoriesMatch() {
        ProductCategory current = category(1L, "Kremy", "255,0,0");
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(current));
        when(productCategoryRepo.findAllByCategoryName("Balsamy")).thenReturn(List.of(
                hiddenCategory(5L, "BALSAMY", "170,170,170"), category(6L, "Balsamy", "204,204,204")));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> productCategoryService.updateCategory(1L, new ProductCategoryDTO(null, "Balsamy", "255,0,0")));

        assertEquals(NAME_TAKEN, ex.getMessage());
        verify(productCategoryRepo, never()).saveAndFlush(any());
    }

    private DataIntegrityViolationException uniqueViolation(String constraint) {
        return new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"" + constraint + "\""));
    }

    @Test
    void createCategory_shouldThrowConflictNameTaken_whenNameIndexViolatedInRace() {
        when(productCategoryRepo.findAllByCategoryName("Nowa")).thenReturn(List.of());
        when(productCategoryRepo.existsByColorAndIsDeletedFalse("1,2,3")).thenReturn(false);
        when(productCategoryRepo.saveAndFlush(any(ProductCategory.class)))
                .thenThrow(uniqueViolation("uq_product_category_name_ci"));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> productCategoryService.createCategory(new ProductCategoryDTO(null, "Nowa", "1,2,3")));

        assertEquals(NAME_TAKEN, ex.getMessage());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void createCategory_shouldThrowConflictColorTaken_whenColorIndexViolatedInRace() {
        when(productCategoryRepo.findAllByCategoryName("Nowa")).thenReturn(List.of());
        when(productCategoryRepo.existsByColorAndIsDeletedFalse("1,2,3")).thenReturn(false);
        when(productCategoryRepo.saveAndFlush(any(ProductCategory.class)))
                .thenThrow(uniqueViolation("uq_product_category_color_active"));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> productCategoryService.createCategory(new ProductCategoryDTO(null, "Nowa", "1,2,3")));

        assertEquals("Ten kolor ma już inna kategoria.", ex.getMessage());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void updateCategory_shouldThrowConflictNameTaken_whenNameIndexViolatedInRace() {
        when(productCategoryRepo.findById(1L)).thenReturn(Optional.of(category(1L, "Kremy", "1,1,1")));
        when(productCategoryRepo.findAllByCategoryName("Balsamy")).thenReturn(List.of());
        when(productCategoryRepo.existsByColorAndIsDeletedFalseAndIdNot("1,1,1", 1L)).thenReturn(false);
        when(productCategoryRepo.saveAndFlush(any(ProductCategory.class)))
                .thenThrow(uniqueViolation("uq_product_category_name_ci"));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> productCategoryService.updateCategory(1L, new ProductCategoryDTO(null, "Balsamy", "1,1,1")));

        assertEquals(NAME_TAKEN, ex.getMessage());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void restoreIfHidden_shouldRestoreCategoryWithOwnColorAndAudit_whenColorFree() {
        ProductCategory hidden = hiddenCategory(5L, "Jednorazowe", "10,20,30");
        when(productCategoryRepo.findByIdAndIsDeletedTrue(5L)).thenReturn(Optional.of(hidden));
        when(productCategoryRepo.existsByColorAndIsDeletedFalse("10,20,30")).thenReturn(false);
        when(productCategoryRepo.saveAndFlush(hidden)).thenReturn(hidden);

        productCategoryService.restoreIfHidden(hidden);

        assertFalse(hidden.getIsDeleted());
        assertEquals("10,20,30", hidden.getColor());
        assertEquals("Jednorazowe", hidden.getName());
        ArgumentCaptor<ProductCategoryDTO> before = ArgumentCaptor.forClass(ProductCategoryDTO.class);
        ArgumentCaptor<ProductCategoryDTO> after = ArgumentCaptor.forClass(ProductCategoryDTO.class);
        verify(auditLogService).logUpdate(eq("ProductCategory"), eq(5L), eq("Jednorazowe"), before.capture(), after.capture());
        assertEquals("10,20,30", before.getValue().getColor());
        assertEquals("10,20,30", after.getValue().getColor());
        verify(productCategoryRepo, never()).findActiveColors();
        verify(productCategoryRepo, never()).save(any());
    }

    @Test
    void restoreIfHidden_shouldUseFirstFreePaletteColor_whenOwnColorTaken() {
        ProductCategory hidden = hiddenCategory(5L, "Jednorazowe", "10,20,30");
        when(productCategoryRepo.findByIdAndIsDeletedTrue(5L)).thenReturn(Optional.of(hidden));
        when(productCategoryRepo.existsByColorAndIsDeletedFalse("10,20,30")).thenReturn(true);
        when(productCategoryRepo.findActiveColors()).thenReturn(List.of("10,20,30", "239,68,68"));
        when(productCategoryRepo.saveAndFlush(hidden)).thenReturn(hidden);

        assertDoesNotThrow(() -> productCategoryService.restoreIfHidden(hidden));

        assertFalse(hidden.getIsDeleted());
        assertEquals("249,115,22", hidden.getColor());
        InOrder inOrder = inOrder(productCategoryRepo);
        inOrder.verify(productCategoryRepo).acquireTransactionLock(anyLong());
        inOrder.verify(productCategoryRepo).findByIdAndIsDeletedTrue(5L);
        inOrder.verify(productCategoryRepo).existsByColorAndIsDeletedFalse("10,20,30");
        inOrder.verify(productCategoryRepo).findActiveColors();
        inOrder.verify(productCategoryRepo).saveAndFlush(hidden);
        verify(productCategoryRepo, never()).save(any());
        ArgumentCaptor<ProductCategoryDTO> before = ArgumentCaptor.forClass(ProductCategoryDTO.class);
        ArgumentCaptor<ProductCategoryDTO> after = ArgumentCaptor.forClass(ProductCategoryDTO.class);
        verify(auditLogService).logUpdate(eq("ProductCategory"), eq(5L), eq("Jednorazowe"), before.capture(), after.capture());
        assertEquals("10,20,30", before.getValue().getColor());
        assertEquals("249,115,22", after.getValue().getColor());
    }

    @Test
    void restoreIfHidden_shouldGenerateColor_whenPaletteExhausted() {
        ProductCategory hidden = hiddenCategory(5L, "Jednorazowe", "10,20,30");
        when(productCategoryRepo.findByIdAndIsDeletedTrue(5L)).thenReturn(Optional.of(hidden));
        when(productCategoryRepo.existsByColorAndIsDeletedFalse("10,20,30")).thenReturn(true);
        when(productCategoryRepo.findActiveColors()).thenReturn(List.of(
                "10,20,30", "239,68,68", "249,115,22", "234,179,8", "34,197,94", "20,184,166",
                "59,130,246", "99,102,241", "168,85,247", "236,72,153", "120,113,108", "128,128,128"));
        when(productCategoryRepo.saveAndFlush(hidden)).thenReturn(hidden);

        productCategoryService.restoreIfHidden(hidden);

        assertFalse(hidden.getIsDeleted());
        assertEquals("175,203,157", hidden.getColor());
    }

    @Test
    void restoreIfHidden_shouldDoNothing_whenCategoryAlreadyRestoredByConcurrentTransaction() {
        ProductCategory staleHidden = hiddenCategory(5L, "Jednorazowe", "10,20,30");
        when(productCategoryRepo.findByIdAndIsDeletedTrue(5L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> productCategoryService.restoreIfHidden(staleHidden));

        assertEquals("10,20,30", staleHidden.getColor());
        verify(productCategoryRepo).acquireTransactionLock(anyLong());
        verify(productCategoryRepo, never()).saveAndFlush(any());
        verify(productCategoryRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void restoreIfHidden_shouldDoNothing_whenCategoryActive() {
        ProductCategory active = category(1L, "Kremy", "1,1,1");

        productCategoryService.restoreIfHidden(active);

        assertFalse(active.getIsDeleted());
        assertEquals("1,1,1", active.getColor());
        verifyNoInteractions(productCategoryRepo, auditLogService);
    }

    @Test
    void restoreIfHidden_shouldDoNothing_whenCategoryNull() {
        assertDoesNotThrow(() -> productCategoryService.restoreIfHidden(null));

        verifyNoInteractions(productCategoryRepo, auditLogService);
    }
}

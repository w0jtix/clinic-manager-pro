package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.CategoryUsageDTO;
import com.clinic.clinicmanager.DTO.ExpenseCategoryDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.ExpenseCategory;
import com.clinic.clinicmanager.repo.ExpenseCategoryRepo;
import com.clinic.clinicmanager.service.impl.ExpenseCategoryServiceImpl;
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

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseCategoryServiceImplTest {

    @Mock private ExpenseCategoryRepo expenseCategoryRepo;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private ExpenseCategoryServiceImpl expenseCategoryService;

    private ExpenseCategory category(Long id, String code, String name, String color, boolean system, boolean internal) {
        return ExpenseCategory.builder()
                .id(id).code(code).name(name).color(color).isSystem(system).isInternal(internal)
                .build();
    }

    private void saveReturnsArgumentWithId(Long id) {
        when(expenseCategoryRepo.saveAndFlush(any(ExpenseCategory.class))).thenAnswer(invocation -> {
            ExpenseCategory saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(id);
            }
            return saved;
        });
    }

    private DataIntegrityViolationException violation(String constraint) {
        return new DataIntegrityViolationException("conflict",
                new SQLException("ERROR: duplicate key value violates unique constraint \"" + constraint + "\""));
    }

    @Test
    void getCategories_returnsAllRowsInIdOrderWithFlags() {
        when(expenseCategoryRepo.findAllByOrderByIdAsc()).thenReturn(List.of(
                category(1L, "RENT", "Czynsz", "255,70,162", true, false),
                category(13L, "MIXED", "Mieszana", "150,150,150", true, true)));

        List<ExpenseCategoryDTO> result = expenseCategoryService.getCategories();

        assertThat(result).extracting(ExpenseCategoryDTO::getCode, ExpenseCategoryDTO::getSystem, ExpenseCategoryDTO::getInternal)
                .containsExactly(
                        tuple("RENT", true, false),
                        tuple("MIXED", true, true));
    }

    @Test
    void getCategoryUsage_returnsRepoUsage() {
        when(expenseCategoryRepo.findCategoryUsage()).thenReturn(List.of(new CategoryUsageDTO(5L, 2L)));

        assertThat(expenseCategoryService.getCategoryUsage())
                .extracting(CategoryUsageDTO::getCategoryId, CategoryUsageDTO::getCount)
                .containsExactly(tuple(5L, 2L));
    }

    @Test
    void createCategory_generatesHiddenCodeAndNonSystemFlags() {
        saveReturnsArgumentWithId(14L);

        ExpenseCategoryDTO result = expenseCategoryService.createCategory(new ExpenseCategoryDTO("Leasing", "99,102,241"));

        assertThat(result.getId()).isEqualTo(14L);
        assertThat(result.getCode()).hasSize(33).matches("C[0-9a-f]{32}");
        assertThat(result.getName()).isEqualTo("Leasing");
        assertThat(result.getColor()).isEqualTo("99,102,241");
        assertThat(result.getSystem()).isFalse();
        assertThat(result.getInternal()).isFalse();
        verify(auditLogService).logCreate(eq("ExpenseCategory"), eq(14L), eq("Leasing"), any(ExpenseCategoryDTO.class));
    }

    @Test
    void createCategory_ignoresCodeAndFlagsSentByClient() {
        saveReturnsArgumentWithId(14L);
        ExpenseCategoryDTO input = new ExpenseCategoryDTO("Leasing", "99,102,241");
        input.setCode("RENT");
        input.setSystem(true);
        input.setInternal(true);

        ExpenseCategoryDTO result = expenseCategoryService.createCategory(input);

        assertThat(result.getCode()).startsWith("C").isNotEqualTo("RENT");
        assertThat(result.getSystem()).isFalse();
        assertThat(result.getInternal()).isFalse();
    }

    @Test
    void createCategory_generatesDifferentCodesForEachCategory() {
        saveReturnsArgumentWithId(14L);

        String first = expenseCategoryService.createCategory(new ExpenseCategoryDTO("Leasing", "1,2,3")).getCode();
        String second = expenseCategoryService.createCategory(new ExpenseCategoryDTO("Paliwo", "4,5,6")).getCode();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void createCategory_trimsNameAndRemovesSpacesFromColor() {
        saveReturnsArgumentWithId(14L);

        ExpenseCategoryDTO result = expenseCategoryService.createCategory(new ExpenseCategoryDTO("  Leasing  ", " 99, 102 ,241 "));

        assertThat(result.getName()).isEqualTo("Leasing");
        assertThat(result.getColor()).isEqualTo("99,102,241");
    }

    @Test
    void createCategory_throwsConflict_whenNameTakenIgnoringCaseAndSpaces() {
        when(expenseCategoryRepo.findAllByCategoryName("czynsz"))
                .thenReturn(List.of(category(1L, "RENT", "Czynsz", "255,70,162", true, false)));

        assertThatThrownBy(() -> expenseCategoryService.createCategory(new ExpenseCategoryDTO(" czynsz ", "1,2,3")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Kategoria o tej nazwie już istnieje.");
        verify(expenseCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void createCategory_throwsConflict_whenColorTaken() {
        when(expenseCategoryRepo.existsByColor("255,70,162")).thenReturn(true);

        assertThatThrownBy(() -> expenseCategoryService.createCategory(new ExpenseCategoryDTO("Leasing", "255,70,162")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Ten kolor ma już inna kategoria.");
        verify(expenseCategoryRepo, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void createCategory_throwsValidation_whenNameBlank(String name) {
        assertThatThrownBy(() -> expenseCategoryService.createCategory(new ExpenseCategoryDTO(name, "1,2,3")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nazwa kategorii jest wymagana.");
        verify(expenseCategoryRepo, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"256,0,0", "1,2", "1,2,3,4", "#ff0000", "a,b,c", "-1,2,3", "1,,3", "1000,1,1"})
    void createCategory_throwsValidation_whenColorFormatInvalid(String color) {
        assertThatThrownBy(() -> expenseCategoryService.createCategory(new ExpenseCategoryDTO("Leasing", color)))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Niepoprawny format koloru.");
        verify(expenseCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void createCategory_throwsValidation_whenTrimmedNameLongerThan50() {
        assertThatThrownBy(() -> expenseCategoryService.createCategory(new ExpenseCategoryDTO(" " + "x".repeat(51) + " ", "1,2,3")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nazwa kategorii może mieć maksymalnie 50 znaków.");
        verify(expenseCategoryRepo, never()).findAllByCategoryName(anyString());
        verify(expenseCategoryRepo, never()).saveAndFlush(any());
        verify(auditLogService, never()).logCreate(anyString(), any(), anyString(), any());
    }

    @Test
    void createCategory_acceptsNameOfExactly50CharsAfterTrim() {
        String name = "x".repeat(50);
        saveReturnsArgumentWithId(14L);

        ExpenseCategoryDTO result = expenseCategoryService.createCategory(new ExpenseCategoryDTO("  " + name + "  ", "1,2,3"));

        assertThat(result.getName()).isEqualTo(name).hasSize(50);
        verify(auditLogService).logCreate(eq("ExpenseCategory"), eq(14L), eq(name), any(ExpenseCategoryDTO.class));
    }

    @Test
    void createCategory_mapsColorIndexRaceToColorConflict() {
        when(expenseCategoryRepo.saveAndFlush(any())).thenThrow(violation("uq_expense_category_color"));

        assertThatThrownBy(() -> expenseCategoryService.createCategory(new ExpenseCategoryDTO("Leasing", "1,2,3")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Ten kolor ma już inna kategoria.");
    }

    @Test
    void createCategory_mapsNameIndexRaceToNameConflict() {
        when(expenseCategoryRepo.saveAndFlush(any())).thenThrow(violation("uq_expense_category_name_ci"));

        assertThatThrownBy(() -> expenseCategoryService.createCategory(new ExpenseCategoryDTO("Leasing", "1,2,3")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Kategoria o tej nazwie już istnieje.");
    }

    @Test
    void updateCategory_renamesSystemCategoryAndKeepsCode() {
        ExpenseCategory rent = category(1L, "RENT", "Czynsz", "255,70,162", true, false);
        when(expenseCategoryRepo.findById(1L)).thenReturn(Optional.of(rent));
        saveReturnsArgumentWithId(1L);

        ExpenseCategoryDTO input = new ExpenseCategoryDTO("Najem", "1,2,3");
        input.setCode("HACK");
        ExpenseCategoryDTO result = expenseCategoryService.updateCategory(1L, input);

        assertThat(result.getCode()).isEqualTo("RENT");
        assertThat(result.getName()).isEqualTo("Najem");
        assertThat(result.getColor()).isEqualTo("1,2,3");
        assertThat(result.getSystem()).isTrue();
        assertThat(rent.getCode()).isEqualTo("RENT");
        ArgumentCaptor<ExpenseCategoryDTO> oldSnapshot = ArgumentCaptor.forClass(ExpenseCategoryDTO.class);
        verify(auditLogService).logUpdate(eq("ExpenseCategory"), eq(1L), eq("Czynsz"), oldSnapshot.capture(), any(ExpenseCategoryDTO.class));
        assertThat(oldSnapshot.getValue().getName()).isEqualTo("Czynsz");
    }

    @Test
    void updateCategory_allowsChangingCaseOfOwnName() {
        ExpenseCategory car = category(8L, "CAR", "Auto", "252,85,5", false, false);
        when(expenseCategoryRepo.findById(8L)).thenReturn(Optional.of(car));
        when(expenseCategoryRepo.findAllByCategoryName("AUTO")).thenReturn(List.of(car));
        saveReturnsArgumentWithId(8L);

        ExpenseCategoryDTO result = expenseCategoryService.updateCategory(8L, new ExpenseCategoryDTO("AUTO", "252,85,5"));

        assertThat(result.getName()).isEqualTo("AUTO");
    }

    @Test
    void updateCategory_throwsConflict_whenNameTakenByAnotherCategory() {
        when(expenseCategoryRepo.findById(8L)).thenReturn(Optional.of(category(8L, "CAR", "Auto", "252,85,5", false, false)));
        when(expenseCategoryRepo.findAllByCategoryName("Czynsz"))
                .thenReturn(List.of(category(1L, "RENT", "Czynsz", "255,70,162", true, false)));

        assertThatThrownBy(() -> expenseCategoryService.updateCategory(8L, new ExpenseCategoryDTO("Czynsz", "252,85,5")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Kategoria o tej nazwie już istnieje.");
        verify(expenseCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void updateCategory_throwsConflict_whenColorTakenByAnotherCategory() {
        when(expenseCategoryRepo.findById(8L)).thenReturn(Optional.of(category(8L, "CAR", "Auto", "252,85,5", false, false)));
        when(expenseCategoryRepo.existsByColorAndIdNot("255,70,162", 8L)).thenReturn(true);

        assertThatThrownBy(() -> expenseCategoryService.updateCategory(8L, new ExpenseCategoryDTO("Auto", "255,70,162")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Ten kolor ma już inna kategoria.");
    }

    @Test
    void updateCategory_throwsConflict_forInternalCategory() {
        when(expenseCategoryRepo.findById(13L)).thenReturn(Optional.of(category(13L, "MIXED", "Mieszana", "150,150,150", true, true)));

        assertThatThrownBy(() -> expenseCategoryService.updateCategory(13L, new ExpenseCategoryDTO("Mix", "1,2,3")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Tej kategorii nie można edytować.");
        verify(expenseCategoryRepo, never()).saveAndFlush(any());
        verify(auditLogService, never()).logUpdate(anyString(), any(), anyString(), any(), any());
    }

    @Test
    void updateCategory_throwsNotFound_whenMissing() {
        when(expenseCategoryRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseCategoryService.updateCategory(99L, new ExpenseCategoryDTO("X", "1,2,3")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateCategory_throwsValidation_whenNameBlank() {
        when(expenseCategoryRepo.findById(8L)).thenReturn(Optional.of(category(8L, "CAR", "Auto", "252,85,5", false, false)));

        assertThatThrownBy(() -> expenseCategoryService.updateCategory(8L, new ExpenseCategoryDTO(" ", "1,2,3")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void updateCategory_throwsValidation_whenTrimmedNameLongerThan50() {
        ExpenseCategory car = category(8L, "CAR", "Auto", "252,85,5", false, false);
        when(expenseCategoryRepo.findById(8L)).thenReturn(Optional.of(car));

        assertThatThrownBy(() -> expenseCategoryService.updateCategory(8L, new ExpenseCategoryDTO("y".repeat(51), "252,85,5")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nazwa kategorii może mieć maksymalnie 50 znaków.");
        assertThat(car.getName()).isEqualTo("Auto");
        verify(expenseCategoryRepo, never()).saveAndFlush(any());
    }

    @Test
    void deleteCategory_throwsConflict_forSystemCategory() {
        when(expenseCategoryRepo.findById(1L)).thenReturn(Optional.of(category(1L, "RENT", "Czynsz", "255,70,162", true, false)));

        assertThatThrownBy(() -> expenseCategoryService.deleteCategoryById(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Kategorii systemowej nie można usunąć.");
        verify(expenseCategoryRepo, never()).delete(any());
    }

    @Test
    void deleteCategory_throwsConflict_forInternalCategory() {
        when(expenseCategoryRepo.findById(13L)).thenReturn(Optional.of(category(13L, "MIXED", "Mieszana", "150,150,150", true, true)));

        assertThatThrownBy(() -> expenseCategoryService.deleteCategoryById(13L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Kategorii systemowej nie można usunąć.");
        verify(expenseCategoryRepo, never()).delete(any());
    }

    @Test
    void deleteCategory_throwsConflict_whenCategoryIsUsed() {
        when(expenseCategoryRepo.findById(8L)).thenReturn(Optional.of(category(8L, "CAR", "Auto", "252,85,5", false, false)));
        when(expenseCategoryRepo.countUsageByCode("CAR")).thenReturn(1L);

        assertThatThrownBy(() -> expenseCategoryService.deleteCategoryById(8L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Kategoria jest używana w wydatkach. Nie można jej usunąć.");
        verify(expenseCategoryRepo, never()).delete(any());
    }

    @Test
    void deleteCategory_hardDeletesUnusedCategoryAndLogsAudit() {
        ExpenseCategory car = category(8L, "CAR", "Auto", "252,85,5", false, false);
        when(expenseCategoryRepo.findById(8L)).thenReturn(Optional.of(car));
        when(expenseCategoryRepo.countUsageByCode("CAR")).thenReturn(0L);

        expenseCategoryService.deleteCategoryById(8L);

        verify(expenseCategoryRepo).delete(car);
        verify(expenseCategoryRepo).flush();
        verify(auditLogService).logDelete(eq("ExpenseCategory"), eq(8L), eq("Auto"), any(ExpenseCategoryDTO.class));
    }

    @Test
    void deleteCategory_mapsForeignKeyRaceToUsageConflict() {
        ExpenseCategory car = category(8L, "CAR", "Auto", "252,85,5", false, false);
        when(expenseCategoryRepo.findById(8L)).thenReturn(Optional.of(car));
        when(expenseCategoryRepo.countUsageByCode("CAR")).thenReturn(0L);
        doThrow(violation("fk_company_expense_category")).when(expenseCategoryRepo).flush();

        assertThatThrownBy(() -> expenseCategoryService.deleteCategoryById(8L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Kategoria jest używana w wydatkach. Nie można jej usunąć.");
        verify(auditLogService, never()).logDelete(anyString(), any(), anyString(), any());
    }

    @Test
    void deleteCategory_throwsNotFound_whenMissing() {
        when(expenseCategoryRepo.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> expenseCategoryService.deleteCategoryById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requireExistingCode_passesForKnownCode() {
        when(expenseCategoryRepo.existsByCode("RENT")).thenReturn(true);

        assertThatCode(() -> expenseCategoryService.requireExistingCode("RENT")).doesNotThrowAnyException();
    }

    @Test
    void requireExistingCode_throwsValidation_forUnknownCode() {
        when(expenseCategoryRepo.existsByCode("XYZ")).thenReturn(false);

        assertThatThrownBy(() -> expenseCategoryService.requireExistingCode("XYZ"))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nieznana kategoria wydatku.");
    }

    @Test
    void requireExistingCode_throwsValidation_forNull() {
        assertThatThrownBy(() -> expenseCategoryService.requireExistingCode(null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Nieznana kategoria wydatku.");
    }
}

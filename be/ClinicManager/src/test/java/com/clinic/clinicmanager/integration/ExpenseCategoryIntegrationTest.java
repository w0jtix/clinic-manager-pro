package com.clinic.clinicmanager.integration;

import com.clinic.clinicmanager.DTO.CategoryUsageDTO;
import com.clinic.clinicmanager.DTO.CompanyExpenseDTO;
import com.clinic.clinicmanager.DTO.CompanyExpenseItemDTO;
import com.clinic.clinicmanager.DTO.ExpenseCategoryDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.ExpenseCategory;
import com.clinic.clinicmanager.model.KsefInvoice;
import com.clinic.clinicmanager.model.KsefSeller;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.ExpenseCategoryRepo;
import com.clinic.clinicmanager.repo.KsefInvoiceRepo;
import com.clinic.clinicmanager.repo.KsefSellerRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.CompanyExpenseService;
import com.clinic.clinicmanager.service.ExpenseCategoryService;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExpenseCategoryIntegrationTest extends AbstractPostgresTest {

    private static final String IN_USE = "Kategoria jest używana w wydatkach. Nie można jej usunąć.";

    @Autowired private ExpenseCategoryService expenseCategoryService;
    @Autowired private CompanyExpenseService companyExpenseService;
    @Autowired private ExpenseCategoryRepo expenseCategoryRepo;
    @Autowired private KsefSellerRepo ksefSellerRepo;
    @Autowired private KsefInvoiceRepo ksefInvoiceRepo;
    @Autowired private EntityManager em;

    @MockBean
    private AuditLogService auditLogService;

    private ExpenseCategory byCode(String code) {
        return expenseCategoryRepo.findAll().stream()
                .filter(c -> c.getCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private ExpenseCategoryDTO createCustom(String name, String color) {
        return expenseCategoryService.createCategory(new ExpenseCategoryDTO(name, color));
    }

    private CompanyExpenseItemDTO item(String name, String category) {
        CompanyExpenseItemDTO item = new CompanyExpenseItemDTO();
        item.setName(name);
        item.setCategory(category);
        item.setQuantity(1);
        item.setVatRate(VatRate.VAT_23);
        item.setPrice(BigDecimal.valueOf(50.0));
        return item;
    }

    private void createExpense(String category, CompanyExpenseItemDTO... items) {
        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setSource("Dostawca");
        dto.setExpenseDate(LocalDate.of(2026, 10, 5));
        dto.setCategory(category);
        dto.setExpenseItems(new ArrayList<>(List.of(items)));
        companyExpenseService.createExpense(dto);
    }

    private KsefSeller seller(String defaultCategory) {
        return ksefSellerRepo.save(KsefSeller.builder()
                .nip(String.format("%010d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 10_000_000_000L)))
                .name("Sprzedawca")
                .defaultExpenseCategory(defaultCategory)
                .build());
    }

    private void ksefInvoice(String category) {
        ksefInvoiceRepo.save(KsefInvoice.builder()
                .ksefReferenceNumber("CAT-" + UUID.randomUUID())
                .invoiceNumber("FV/1")
                .invoiceType("VAT")
                .seller(seller(null))
                .issueDate(LocalDate.of(2026, 10, 1))
                .category(category)
                .netAmount(BigDecimal.valueOf(100))
                .vatAmount(BigDecimal.valueOf(23))
                .grossAmount(BigDecimal.valueOf(123))
                .build());
    }

    private Map<Long, Long> usage() {
        return expenseCategoryService.getCategoryUsage().stream()
                .collect(Collectors.toMap(CategoryUsageDTO::getCategoryId, CategoryUsageDTO::getCount));
    }

    @Test
    void getCategories_returnsSeededCategoriesOrderedById() {
        List<ExpenseCategoryDTO> categories = expenseCategoryService.getCategories();

        assertThat(categories).extracting(ExpenseCategoryDTO::getCode).startsWith(
                "RENT", "TAXES", "ZUS", "PRODUCTS", "EQUIPMENT", "SALARY", "SERVICES",
                "CAR", "TRAINING", "SUPPLIES", "UNRELATED", "OTHER", "MIXED");
        ExpenseCategoryDTO mixed = categories.get(12);
        assertThat(mixed.getSystem()).isTrue();
        assertThat(mixed.getInternal()).isTrue();
    }

    @Test
    void createCategory_leasing_getsGeneratedCodeAndNonSystemFlags() {
        ExpenseCategoryDTO created = createCustom("Leasing", "99,102,241");

        assertThat(created.getId()).isNotNull();
        assertThat(created.getCode()).startsWith("C").hasSize(33).matches("C[0-9a-f]{32}");
        assertThat(created.getSystem()).isFalse();
        assertThat(created.getInternal()).isFalse();
        assertThat(expenseCategoryService.getCategories()).last()
                .extracting(ExpenseCategoryDTO::getCode).isEqualTo(created.getCode());
    }

    @Test
    void createCategory_rejectsNameOfExistingCategoryIgnoringCaseAndSpaces() {
        assertThatThrownBy(() -> createCustom(" czynsz ", "1,2,3"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Kategoria o tej nazwie już istnieje.");
    }

    @Test
    void createCategory_rejectsColorOfExistingCategory() {
        assertThatThrownBy(() -> createCustom("Leasing", "255,70,162"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Ten kolor ma już inna kategoria.");
    }

    @Test
    void createCategory_rejectsBlankName() {
        long before = expenseCategoryRepo.count();

        assertThatThrownBy(() -> createCustom("  ", "1,2,3"))
                .isInstanceOf(ValidationException.class);
        assertThat(expenseCategoryRepo.count()).isEqualTo(before);
    }

    @Test
    void updateCategory_renamesSystemCategoryAndKeepsCode() {
        Long rentId = byCode("RENT").getId();

        ExpenseCategoryDTO updated = expenseCategoryService.updateCategory(rentId, new ExpenseCategoryDTO("Najem", "1,2,3"));
        em.flush();
        em.clear();

        assertThat(updated.getCode()).isEqualTo("RENT");
        assertThat(updated.getSystem()).isTrue();
        ExpenseCategory reloaded = expenseCategoryRepo.findById(rentId).orElseThrow();
        assertThat(reloaded.getCode()).isEqualTo("RENT");
        assertThat(reloaded.getName()).isEqualTo("Najem");
        assertThat(reloaded.getColor()).isEqualTo("1,2,3");
    }

    @Test
    void updateCategory_customCategoryKeepsGeneratedCode() {
        ExpenseCategoryDTO created = createCustom("Leasing", "99,102,241");

        ExpenseCategoryDTO updated = expenseCategoryService.updateCategory(created.getId(), new ExpenseCategoryDTO("Leasing auta", "99,102,242"));

        assertThat(updated.getCode()).isEqualTo(created.getCode());
        assertThat(updated.getName()).isEqualTo("Leasing auta");
    }

    @Test
    void updateCategory_rejectsMixed() {
        Long mixedId = byCode("MIXED").getId();

        assertThatThrownBy(() -> expenseCategoryService.updateCategory(mixedId, new ExpenseCategoryDTO("Mix", "1,2,3")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Tej kategorii nie można edytować.");
        em.clear();
        assertThat(expenseCategoryRepo.findById(mixedId).orElseThrow().getName()).isEqualTo("Mieszana");
    }

    @Test
    void deleteCategory_rejectsSystemCategories() {
        Long rentId = byCode("RENT").getId();
        Long mixedId = byCode("MIXED").getId();

        assertThatThrownBy(() -> expenseCategoryService.deleteCategoryById(rentId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Kategorii systemowej nie można usunąć.");
        assertThatThrownBy(() -> expenseCategoryService.deleteCategoryById(mixedId))
                .isInstanceOf(ConflictException.class);
        assertThat(expenseCategoryRepo.existsById(rentId)).isTrue();
        assertThat(expenseCategoryRepo.existsById(mixedId)).isTrue();
    }

    @Test
    void deleteCategory_rejectsCategoryUsedOnlyAsSellerDefault() {
        ExpenseCategoryDTO created = createCustom("Leasing", "99,102,241");
        seller(created.getCode());
        em.flush();

        assertThatThrownBy(() -> expenseCategoryService.deleteCategoryById(created.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage(IN_USE);
        assertThat(expenseCategoryRepo.existsById(created.getId())).isTrue();
    }

    @Test
    void deleteCategory_rejectsCategoryUsedOnlyOnExpenseItem() {
        ExpenseCategoryDTO created = createCustom("Leasing", "99,102,241");
        createExpense(ExpenseCategoryCode.MIXED, item("A", "CAR"), item("B", created.getCode()));
        em.flush();

        assertThatThrownBy(() -> expenseCategoryService.deleteCategoryById(created.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage(IN_USE);
    }

    @Test
    void deleteCategory_rejectsCategoryUsedOnlyOnKsefInvoice() {
        ExpenseCategoryDTO created = createCustom("Leasing", "99,102,241");
        ksefInvoice(created.getCode());
        em.flush();

        assertThatThrownBy(() -> expenseCategoryService.deleteCategoryById(created.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage(IN_USE);
    }

    @Test
    void deleteCategory_rejectsNonSystemSeededCategoryInUse() {
        createExpense("CAR", item("Paliwo", null));
        em.flush();

        assertThatThrownBy(() -> expenseCategoryService.deleteCategoryById(byCode("CAR").getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessage(IN_USE);
    }

    @Test
    void deleteCategory_hardDeletesUnusedCategory() {
        ExpenseCategoryDTO created = createCustom("Leasing", "99,102,241");

        expenseCategoryService.deleteCategoryById(created.getId());
        em.clear();

        assertThat(expenseCategoryRepo.existsById(created.getId())).isFalse();
        assertThat(expenseCategoryService.getCategories())
                .extracting(ExpenseCategoryDTO::getId)
                .doesNotContain(created.getId());
    }

    @Test
    void deleteCategory_freesNameAndColorForReuse() {
        ExpenseCategoryDTO created = createCustom("Leasing", "99,102,241");
        expenseCategoryService.deleteCategoryById(created.getId());

        ExpenseCategoryDTO again = createCustom("Leasing", "99,102,241");

        assertThat(again.getCode()).isNotEqualTo(created.getCode());
    }

    @Test
    void deleteAndUpdate_throwNotFoundForMissingId() {
        assertThatThrownBy(() -> expenseCategoryService.deleteCategoryById(999_999L))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> expenseCategoryService.updateCategory(999_999L, new ExpenseCategoryDTO("X", "1,2,3")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void usage_sumsExpenseAndKsefInvoiceForSameCategory() {
        ExpenseCategoryDTO created = createCustom("Leasing", "99,102,241");
        createExpense(created.getCode(), item("Rata", null));
        ksefInvoice(created.getCode());
        em.flush();

        assertThat(usage()).containsEntry(created.getId(), 2L);
    }

    @Test
    void usage_countsAllFourColumns() {
        ExpenseCategoryDTO created = createCustom("Leasing", "99,102,241");
        createExpense(created.getCode(), item("Rata", null));
        createExpense(ExpenseCategoryCode.MIXED, item("A", "CAR"), item("B", created.getCode()));
        ksefInvoice(created.getCode());
        seller(created.getCode());
        em.flush();

        assertThat(usage()).containsEntry(created.getId(), 4L);
    }
}

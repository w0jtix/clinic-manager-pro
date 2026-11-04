package com.clinic.clinicmanager.integration;

import com.clinic.clinicmanager.DTO.CompanyExpenseDTO;
import com.clinic.clinicmanager.DTO.CompanyExpenseItemDTO;
import com.clinic.clinicmanager.DTO.request.CompanyExpenseUpdateDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.CompanyExpense;
import com.clinic.clinicmanager.model.KsefInvoice;
import com.clinic.clinicmanager.model.KsefSeller;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.CompanyExpenseRepo;
import com.clinic.clinicmanager.repo.KsefInvoiceRepo;
import com.clinic.clinicmanager.repo.KsefSellerRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.CompanyExpenseService;
import com.clinic.clinicmanager.service.KsefInvoiceService;
import com.clinic.clinicmanager.service.KsefSellerService;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExpenseCategoryAssignmentIntegrationTest extends AbstractPostgresTest {

    private static final String UNKNOWN_CATEGORY = "Nieznana kategoria wydatku.";

    @Autowired private CompanyExpenseService companyExpenseService;
    @Autowired private KsefInvoiceService ksefInvoiceService;
    @Autowired private KsefSellerService ksefSellerService;
    @Autowired private CompanyExpenseRepo companyExpenseRepo;
    @Autowired private KsefInvoiceRepo ksefInvoiceRepo;
    @Autowired private KsefSellerRepo ksefSellerRepo;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager em;

    @MockBean
    private AuditLogService auditLogService;

    private KsefSeller seller;
    private KsefInvoice invoice;

    @BeforeEach
    void setUp() {
        seller = ksefSellerRepo.save(KsefSeller.builder().nip("5260250274").name("Hurtownia Test").build());
        invoice = ksefInvoiceRepo.save(KsefInvoice.builder()
                .ksefReferenceNumber("ASSIGN-" + UUID.randomUUID())
                .invoiceNumber("FV/1/2026")
                .invoiceType("VAT")
                .seller(seller)
                .issueDate(LocalDate.of(2026, 10, 1))
                .netAmount(BigDecimal.valueOf(100))
                .vatAmount(BigDecimal.valueOf(23))
                .grossAmount(BigDecimal.valueOf(123))
                .build());
    }

    private CompanyExpenseItemDTO item(String name, String category) {
        CompanyExpenseItemDTO item = new CompanyExpenseItemDTO();
        item.setName(name);
        item.setCategory(category);
        item.setQuantity(1);
        item.setVatRate(VatRate.VAT_23);
        item.setPrice(BigDecimal.valueOf(100.0));
        return item;
    }

    private CompanyExpenseDTO expense(String category, CompanyExpenseItemDTO... items) {
        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setSource("Dostawca A");
        dto.setExpenseDate(LocalDate.of(2026, 10, 5));
        dto.setCategory(category);
        dto.setExpenseItems(new ArrayList<>(List.of(items)));
        return dto;
    }

    private String insertCustomCategory(String name, String color) {
        String code = "C" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update("INSERT INTO expense_category (code, name, color, is_system, is_internal) VALUES (?, ?, ?, false, false)",
                code, name, color);
        return code;
    }

    @Test
    void createExpense_rejectsUnknownCategory() {
        long before = companyExpenseRepo.count();

        assertThatThrownBy(() -> companyExpenseService.createExpense(expense("XYZ", item("Pozycja", null))))
                .isInstanceOf(ValidationException.class)
                .hasMessage(UNKNOWN_CATEGORY);
        assertThat(companyExpenseRepo.count()).isEqualTo(before);
    }

    @Test
    void createExpense_rejectsUnknownMixedItemCategory() {
        assertThatThrownBy(() -> companyExpenseService.createExpense(expense(ExpenseCategoryCode.MIXED,
                item("A", "CAR"), item("B", "XYZ"))))
                .isInstanceOf(ValidationException.class)
                .hasMessage(UNKNOWN_CATEGORY);
    }

    @Test
    void createExpense_acceptsCustomCategoryAndStoresItsCode() {
        String code = insertCustomCategory("Leasing", "99,102,241");

        Long id = companyExpenseService.createExpense(expense(code, item("Rata", null))).getId();
        em.flush();
        em.clear();

        CompanyExpense saved = companyExpenseRepo.findById(id).orElseThrow();
        assertThat(saved.getCategory()).isEqualTo(code);
        assertThat(companyExpenseService.getExpenseById(id).getCategory()).isEqualTo(code);
    }

    @Test
    void createExpense_acceptsCustomCategoryOnMixedItem() {
        String code = insertCustomCategory("Leasing", "99,102,241");

        CompanyExpenseDTO saved = companyExpenseService.createExpense(expense(ExpenseCategoryCode.MIXED,
                item("A", "CAR"), item("B", code)));

        assertThat(saved.getItemCategories()).containsExactlyInAnyOrder("CAR", code);
    }

    @Test
    void createExpense_keepsForbiddenItemCategoryRule() {
        assertThatThrownBy(() -> companyExpenseService.createExpense(expense(ExpenseCategoryCode.MIXED,
                item("A", ExpenseCategoryCode.RENT), item("B", "CAR"))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Niedozwolona kategoria pozycji.");
    }

    @Test
    void updateExpense_rejectsUnknownCategory() {
        Long id = companyExpenseService.createExpense(expense("OTHER", item("Pozycja", null))).getId();
        em.flush();

        CompanyExpenseUpdateDTO update = new CompanyExpenseUpdateDTO(LocalDate.of(2026, 10, 6), "Dostawca A", "XYZ", null, null, null);

        assertThatThrownBy(() -> companyExpenseService.updateExpense(id, update))
                .isInstanceOf(ValidationException.class)
                .hasMessage(UNKNOWN_CATEGORY);
        em.clear();
        assertThat(companyExpenseRepo.findById(id).orElseThrow().getCategory()).isEqualTo("OTHER");
    }

    @Test
    void ksefSetCategory_rejectsUnknownCategory() {
        assertThatThrownBy(() -> ksefInvoiceService.setCategory(invoice.getId(), "XYZ", false))
                .isInstanceOf(ValidationException.class)
                .hasMessage(UNKNOWN_CATEGORY);
        em.flush();
        em.clear();
        assertThat(ksefInvoiceRepo.findById(invoice.getId()).orElseThrow().getCategory()).isNull();
    }

    @Test
    void ksefSetCategory_acceptsCustomCategory() {
        String code = insertCustomCategory("Leasing", "99,102,241");

        assertThat(ksefInvoiceService.setCategory(invoice.getId(), code, true).getCategory()).isEqualTo(code);
        em.flush();
        em.clear();
        assertThat(ksefInvoiceRepo.findById(invoice.getId()).orElseThrow().getCategory()).isEqualTo(code);
        assertThat(ksefSellerRepo.findById(seller.getId()).orElseThrow().getDefaultExpenseCategory()).isEqualTo(code);
    }

    @Test
    void ksefSetCategory_keepsSalaryAndProductsRules() {
        assertThatThrownBy(() -> ksefInvoiceService.setCategory(invoice.getId(), ExpenseCategoryCode.SALARY, false))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Category SALARY cannot be assigned to a KSeF invoice");
        assertThatThrownBy(() -> ksefInvoiceService.setCategory(invoice.getId(), ExpenseCategoryCode.PRODUCTS, false))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Category PRODUCTS can only be set through product verification");
    }

    @Test
    void sellerDefaultCategory_rejectsUnknownCategory() {
        assertThatThrownBy(() -> ksefSellerService.setDefaultCategory(seller.getId(), "XYZ"))
                .isInstanceOf(ValidationException.class)
                .hasMessage(UNKNOWN_CATEGORY);
        em.flush();
        em.clear();
        assertThat(ksefSellerRepo.findById(seller.getId()).orElseThrow().getDefaultExpenseCategory()).isNull();
    }

    @Test
    void sellerDefaultCategory_productsStillEnablesAutoMark() {
        ksefSellerService.setDefaultCategory(seller.getId(), ExpenseCategoryCode.PRODUCTS);
        em.flush();
        em.clear();

        KsefSeller reloaded = ksefSellerRepo.findById(seller.getId()).orElseThrow();
        assertThat(reloaded.getDefaultExpenseCategory()).isEqualTo(ExpenseCategoryCode.PRODUCTS);
        assertThat(reloaded.getAutoMarkProducts()).isTrue();
    }

    @Test
    void sellerDefaultCategory_nullClearsDefault() {
        ksefSellerService.setDefaultCategory(seller.getId(), "CAR");
        ksefSellerService.removeAutoMark(seller.getId());
        em.flush();
        em.clear();

        assertThat(ksefSellerRepo.findById(seller.getId()).orElseThrow().getDefaultExpenseCategory()).isNull();
    }
}

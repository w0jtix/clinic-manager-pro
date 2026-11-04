package com.clinic.clinicmanager.integration;

import com.clinic.clinicmanager.support.AbstractPostgresTest;

import com.clinic.clinicmanager.DTO.CompanyExpenseDTO;
import com.clinic.clinicmanager.DTO.CompanyExpenseItemDTO;
import com.clinic.clinicmanager.DTO.EmployeeSummaryDTO;
import com.clinic.clinicmanager.DTO.ProductDTO;
import com.clinic.clinicmanager.DTO.StockChangePreviewDTO;
import com.clinic.clinicmanager.DTO.StockChangeProductDTO;
import com.clinic.clinicmanager.DTO.SupplyHistoryDTO;
import com.clinic.clinicmanager.DTO.UsageRecordDTO;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.exceptions.StockChangeBlockedException;
import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.model.CompanyExpense;
import com.clinic.clinicmanager.model.CompanyExpenseItem;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.model.ProductSupplyHistory;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;
import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import com.clinic.clinicmanager.model.constants.UsageReason;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.BrandRepo;
import com.clinic.clinicmanager.repo.CompanyExpenseRepo;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.repo.ProductCategoryRepo;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.repo.ProductSupplyHistoryRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.CompanyExpenseService;
import com.clinic.clinicmanager.service.ProductService;
import com.clinic.clinicmanager.service.SupplyHistoryService;
import com.clinic.clinicmanager.service.UsageRecordService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CompanyExpenseDeleteIntegrationTest extends AbstractPostgresTest {

    @Autowired private CompanyExpenseService companyExpenseService;
    @Autowired private UsageRecordService usageRecordService;
    @Autowired private ProductService productService;
    @Autowired private SupplyHistoryService supplyHistoryService;
    @Autowired private CompanyExpenseRepo companyExpenseRepo;
    @Autowired private ProductRepo productRepo;
    @Autowired private ProductCategoryRepo productCategoryRepo;
    @Autowired private BrandRepo brandRepo;
    @Autowired private EmployeeRepo employeeRepo;
    @Autowired private ProductSupplyHistoryRepo supplyHistoryRepo;
    @Autowired private EntityManager em;

    @MockBean
    private AuditLogService auditLogService;

    private ProductCategory category;
    private Brand brand;
    private Product product;
    private Employee employee;

    @BeforeEach
    void setUp() {
        category = productCategoryRepo.save(ProductCategory.builder().name("Kremy").color("#FF0000").build());
        brand = brandRepo.save(Brand.builder().name("Nike").build());
        product = stockedProduct("Krem", 5);
        employee = employeeRepo.save(Employee.builder().name("Anna").lastName("Kowalska").drawingColor("#2563eb").build());

        UserDetailsImpl userDetails = new UserDetailsImpl(1L, "anna", "pwd", null,
                new EmployeeSummaryDTO(employee),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Product stockedProduct(String name, int supply) {
        Product saved = productRepo.save(Product.builder()
                .name(name).category(category).brand(brand).supply(supply).build());
        supplyHistoryRepo.save(ProductSupplyHistory.builder()
                .product(saved)
                .delta(supply)
                .supplyAfter(supply)
                .source(SupplyChangeSource.INITIAL_BALANCE)
                .occurredAt(LocalDateTime.now())
                .performedBy("system")
                .build());
        return saved;
    }

    private CompanyExpenseItemDTO productItem(Long productId, int quantity, String itemCategory) {
        CompanyExpenseItemDTO item = new CompanyExpenseItemDTO();
        item.setProductId(productId);
        item.setCategory(itemCategory);
        item.setQuantity(quantity);
        item.setVatRate(VatRate.VAT_23);
        item.setPrice(BigDecimal.valueOf(15.0));
        return item;
    }

    private CompanyExpenseItemDTO plainItem(String name, String itemCategory) {
        CompanyExpenseItemDTO item = new CompanyExpenseItemDTO();
        item.setName(name);
        item.setCategory(itemCategory);
        item.setQuantity(1);
        item.setVatRate(VatRate.VAT_23);
        item.setPrice(BigDecimal.valueOf(100.0));
        return item;
    }

    private Long createExpense(String expenseCategory, CompanyExpenseItemDTO... items) {
        CompanyExpenseDTO expenseDTO = new CompanyExpenseDTO();
        expenseDTO.setSource("Dostawca A");
        expenseDTO.setExpenseDate(LocalDate.now());
        expenseDTO.setCategory(expenseCategory);
        expenseDTO.setExpenseItems(new ArrayList<>(List.of(items)));
        return companyExpenseService.createExpense(expenseDTO).getId();
    }

    private void useProduct(Long productId, int quantity) {
        UsageRecordDTO usageInput = new UsageRecordDTO();
        ProductDTO productRef = new ProductDTO();
        productRef.setId(productId);
        usageInput.setProduct(productRef);
        EmployeeSummaryDTO employeeRef = new EmployeeSummaryDTO();
        employeeRef.setId(employee.getId());
        usageInput.setEmployee(employeeRef);
        usageInput.setQuantity(quantity);
        usageInput.setUsageDate(LocalDate.now());
        usageInput.setUsageReason(UsageReason.REGULAR_USAGE);
        usageRecordService.createUsageRecord(usageInput);
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private int supplyOf(Long productId) {
        return productRepo.findByIdIncludingDeleted(productId).orElseThrow().getSupply();
    }

    private List<ProductSupplyHistory> historyOldestFirst(Long productId) {
        List<ProductSupplyHistory> entries = new ArrayList<>(supplyHistoryRepo
                .findAllByProductId(productId, null, PageRequest.of(0, 50))
                .getContent());
        Collections.reverse(entries);
        return entries;
    }

    private void assertHistoryMatchesSupply(Long productId) {
        List<ProductSupplyHistory> entries = historyOldestFirst(productId);
        int runningSupply = 0;
        for (ProductSupplyHistory entry : entries) {
            runningSupply += entry.getDelta();
            assertThat(entry.getSupplyAfter()).isEqualTo(runningSupply);
        }
        assertThat(entries.get(entries.size() - 1).getSupplyAfter()).isEqualTo(supplyOf(productId));
    }

    @Test
    void deleteProductsExpense_reducesSupply_andAppendsRevertEntryWithoutTouchingEarlierHistory() {
        Long expenseId = createExpense(ExpenseCategoryCode.PRODUCTS,
                productItem(product.getId(), 3, null),
                productItem(product.getId(), 2, null));
        useProduct(product.getId(), 4);
        flushAndClear();
        assertThat(supplyOf(product.getId())).isEqualTo(6);
        List<ProductSupplyHistory> historyBefore = historyOldestFirst(product.getId());

        companyExpenseService.deleteExpenseById(expenseId);
        flushAndClear();

        assertThat(supplyOf(product.getId())).isEqualTo(1);
        assertThat(companyExpenseRepo.findById(expenseId)).isEmpty();
        List<ProductSupplyHistory> historyAfter = historyOldestFirst(product.getId());
        assertThat(historyAfter)
                .extracting(ProductSupplyHistory::getSource, ProductSupplyHistory::getDelta,
                        ProductSupplyHistory::getSupplyAfter, ProductSupplyHistory::getSourceId,
                        ProductSupplyHistory::getSourceLabel)
                .containsExactly(
                        tuple(SupplyChangeSource.INITIAL_BALANCE, 5, 5, null, null),
                        tuple(SupplyChangeSource.COMPANY_EXPENSE, 5, 10, expenseId, "Koszt: Dostawca A"),
                        tuple(SupplyChangeSource.USAGE_RECORD, -4, 6, historyBefore.get(2).getSourceId(),
                                historyBefore.get(2).getSourceLabel()),
                        tuple(SupplyChangeSource.COMPANY_EXPENSE_DELETED, -5, 1, expenseId, "Koszt: Dostawca A"));
        assertThat(historyAfter.subList(0, historyBefore.size()))
                .extracting(ProductSupplyHistory::getId, ProductSupplyHistory::getSupplyAfter)
                .containsExactlyElementsOf(historyBefore.stream()
                        .map(entry -> tuple(entry.getId(), entry.getSupplyAfter()))
                        .toList());
        assertHistoryMatchesSupply(product.getId());

        List<SupplyHistoryDTO> history = supplyHistoryService
                .getHistory(product.getId(), null, PageRequest.of(0, 50))
                .getContent();
        assertThat(history)
                .filteredOn(entry -> entry.getSource() == SupplyChangeSource.COMPANY_EXPENSE
                        || entry.getSource() == SupplyChangeSource.COMPANY_EXPENSE_DELETED)
                .hasSize(2)
                .allSatisfy(entry -> assertThat(entry.isSourceExists()).isFalse());
    }

    @Test
    void getHistory_marksExpenseEntryAsExisting_whileExpenseIsNotDeleted() {
        createExpense(ExpenseCategoryCode.PRODUCTS, productItem(product.getId(), 2, null));
        flushAndClear();

        List<SupplyHistoryDTO> history = supplyHistoryService
                .getHistory(product.getId(), null, PageRequest.of(0, 50))
                .getContent();

        assertThat(history)
                .filteredOn(entry -> entry.getSource() == SupplyChangeSource.COMPANY_EXPENSE)
                .singleElement()
                .satisfies(entry -> assertThat(entry.isSourceExists()).isTrue());
    }

    @Test
    void deleteMixedExpense_reducesSupplyOfEveryProductItem_andKeepsHistoryConsistent() {
        Product serum = stockedProduct("Serum", 1);
        Long expenseId = createExpense(ExpenseCategoryCode.MIXED,
                productItem(product.getId(), 2, ExpenseCategoryCode.PRODUCTS),
                productItem(serum.getId(), 4, ExpenseCategoryCode.PRODUCTS),
                plainItem("Papier", "SUPPLIES"));
        useProduct(product.getId(), 1);
        useProduct(serum.getId(), 1);
        flushAndClear();
        assertThat(supplyOf(product.getId())).isEqualTo(6);
        assertThat(supplyOf(serum.getId())).isEqualTo(4);

        companyExpenseService.deleteExpenseById(expenseId);
        flushAndClear();

        assertThat(companyExpenseRepo.findById(expenseId)).isEmpty();
        assertThat(supplyOf(product.getId())).isEqualTo(4);
        assertThat(supplyOf(serum.getId())).isZero();
        assertThat(historyOldestFirst(serum.getId()))
                .extracting(ProductSupplyHistory::getSource, ProductSupplyHistory::getDelta,
                        ProductSupplyHistory::getSupplyAfter)
                .containsExactly(
                        tuple(SupplyChangeSource.INITIAL_BALANCE, 1, 1),
                        tuple(SupplyChangeSource.COMPANY_EXPENSE, 4, 5),
                        tuple(SupplyChangeSource.USAGE_RECORD, -1, 4),
                        tuple(SupplyChangeSource.COMPANY_EXPENSE_DELETED, -4, 0));
        assertThat(historyOldestFirst(product.getId()))
                .extracting(ProductSupplyHistory::getSource, ProductSupplyHistory::getDelta,
                        ProductSupplyHistory::getSupplyAfter)
                .containsExactly(
                        tuple(SupplyChangeSource.INITIAL_BALANCE, 5, 5),
                        tuple(SupplyChangeSource.COMPANY_EXPENSE, 2, 7),
                        tuple(SupplyChangeSource.USAGE_RECORD, -1, 6),
                        tuple(SupplyChangeSource.COMPANY_EXPENSE_DELETED, -2, 4));
        assertHistoryMatchesSupply(serum.getId());
        assertHistoryMatchesSupply(product.getId());
    }

    @Test
    void deleteExpenseRecordedBeforeSupplyHistory_appendsRevertEntry_andKeepsHistoryConsistent() {
        Product legacyProduct = stockedProduct("Legacy", 8);
        CompanyExpense legacyExpense = CompanyExpense.builder()
                .source("Stary koszt")
                .expenseDate(LocalDate.now().minusYears(1))
                .category(ExpenseCategoryCode.PRODUCTS)
                .build();
        legacyExpense.addExpenseItem(CompanyExpenseItem.builder()
                .name(legacyProduct.getName())
                .quantity(3)
                .vatRate(VatRate.VAT_23)
                .price(BigDecimal.valueOf(15.0))
                .product(legacyProduct)
                .build());
        legacyExpense.calculateTotals();
        Long expenseId = companyExpenseRepo.save(legacyExpense).getId();
        flushAndClear();

        companyExpenseService.deleteExpenseById(expenseId);
        flushAndClear();

        assertThat(supplyOf(legacyProduct.getId())).isEqualTo(5);
        assertThat(historyOldestFirst(legacyProduct.getId()))
                .extracting(ProductSupplyHistory::getSource, ProductSupplyHistory::getDelta,
                        ProductSupplyHistory::getSupplyAfter, ProductSupplyHistory::getSourceLabel)
                .containsExactly(
                        tuple(SupplyChangeSource.INITIAL_BALANCE, 8, 8, null),
                        tuple(SupplyChangeSource.COMPANY_EXPENSE_DELETED, -3, 5, "Koszt: Stary koszt"));
        assertHistoryMatchesSupply(legacyProduct.getId());
    }

    @Test
    void deleteExpense_isBlockedWithPreviewAndChangesNothing_whenSupplyInsufficient() {
        Long expenseId = createExpense(ExpenseCategoryCode.PRODUCTS, productItem(product.getId(), 5, null));
        useProduct(product.getId(), 8);
        flushAndClear();
        List<Integer> supplyAfterBefore = historyOldestFirst(product.getId()).stream()
                .map(ProductSupplyHistory::getSupplyAfter)
                .toList();

        StockChangePreviewDTO preview = companyExpenseService.getDeletionPreview(expenseId);

        assertThat(preview.isCanApply()).isFalse();
        assertThat(preview.getProducts())
                .extracting(StockChangeProductDTO::getQuantity, StockChangeProductDTO::getCurrentSupply,
                        StockChangeProductDTO::getSupplyAfter, StockChangeProductDTO::isSufficient)
                .containsExactly(tuple(-5, 2, -3, false));

        assertThatThrownBy(() -> companyExpenseService.deleteExpenseById(expenseId))
                .isInstanceOfSatisfying(StockChangeBlockedException.class,
                        blocked -> assertThat(blocked.getPreview().isCanApply()).isFalse());
        em.clear();

        assertThat(supplyOf(product.getId())).isEqualTo(2);
        assertThat(companyExpenseRepo.findById(expenseId)).isPresent();
        assertThat(historyOldestFirst(product.getId()))
                .extracting(ProductSupplyHistory::getSupplyAfter)
                .containsExactlyElementsOf(supplyAfterBefore);
    }

    @Test
    void deleteExpense_skipsProductRemovedFromStock_andAppendsNoHistoryEntry() {
        Long expenseId = createExpense(ExpenseCategoryCode.PRODUCTS, productItem(product.getId(), 3, null));
        productService.deleteProductById(product.getId());
        flushAndClear();
        List<ProductSupplyHistory> historyBefore = historyOldestFirst(product.getId());
        assertThat(historyBefore)
                .extracting(ProductSupplyHistory::getSource)
                .containsExactly(SupplyChangeSource.INITIAL_BALANCE, SupplyChangeSource.COMPANY_EXPENSE,
                        SupplyChangeSource.PRODUCT_DELETED);

        StockChangePreviewDTO preview = companyExpenseService.getDeletionPreview(expenseId);
        assertThat(preview.isCanApply()).isTrue();
        assertThat(preview.getProducts().get(0).isSkipped()).isTrue();

        companyExpenseService.deleteExpenseById(expenseId);
        flushAndClear();

        Product removed = productRepo.findByIdIncludingDeleted(product.getId()).orElseThrow();
        assertThat(removed.getIsDeleted()).isTrue();
        assertThat(removed.getSupply()).isZero();
        assertThat(companyExpenseRepo.findById(expenseId)).isEmpty();
        assertThat(historyOldestFirst(product.getId()))
                .extracting(ProductSupplyHistory::getId, ProductSupplyHistory::getSupplyAfter)
                .containsExactlyElementsOf(historyBefore.stream()
                        .map(entry -> tuple(entry.getId(), entry.getSupplyAfter()))
                        .toList());
    }

    @Test
    void deleteExpense_withoutProducts_leavesSupplyAndHistoryUntouched() {
        Long expenseId = createExpense(ExpenseCategoryCode.RENT, plainItem("Czynsz", null));
        flushAndClear();

        assertThat(companyExpenseService.getDeletionPreview(expenseId).getProducts()).isEmpty();

        companyExpenseService.deleteExpenseById(expenseId);
        flushAndClear();

        assertThat(companyExpenseRepo.findById(expenseId)).isEmpty();
        assertThat(supplyOf(product.getId())).isEqualTo(5);
        assertThat(historyOldestFirst(product.getId())).hasSize(1);
        assertHistoryMatchesSupply(product.getId());
    }
}

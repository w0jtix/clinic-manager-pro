package com.clinic.clinicmanager.integration;

import com.clinic.clinicmanager.support.AbstractPostgresTest;

import com.clinic.clinicmanager.DTO.CompanyExpenseDTO;
import com.clinic.clinicmanager.DTO.CompanyExpenseItemDTO;
import com.clinic.clinicmanager.DTO.EmployeeSummaryDTO;
import com.clinic.clinicmanager.DTO.InventoryReportDTO;
import com.clinic.clinicmanager.DTO.InventoryReportItemDTO;
import com.clinic.clinicmanager.DTO.ProductDTO;
import com.clinic.clinicmanager.DTO.SaleDTO;
import com.clinic.clinicmanager.DTO.SaleItemDTO;
import com.clinic.clinicmanager.DTO.UsageRecordDTO;
import com.clinic.clinicmanager.DTO.VisitDTO;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.model.AppSettings;
import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.model.ProductSupplyHistory;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;
import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import com.clinic.clinicmanager.model.constants.UsageReason;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.BrandRepo;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.repo.ProductCategoryRepo;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.repo.ProductSupplyHistoryRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.CompanyExpenseService;
import com.clinic.clinicmanager.service.InventoryReportService;
import com.clinic.clinicmanager.service.UsageRecordService;
import com.clinic.clinicmanager.service.impl.VisitSaleService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SupplyHistoryControlScenarioIntegrationTest extends AbstractPostgresTest {

    @Autowired private InventoryReportService inventoryReportService;
    @Autowired private VisitSaleService visitSaleService;
    @Autowired private CompanyExpenseService companyExpenseService;
    @Autowired private UsageRecordService usageRecordService;
    @Autowired private ProductRepo productRepo;
    @Autowired private ProductCategoryRepo productCategoryRepo;
    @Autowired private BrandRepo brandRepo;
    @Autowired private EmployeeRepo employeeRepo;
    @Autowired private ProductSupplyHistoryRepo supplyHistoryRepo;
    @Autowired private EntityManager em;

    @MockBean
    private AuditLogService auditLogService;

    private Product product;
    private Employee employee;

    @BeforeEach
    void setUp() {
        ProductCategory category = productCategoryRepo.save(
                ProductCategory.builder().name("Kremy").color("#FF0000").build());
        Brand brand = brandRepo.save(Brand.builder().name("Nike").build());
        product = productRepo.save(Product.builder()
                .name("Krem").category(category).brand(brand).supply(5).build());
        employee = employeeRepo.save(Employee.builder().name("Anna").lastName("Kowalska").drawingColor("#2563eb").build());

        supplyHistoryRepo.save(ProductSupplyHistory.builder()
                .product(product)
                .delta(product.getSupply())
                .supplyAfter(product.getSupply())
                .source(SupplyChangeSource.INITIAL_BALANCE)
                .occurredAt(LocalDateTime.now())
                .performedBy("system")
                .build());

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

    private void assertSupplySumInvariant(Long productId) {
        em.flush();
        Product current = productRepo.findOneById(productId).orElseThrow();
        List<ProductSupplyHistory> entries = supplyHistoryRepo
                .findAllByProductId(productId, null, PageRequest.of(0, 50)).getContent();
        long sumOfDeltas = entries.stream().mapToLong(ProductSupplyHistory::getDelta).sum();

        assertThat(sumOfDeltas).isEqualTo(current.getSupply().longValue());
    }

    private SaleItemDTO saleItemForProduct(Long productId, double price) {
        ProductDTO productRef = new ProductDTO();
        productRef.setId(productId);
        SaleItemDTO item = new SaleItemDTO();
        item.setProduct(productRef);
        item.setPrice(BigDecimal.valueOf(price));
        return item;
    }

    @Test
    void controlScenario_reportSetsThreeThenTwoSoldThenReportDeleted_keepsSupplySumInvariant() {
        InventoryReportDTO reportInput = new InventoryReportDTO();
        ProductDTO productRef = new ProductDTO();
        productRef.setId(product.getId());
        InventoryReportItemDTO itemDTO = new InventoryReportItemDTO();
        itemDTO.setProduct(productRef);
        itemDTO.setSupplyAfter(3);
        reportInput.setItems(List.of(itemDTO));

        InventoryReportDTO savedReport = inventoryReportService.createReport(reportInput);
        assertSupplySumInvariant(product.getId());
        assertThat(productRepo.findOneById(product.getId()).orElseThrow().getSupply()).isEqualTo(3);

        VisitDTO visit = new VisitDTO();
        visit.setId(999L);
        SaleDTO sale = new SaleDTO();
        sale.setItems(new ArrayList<>(List.of(
                saleItemForProduct(product.getId(), 20.0),
                saleItemForProduct(product.getId(), 20.0))));
        visit.setSale(sale);

        visitSaleService.processSale(visit, AppSettings.builder().build(), false);
        assertSupplySumInvariant(product.getId());
        assertThat(productRepo.findOneById(product.getId()).orElseThrow().getSupply()).isEqualTo(1);

        inventoryReportService.deleteReportById(savedReport.getId());
        assertSupplySumInvariant(product.getId());

        Product finalProduct = productRepo.findOneById(product.getId()).orElseThrow();
        assertThat(finalProduct.getSupply()).isEqualTo(3);

        List<ProductSupplyHistory> entries = supplyHistoryRepo
                .findAllByProductId(product.getId(), null, PageRequest.of(0, 50))
                .getContent();
        List<Integer> deltasInInsertionOrder = entries.stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .map(ProductSupplyHistory::getDelta)
                .toList();

        assertThat(deltasInInsertionOrder).containsExactly(5, -2, -1, -1, 2);
    }

    @Test
    void multiSourceSequence_companyExpenseThenUsageThenUsageRevert_keepsSupplySumInvariant() {
        CompanyExpenseItemDTO expenseItem = new CompanyExpenseItemDTO();
        expenseItem.setProductId(product.getId());
        expenseItem.setQuantity(10);
        expenseItem.setVatRate(VatRate.VAT_23);
        expenseItem.setPrice(BigDecimal.valueOf(15.0));

        CompanyExpenseDTO expenseDTO = new CompanyExpenseDTO();
        expenseDTO.setSource("Dostawca A");
        expenseDTO.setExpenseDate(LocalDate.now());
        expenseDTO.setCategory(ExpenseCategoryCode.PRODUCTS);
        expenseDTO.setExpenseItems(new ArrayList<>(List.of(expenseItem)));

        companyExpenseService.createExpense(expenseDTO);
        assertSupplySumInvariant(product.getId());
        assertThat(productRepo.findOneById(product.getId()).orElseThrow().getSupply()).isEqualTo(15);

        UsageRecordDTO usageInput = new UsageRecordDTO();
        ProductDTO productRef = new ProductDTO();
        productRef.setId(product.getId());
        usageInput.setProduct(productRef);
        EmployeeSummaryDTO employeeRef = new EmployeeSummaryDTO();
        employeeRef.setId(employee.getId());
        usageInput.setEmployee(employeeRef);
        usageInput.setQuantity(4);
        usageInput.setUsageDate(LocalDate.now());
        usageInput.setUsageReason(UsageReason.REGULAR_USAGE);

        UsageRecordDTO savedUsage = usageRecordService.createUsageRecord(usageInput);
        assertSupplySumInvariant(product.getId());
        assertThat(productRepo.findOneById(product.getId()).orElseThrow().getSupply()).isEqualTo(11);

        usageRecordService.deleteUsageRecordById(savedUsage.getId());
        assertSupplySumInvariant(product.getId());
        assertThat(productRepo.findOneById(product.getId()).orElseThrow().getSupply()).isEqualTo(15);
    }
}

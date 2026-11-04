package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.CompanyExpenseDTO;
import com.clinic.clinicmanager.DTO.CompanyExpenseItemDTO;
import com.clinic.clinicmanager.DTO.ExchangeRateDTO;
import com.clinic.clinicmanager.DTO.ExpenseSellerDTO;
import com.clinic.clinicmanager.DTO.StockChangePreviewDTO;
import com.clinic.clinicmanager.DTO.StockChangeProductDTO;
import com.clinic.clinicmanager.DTO.request.CompanyExpenseFilterDTO;
import com.clinic.clinicmanager.DTO.request.CompanyExpenseUpdateDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ExternalServiceUnavailableException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.StockChangeBlockedException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;
import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import com.clinic.clinicmanager.model.constants.SalaryComponent;
import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.CompanyExpenseItemRepo;
import com.clinic.clinicmanager.repo.CompanyExpenseRepo;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.repo.ExchangeRateRepo;
import com.clinic.clinicmanager.repo.KsefSellerRepo;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.repo.projection.UnifiedExpenseProjection;
import com.clinic.clinicmanager.service.impl.CompanyExpenseServiceImpl;
import com.clinic.clinicmanager.service.impl.ExchangeRateServiceImpl;
import com.clinic.clinicmanager.service.impl.NbpExchangeRateClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyExpenseServiceImplTest {

    private static void assertMoney(double expected, BigDecimal actual) {
        assertEquals(0, BigDecimal.valueOf(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    @Mock CompanyExpenseRepo companyExpenseRepo;
    @Mock CompanyExpenseItemRepo companyExpenseItemRepo;
    @Mock ProductRepo productRepo;
    @Mock EmployeeRepo employeeRepo;
    @Mock KsefSellerRepo ksefSellerRepo;
    @Mock AuditLogService auditLogService;
    @Mock SupplyHistoryService supplyHistoryService;
    @Mock ExpenseCategoryService expenseCategoryService;
    @Mock ExchangeRateService exchangeRateService;
    @Mock ExchangeRateRepo exchangeRateRepo;
    @Mock NbpExchangeRateClient nbpClient;

    @InjectMocks
    CompanyExpenseServiceImpl expenseService;

    private CompanyExpense expense(Long id) {
        return CompanyExpense.builder()
                .id(id)
                .source("Dostawca A")
                .expenseDate(LocalDate.of(2025, 4, 14))
                .category(ExpenseCategoryCode.PRODUCTS)
                .build();
    }

    @Test
    void getExpenseById_shouldReturnDTO_whenFound() {
        when(companyExpenseRepo.findOneByIdWithItems(1L)).thenReturn(Optional.of(expense(1L)));

        assertEquals(1L, expenseService.getExpenseById(1L).getId());
    }

    @Test
    void getExpenseById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(companyExpenseRepo.findOneByIdWithItems(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> expenseService.getExpenseById(99L));
    }

    @Test
    void getLatestExpenseByCategory_shouldReturnDTO_whenFound() {
        when(companyExpenseRepo.findFirstByCategoryOrderByExpenseDateDescIdDesc(ExpenseCategoryCode.PRODUCTS))
                .thenReturn(Optional.of(expense(1L)));
        when(companyExpenseRepo.findOneByIdWithItems(1L))
                .thenReturn(Optional.of(expense(1L)));

        assertTrue(expenseService.getLatestExpenseByCategory(ExpenseCategoryCode.PRODUCTS).isPresent());
    }

    @Test
    void getLatestExpenseByCategory_shouldReturnEmpty_whenNotFound() {
        when(companyExpenseRepo.findFirstByCategoryOrderByExpenseDateDescIdDesc(ExpenseCategoryCode.PRODUCTS))
                .thenReturn(Optional.empty());

        assertTrue(expenseService.getLatestExpenseByCategory(ExpenseCategoryCode.PRODUCTS).isEmpty());
    }

    @Test
    void getExpenses_shouldUseYearAndMonthRange_whenBothProvided() {
        CompanyExpenseFilterDTO filter = new CompanyExpenseFilterDTO();
        filter.setYear(2025);
        filter.setMonth(4);
        when(companyExpenseRepo.findUnifiedWithFilters(any(), any(), any(), anyInt(), any()))
                .thenReturn(Page.empty());

        expenseService.getExpenses(filter, 0, 10);

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(companyExpenseRepo).findUnifiedWithFilters(
                fromCaptor.capture(), toCaptor.capture(), any(), eq(0), any());
        assertEquals(LocalDate.of(2025, 4, 1), fromCaptor.getValue());
        assertEquals(LocalDate.of(2025, 4, 30), toCaptor.getValue());
    }

    @Test
    void getExpenses_shouldUseCurrentMonthOfYear_whenOnlyYearProvided() {
        CompanyExpenseFilterDTO filter = new CompanyExpenseFilterDTO();
        filter.setYear(2025);
        when(companyExpenseRepo.findUnifiedWithFilters(any(), any(), any(), anyInt(), any()))
                .thenReturn(Page.empty());

        expenseService.getExpenses(filter, 0, 10);

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(companyExpenseRepo).findUnifiedWithFilters(
                fromCaptor.capture(), any(), any(), anyInt(), any());
        assertEquals(2025, fromCaptor.getValue().getYear());
    }

    @Test
    void getExpenses_shouldUseCurrentMonth_whenFilterIsNull() {
        when(companyExpenseRepo.findUnifiedWithFilters(any(), any(), any(), anyInt(), any()))
                .thenReturn(Page.empty());

        expenseService.getExpenses(null, 0, 10);

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(companyExpenseRepo).findUnifiedWithFilters(
                fromCaptor.capture(), any(), any(), anyInt(), any());
        assertEquals(1, fromCaptor.getValue().getDayOfMonth());
    }

    @Test
    void getExpenses_mapsUnifiedRows_forManualAndKsefSources() {
        UnifiedExpenseProjection manual = mock(UnifiedExpenseProjection.class);
        when(manual.getSourceId()).thenReturn(1L);
        when(manual.getSourceType()).thenReturn("MANUAL");
        when(manual.getSource()).thenReturn("Dostawca A");
        when(manual.getExpenseDate()).thenReturn(LocalDate.of(2025, 4, 10));
        when(manual.getCategory()).thenReturn("RENT");
        when(manual.getTotalNet()).thenReturn(new BigDecimal("100.00"));
        when(manual.getTotalVat()).thenReturn(new BigDecimal("23.00"));
        when(manual.getTotalValue()).thenReturn(new BigDecimal("123.00"));

        UnifiedExpenseProjection ksef = mock(UnifiedExpenseProjection.class);
        when(ksef.getSourceId()).thenReturn(7L);
        when(ksef.getSourceType()).thenReturn("KSEF");
        when(ksef.getSource()).thenReturn("Seller Sp. z o.o.");
        when(ksef.getExpenseDate()).thenReturn(LocalDate.of(2025, 4, 5));
        when(ksef.getCategory()).thenReturn("PRODUCTS");
        when(ksef.getTotalNet()).thenReturn(new BigDecimal("200.00"));
        when(ksef.getTotalVat()).thenReturn(new BigDecimal("46.00"));
        when(ksef.getTotalValue()).thenReturn(new BigDecimal("246.00"));

        CompanyExpenseFilterDTO filter = new CompanyExpenseFilterDTO();
        filter.setYear(2025);
        filter.setMonth(4);
        when(companyExpenseRepo.findUnifiedWithFilters(any(), any(), any(), anyInt(), any()))
                .thenReturn(new PageImpl<>(List.of(manual, ksef), PageRequest.of(0, 10), 2));

        Page<CompanyExpenseDTO> result = expenseService.getExpenses(filter, 0, 10);

        assertEquals(2, result.getTotalElements());
        CompanyExpenseDTO manualDto = result.getContent().get(0);
        assertEquals("MANUAL", manualDto.getSourceType());
        assertEquals(ExpenseCategoryCode.RENT, manualDto.getCategory());
        assertMoney(123.0, manualDto.getTotalValue());

        CompanyExpenseDTO ksefDto = result.getContent().get(1);
        assertEquals("KSEF", ksefDto.getSourceType());
        assertEquals(ExpenseCategoryCode.PRODUCTS, ksefDto.getCategory());
        assertMoney(246.0, ksefDto.getTotalValue());
    }

    @Test
    void getExpenses_mapsSellerId_forKsefRowsAndLeavesItNullForManual() {
        UnifiedExpenseProjection manual = mock(UnifiedExpenseProjection.class);
        when(manual.getSourceId()).thenReturn(1L);
        when(manual.getSourceType()).thenReturn("MANUAL");
        when(manual.getSellerId()).thenReturn(null);

        UnifiedExpenseProjection ksef = mock(UnifiedExpenseProjection.class);
        when(ksef.getSourceId()).thenReturn(7L);
        when(ksef.getSourceType()).thenReturn("KSEF");
        when(ksef.getSellerId()).thenReturn(42L);

        Page<UnifiedExpenseProjection> page =
                new PageImpl<>(List.of(manual, ksef), PageRequest.of(0, 10), 2);
        CompanyExpenseFilterDTO filter = new CompanyExpenseFilterDTO();
        filter.setYear(2025);
        filter.setMonth(4);
        when(companyExpenseRepo.findUnifiedWithFilters(any(), any(), any(), anyInt(), any()))
                .thenReturn(page);

        Page<CompanyExpenseDTO> result = expenseService.getExpenses(filter, 0, 10);

        assertNull(result.getContent().get(0).getSellerId());
        assertEquals(42L, result.getContent().get(1).getSellerId());
    }

    @Test
    void getExpensePreview_shouldCalculateTotalsAndSetOnDTO() {
        CompanyExpenseItemDTO item = new CompanyExpenseItemDTO();
        item.setPrice(BigDecimal.valueOf(100.0));
        item.setQuantity(1);
        item.setVatRate(VatRate.VAT_23);

        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setExpenseItems(new ArrayList<>(List.of(item)));

        CompanyExpenseDTO result = expenseService.getExpensePreview(dto);

        assertNotNull(result.getTotalValue());
        assertNotNull(result.getTotalNet());
        assertNotNull(result.getTotalVat());
    }

    @Test
    void createExpense_shouldSaveWithManualItems_whenNoOrderId() {
        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setSource("Dostawca A");
        dto.setExpenseDate(LocalDate.of(2025, 4, 14));
        dto.setCategory(ExpenseCategoryCode.PRODUCTS);
        CompanyExpenseItemDTO itemDTO = new CompanyExpenseItemDTO();
        itemDTO.setName("Rękawiczki");
        itemDTO.setQuantity(1);
        itemDTO.setVatRate(VatRate.VAT_23);
        itemDTO.setPrice(BigDecimal.valueOf(15.0));
        dto.setExpenseItems(new ArrayList<>(List.of(itemDTO)));

        when(companyExpenseRepo.save(any())).thenReturn(expense(1L));

        CompanyExpenseDTO result = expenseService.createExpense(dto);

        assertEquals(1L, result.getId());
        verify(auditLogService).logCreate(eq("CompanyExpense"), eq(1L), anyString(), any());
    }

    @Test
    void createExpense_shouldIncreaseProductSupplyAndUseProductName_whenProductsCategory() {
        Product product = Product.builder().id(5L).name("Krem do stóp").supply(10).build();
        when(productRepo.findByIdNotDeleted(5L)).thenReturn(Optional.of(product));
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyExpenseItemDTO itemDTO = new CompanyExpenseItemDTO();
        itemDTO.setProductId(5L);
        itemDTO.setQuantity(3);
        itemDTO.setVatRate(VatRate.VAT_23);
        itemDTO.setPrice(BigDecimal.valueOf(20.0));

        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setSource("Dostawca A");
        dto.setExpenseDate(LocalDate.of(2025, 4, 14));
        dto.setCategory(ExpenseCategoryCode.PRODUCTS);
        dto.setExpenseItems(new ArrayList<>(List.of(itemDTO)));

        CompanyExpenseDTO result = expenseService.createExpense(dto);

        assertEquals(13, product.getSupply());
        verify(productRepo).save(product);
        assertEquals("Krem do stóp", result.getExpenseItems().get(0).getName());
        assertEquals(5L, result.getExpenseItems().get(0).getProductId());
    }

    @Test
    void createExpense_shouldNotTouchSupply_whenNonProductsCategory() {
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyExpenseItemDTO itemDTO = new CompanyExpenseItemDTO();
        itemDTO.setProductId(5L);
        itemDTO.setQuantity(3);
        itemDTO.setVatRate(VatRate.VAT_23);
        itemDTO.setPrice(BigDecimal.valueOf(20.0));
        itemDTO.setName("Czynsz");

        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setSource("Wynajmujący");
        dto.setExpenseDate(LocalDate.of(2025, 4, 14));
        dto.setCategory(ExpenseCategoryCode.RENT);
        dto.setExpenseItems(new ArrayList<>(List.of(itemDTO)));

        expenseService.createExpense(dto);

        verify(productRepo, never()).findByIdNotDeleted(any());
        verify(productRepo, never()).save(any());
    }

    @Test
    void createExpense_shouldAssignEmployeeAndCarrySalaryComponents_whenSalaryCategory() {
        Employee employee = Employee.builder().id(7L).name("Anna").lastName("Kowalska").build();
        when(employeeRepo.findOneById(7L)).thenReturn(Optional.of(employee));
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyExpenseItemDTO base = new CompanyExpenseItemDTO();
        base.setName("Wynagrodzenie podstawowe");
        base.setQuantity(1);
        base.setVatRate(VatRate.VAT_0);
        base.setPrice(BigDecimal.valueOf(4000.0));
        base.setSalaryComponent(SalaryComponent.BASE);

        CompanyExpenseItemDTO bonus = new CompanyExpenseItemDTO();
        bonus.setName("Premia od usług");
        bonus.setQuantity(1);
        bonus.setVatRate(VatRate.VAT_0);
        bonus.setPrice(BigDecimal.valueOf(1000.0));
        bonus.setSalaryComponent(SalaryComponent.BONUS);

        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setSource("Wypłata 07/2026");
        dto.setExpenseDate(LocalDate.of(2026, 8, 5));
        dto.setCategory(ExpenseCategoryCode.SALARY);
        dto.setEmployeeId(7L);
        dto.setExpenseItems(new ArrayList<>(List.of(base, bonus)));

        CompanyExpenseDTO result = expenseService.createExpense(dto);

        assertEquals(7L, result.getEmployeeId());
        assertEquals(SalaryComponent.BASE, result.getExpenseItems().get(0).getSalaryComponent());
        assertEquals(SalaryComponent.BONUS, result.getExpenseItems().get(1).getSalaryComponent());
    }

    @Test
    void createExpense_shouldIgnoreSalaryComponentAndEmployee_whenNonSalaryCategory() {
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyExpenseItemDTO itemDTO = new CompanyExpenseItemDTO();
        itemDTO.setName("Czynsz");
        itemDTO.setQuantity(1);
        itemDTO.setVatRate(VatRate.VAT_23);
        itemDTO.setPrice(BigDecimal.valueOf(2000.0));
        itemDTO.setSalaryComponent(SalaryComponent.BASE);

        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setSource("Wynajmujący");
        dto.setExpenseDate(LocalDate.of(2026, 8, 5));
        dto.setCategory(ExpenseCategoryCode.RENT);
        dto.setEmployeeId(7L);
        dto.setExpenseItems(new ArrayList<>(List.of(itemDTO)));

        CompanyExpenseDTO result = expenseService.createExpense(dto);

        assertNull(result.getEmployeeId());
        assertNull(result.getExpenseItems().get(0).getSalaryComponent());
        verify(employeeRepo, never()).findOneById(any());
    }

    @Test
    void getSalaryHistory_shouldReturnMappedExpenses_orderedByRepo() {
        Employee employee = Employee.builder().id(7L).name("Anna").lastName("Kowalska").build();
        when(employeeRepo.findOneById(7L)).thenReturn(Optional.of(employee));

        CompanyExpense payout = CompanyExpense.builder()
                .id(1L).source("Wypłata 07/2026")
                .expenseDate(LocalDate.of(2026, 8, 5))
                .category(ExpenseCategoryCode.SALARY)
                .employee(employee)
                .build();
        when(companyExpenseRepo.findByCategoryAndEmployeeIdOrderByExpenseDateAsc(ExpenseCategoryCode.SALARY, 7L))
                .thenReturn(List.of(payout));

        List<CompanyExpenseDTO> result = expenseService.getSalaryHistory(7L);

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getEmployeeId());
        assertEquals(ExpenseCategoryCode.SALARY, result.get(0).getCategory());
    }

    @Test
    void getSalaryHistory_shouldThrowResourceNotFoundException_whenEmployeeMissing() {
        when(employeeRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> expenseService.getSalaryHistory(99L));
        verify(companyExpenseRepo, never()).findByCategoryAndEmployeeIdOrderByExpenseDateAsc(any(), any());
    }

    @Test
    void deleteExpenseById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(companyExpenseRepo.findOneByIdWithItems(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> expenseService.deleteExpenseById(99L));
    }

    @Test
    void createExpense_shouldRecordSupplyHistoryOnce_aggregatingDuplicateProductItems() {
        Product product = Product.builder().id(5L).name("Krem do stóp").supply(10).build();
        when(productRepo.findByIdNotDeleted(5L)).thenReturn(Optional.of(product));
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> {
            CompanyExpense expense = invocation.getArgument(0);
            expense.setId(1L);
            return expense;
        });

        CompanyExpenseItemDTO item1 = new CompanyExpenseItemDTO();
        item1.setProductId(5L);
        item1.setQuantity(3);
        item1.setVatRate(VatRate.VAT_23);
        item1.setPrice(BigDecimal.valueOf(20.0));

        CompanyExpenseItemDTO item2 = new CompanyExpenseItemDTO();
        item2.setProductId(5L);
        item2.setQuantity(2);
        item2.setVatRate(VatRate.VAT_23);
        item2.setPrice(BigDecimal.valueOf(20.0));

        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setSource("Dostawca A");
        dto.setExpenseDate(LocalDate.of(2025, 4, 14));
        dto.setCategory(ExpenseCategoryCode.PRODUCTS);
        dto.setExpenseItems(new ArrayList<>(List.of(item1, item2)));

        expenseService.createExpense(dto);

        verify(supplyHistoryService, times(1)).record(product, 5, SupplyChangeSource.COMPANY_EXPENSE,
                1L, "Koszt: Dostawca A");
    }

    @Test
    void createExpense_shouldNotRecordSupplyHistory_whenNonProductsCategory() {
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyExpenseItemDTO itemDTO = new CompanyExpenseItemDTO();
        itemDTO.setName("Czynsz");
        itemDTO.setQuantity(1);
        itemDTO.setVatRate(VatRate.VAT_23);
        itemDTO.setPrice(BigDecimal.valueOf(2000.0));

        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setSource("Wynajmujący");
        dto.setExpenseDate(LocalDate.of(2025, 4, 14));
        dto.setCategory(ExpenseCategoryCode.RENT);
        dto.setExpenseItems(new ArrayList<>(List.of(itemDTO)));

        expenseService.createExpense(dto);

        verifyNoInteractions(supplyHistoryService);
    }

    @Test
    void deleteExpenseById_shouldDeleteAndAudit() {
        CompanyExpense existing = CompanyExpense.builder()
                .id(1L)
                .source("Dostawca A")
                .expenseDate(LocalDate.of(2025, 4, 14))
                .category(ExpenseCategoryCode.RENT)
                .build();
        when(companyExpenseRepo.findOneByIdWithItems(1L)).thenReturn(Optional.of(existing));

        expenseService.deleteExpenseById(1L);

        verify(companyExpenseRepo).delete(existing);
        verify(auditLogService).logDelete(eq("CompanyExpense"), eq(1L), anyString(), any());
        verifyNoInteractions(productRepo, supplyHistoryService);
    }

    private CompanyExpenseDTO rentExpenseDTO(ExpenseSellerDTO seller) {
        CompanyExpenseItemDTO itemDTO = new CompanyExpenseItemDTO();
        itemDTO.setName("Czynsz");
        itemDTO.setQuantity(1);
        itemDTO.setVatRate(VatRate.VAT_23);
        itemDTO.setPrice(BigDecimal.valueOf(2000.0));

        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setSource("Wpisane ręcznie");
        dto.setExpenseDate(LocalDate.of(2026, 9, 1));
        dto.setCategory(ExpenseCategoryCode.RENT);
        dto.setExpenseItems(new ArrayList<>(List.of(itemDTO)));
        dto.setSeller(seller);
        return dto;
    }

    private ExpenseSellerDTO sellerInput(String nip, String name, String address) {
        return new ExpenseSellerDTO(null, nip, name, address);
    }

    @Test
    void createExpense_shouldCreateAndLinkNewSeller_whenNipUnknown() {
        when(ksefSellerRepo.findByNip("5261040828")).thenReturn(Optional.empty());
        when(ksefSellerRepo.save(any(KsefSeller.class))).thenAnswer(invocation -> {
            KsefSeller seller = invocation.getArgument(0);
            seller.setId(11L);
            return seller;
        });
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyExpenseDTO result = expenseService.createExpense(
                rentExpenseDTO(sellerInput(" 526-104-08 28 ", "  Wynajem Sp. z o.o. ", " ul. Prosta 1, Warszawa ")));

        ArgumentCaptor<KsefSeller> captor = ArgumentCaptor.forClass(KsefSeller.class);
        verify(ksefSellerRepo).save(captor.capture());
        assertEquals("5261040828", captor.getValue().getNip());
        assertEquals("Wynajem Sp. z o.o.", captor.getValue().getName());
        assertEquals("ul. Prosta 1, Warszawa", captor.getValue().getAddress());

        assertEquals("Wynajem Sp. z o.o.", result.getSource());
        assertEquals(11L, result.getSellerId());
        assertEquals(11L, result.getSeller().getId());
        assertEquals("5261040828", result.getSeller().getNip());
        assertEquals("ul. Prosta 1, Warszawa", result.getSeller().getAddress());
        verify(auditLogService).logCreate(eq("KsefSeller"), eq(11L), eq("Wynajem Sp. z o.o."), any());
        verify(auditLogService).logCreate(eq("CompanyExpense"), any(), anyString(), any());
    }

    @Test
    void createExpense_shouldLinkExistingSellerWithoutModifyingIt_andUseItsNameAsSource() {
        KsefSeller existing = KsefSeller.builder()
                .id(3L).nip("5261040828").name("Dostawca KSeF").address("Stary adres")
                .email("a@b.pl").phone("123").build();
        when(ksefSellerRepo.findByNip("5261040828")).thenReturn(Optional.of(existing));
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyExpenseDTO result = expenseService.createExpense(
                rentExpenseDTO(sellerInput("5261040828", "Inna nazwa", "Inny adres")));

        assertEquals("Dostawca KSeF", result.getSource());
        assertEquals("Dostawca KSeF", result.getSeller().getName());
        assertEquals("Stary adres", result.getSeller().getAddress());
        assertEquals("Dostawca KSeF", existing.getName());
        assertEquals("Stary adres", existing.getAddress());
        assertEquals("a@b.pl", existing.getEmail());
        verify(ksefSellerRepo, never()).save(any());
        verify(auditLogService, never()).logCreate(eq("KsefSeller"), any(), any(), any());
    }

    @Test
    void createExpense_shouldKeepRequestSource_whenExistingSellerHasNoName() {
        KsefSeller existing = KsefSeller.builder().id(3L).nip("5261040828").build();
        when(ksefSellerRepo.findByNip("5261040828")).thenReturn(Optional.of(existing));
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyExpenseDTO result = expenseService.createExpense(
                rentExpenseDTO(sellerInput("5261040828", null, null)));

        assertEquals("Wpisane ręcznie", result.getSource());
        assertEquals(3L, result.getSellerId());
    }

    @Test
    void createExpense_shouldThrowValidationException_whenNipChecksumInvalid() {
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(
                rentExpenseDTO(sellerInput("1234567890", "Firma", "Adres"))));

        assertEquals("Niepoprawny NIP lub numer VAT UE sprzedawcy.", ex.getMessage());
        verifyNoInteractions(ksefSellerRepo);
        verify(companyExpenseRepo, never()).save(any());
    }

    @Test
    void createExpense_shouldCreateNewSellerWithNormalizedForeignVatId() {
        when(ksefSellerRepo.findByNip("DE123456789")).thenReturn(Optional.empty());
        when(ksefSellerRepo.save(any(KsefSeller.class))).thenAnswer(invocation -> {
            KsefSeller seller = invocation.getArgument(0);
            seller.setId(12L);
            return seller;
        });
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyExpenseDTO result = expenseService.createExpense(
                rentExpenseDTO(sellerInput("de 123456789", "Lieferant GmbH", "Hauptstr. 1, Berlin")));

        ArgumentCaptor<KsefSeller> captor = ArgumentCaptor.forClass(KsefSeller.class);
        verify(ksefSellerRepo).save(captor.capture());
        assertEquals("DE123456789", captor.getValue().getNip());
        assertEquals("Lieferant GmbH", captor.getValue().getName());
        assertEquals(12L, result.getSellerId());
        assertEquals("DE123456789", result.getSeller().getNip());
        verify(auditLogService).logCreate(eq("KsefSeller"), eq(12L), eq("Lieferant GmbH"), any());
    }

    @Test
    void createExpense_shouldLinkExistingForeignSeller_withoutSavingNewSeller() {
        KsefSeller existing = KsefSeller.builder()
                .id(4L).nip("DE123456789").name("Lieferant GmbH").address("Hauptstr. 1, Berlin").build();
        when(ksefSellerRepo.findByNip("DE123456789")).thenReturn(Optional.of(existing));
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyExpenseDTO result = expenseService.createExpense(
                rentExpenseDTO(sellerInput("DE123456789", null, null)));

        assertEquals(4L, result.getSellerId());
        assertEquals("Lieferant GmbH", result.getSource());
        assertEquals("DE123456789", result.getSeller().getNip());
        verify(ksefSellerRepo, never()).save(any());
        verify(auditLogService, never()).logCreate(eq("KsefSeller"), any(), any(), any());
    }

    @Test
    void createExpense_shouldThrowValidationException_whenTaxIdHasInvalidCharacters() {
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(
                rentExpenseDTO(sellerInput("XX12#", "Firma", "Adres"))));

        assertEquals("Niepoprawny NIP lub numer VAT UE sprzedawcy.", ex.getMessage());
        verifyNoInteractions(ksefSellerRepo);
        verify(companyExpenseRepo, never()).save(any());
    }

    @Test
    void createExpense_shouldThrowValidationException_whenPrefixedPolishNipChecksumInvalid() {
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(
                rentExpenseDTO(sellerInput("PL1234567890", "Firma", "Adres"))));

        assertEquals("Niepoprawny NIP lub numer VAT UE sprzedawcy.", ex.getMessage());
        verifyNoInteractions(ksefSellerRepo);
    }

    @Test
    void createExpense_shouldLookUpAndSavePolishSellerWithoutPrefix_whenPlPrefixGiven() {
        when(ksefSellerRepo.findByNip("5261040828")).thenReturn(Optional.empty());
        when(ksefSellerRepo.save(any(KsefSeller.class))).thenAnswer(invocation -> {
            KsefSeller seller = invocation.getArgument(0);
            seller.setId(13L);
            return seller;
        });
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        expenseService.createExpense(rentExpenseDTO(sellerInput("PL 526-104-08-28", "Firma", "Adres")));

        verify(ksefSellerRepo).findByNip("5261040828");
        ArgumentCaptor<KsefSeller> captor = ArgumentCaptor.forClass(KsefSeller.class);
        verify(ksefSellerRepo).save(captor.capture());
        assertEquals("5261040828", captor.getValue().getNip());
    }

    @Test
    void createExpense_shouldLinkExistingPolishSeller_whenPlPrefixGiven() {
        KsefSeller existing = KsefSeller.builder().id(3L).nip("5261040828").name("Dostawca KSeF").build();
        when(ksefSellerRepo.findByNip("5261040828")).thenReturn(Optional.of(existing));
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyExpenseDTO result = expenseService.createExpense(
                rentExpenseDTO(sellerInput("pl5261040828", null, null)));

        assertEquals(3L, result.getSellerId());
        verify(ksefSellerRepo, never()).save(any());
    }

    @Test
    void createExpense_shouldThrowValidationException_whenNipHasWrongLength() {
        assertThrows(ValidationException.class, () -> expenseService.createExpense(
                rentExpenseDTO(sellerInput("526104082", "Firma", "Adres"))));

        verifyNoInteractions(ksefSellerRepo);
    }

    @Test
    void createExpense_shouldThrowValidationException_whenNewNipWithoutName() {
        when(ksefSellerRepo.findByNip("5261040828")).thenReturn(Optional.empty());

        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(
                rentExpenseDTO(sellerInput("5261040828", "   ", "Adres"))));

        assertEquals("Nazwa sprzedawcy jest wymagana dla nowego NIP.", ex.getMessage());
        verify(ksefSellerRepo, never()).save(any());
        verify(companyExpenseRepo, never()).save(any());
    }

    @Test
    void createExpense_shouldThrowValidationException_whenNewNipWithoutAddress() {
        when(ksefSellerRepo.findByNip("5261040828")).thenReturn(Optional.empty());

        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(
                rentExpenseDTO(sellerInput("5261040828", "Firma", null))));

        assertEquals("Adres sprzedawcy jest wymagany dla nowego NIP.", ex.getMessage());
        verify(ksefSellerRepo, never()).save(any());
    }

    @Test
    void createExpense_shouldBehaveAsBefore_whenSellerNipBlank() {
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyExpenseDTO result = expenseService.createExpense(
                rentExpenseDTO(sellerInput("  ", "Firma", "Adres")));

        assertEquals("Wpisane ręcznie", result.getSource());
        assertNull(result.getSeller());
        assertNull(result.getSellerId());
        verifyNoInteractions(ksefSellerRepo);
    }

    @Test
    void createExpense_shouldBehaveAsBefore_whenSellerAbsent() {
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyExpenseDTO result = expenseService.createExpense(rentExpenseDTO(null));

        assertEquals("Wpisane ręcznie", result.getSource());
        assertNull(result.getSeller());
        verifyNoInteractions(ksefSellerRepo);
    }

    @Test
    void createExpense_shouldIgnoreSeller_whenZusCategory() {
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CompanyExpenseDTO dto = rentExpenseDTO(sellerInput("1234567890", "ZUS", "Adres"));
        dto.setCategory(ExpenseCategoryCode.ZUS);
        dto.setSource("ZUS 09/2026");

        CompanyExpenseDTO result = expenseService.createExpense(dto);

        assertEquals("ZUS 09/2026", result.getSource());
        assertNull(result.getSeller());
        verifyNoInteractions(ksefSellerRepo);
    }

    private void assertItemValidation(String expectedMessage, CompanyExpenseDTO dto) {
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(dto));

        assertEquals(expectedMessage, ex.getMessage());
        verifyNoInteractions(companyExpenseRepo, productRepo, supplyHistoryService, ksefSellerRepo,
                employeeRepo, auditLogService);
    }

    private CompanyExpenseDTO rentExpenseWithItem(Consumer<CompanyExpenseItemDTO> change) {
        CompanyExpenseDTO dto = rentExpenseDTO(sellerInput("5261040828", "Firma", "Adres"));
        change.accept(dto.getExpenseItems().get(0));
        return dto;
    }

    @Test
    void createExpense_shouldThrowValidationException_whenItemsEmpty() {
        CompanyExpenseDTO dto = rentExpenseDTO(sellerInput("5261040828", "Firma", "Adres"));
        dto.setExpenseItems(new ArrayList<>());

        assertItemValidation("Koszt musi mieć co najmniej jedną pozycję.", dto);
    }

    @Test
    void createExpense_shouldThrowValidationException_whenItemsNull() {
        CompanyExpenseDTO dto = rentExpenseDTO(null);
        dto.setExpenseItems(null);

        assertItemValidation("Koszt musi mieć co najmniej jedną pozycję.", dto);
    }

    @Test
    void createExpense_shouldThrowValidationException_whenItemQuantityNull() {
        assertItemValidation("Ilość pozycji musi wynosić co najmniej 1.",
                rentExpenseWithItem(item -> item.setQuantity(null)));
    }

    @Test
    void createExpense_shouldThrowValidationException_whenItemQuantityZero() {
        assertItemValidation("Ilość pozycji musi wynosić co najmniej 1.",
                rentExpenseWithItem(item -> item.setQuantity(0)));
    }

    @Test
    void createExpense_shouldThrowValidationException_whenItemQuantityNegative() {
        assertItemValidation("Ilość pozycji musi wynosić co najmniej 1.",
                rentExpenseWithItem(item -> item.setQuantity(-2)));
    }

    @Test
    void createExpense_shouldThrowValidationException_whenItemPriceNull() {
        assertItemValidation("Cena pozycji nie może być ujemna.",
                rentExpenseWithItem(item -> item.setPrice(null)));
    }

    @Test
    void createExpense_shouldThrowValidationException_whenItemPriceNegative() {
        assertItemValidation("Cena pozycji nie może być ujemna.",
                rentExpenseWithItem(item -> item.setPrice(new BigDecimal("-0.01"))));
    }

    @Test
    void createExpense_shouldThrowValidationException_whenItemVatRateNull() {
        assertItemValidation("Pozycja musi mieć stawkę VAT.",
                rentExpenseWithItem(item -> item.setVatRate(null)));
    }

    @Test
    void createExpense_shouldNotTouchProducts_whenLaterItemInvalid() {
        CompanyExpenseItemDTO product = new CompanyExpenseItemDTO();
        product.setProductId(5L);
        product.setQuantity(3);
        product.setVatRate(VatRate.VAT_23);
        product.setPrice(BigDecimal.valueOf(20.0));

        CompanyExpenseItemDTO invalid = new CompanyExpenseItemDTO();
        invalid.setProductId(6L);
        invalid.setQuantity(0);
        invalid.setVatRate(VatRate.VAT_23);
        invalid.setPrice(BigDecimal.valueOf(20.0));

        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setSource("Dostawca A");
        dto.setExpenseDate(LocalDate.of(2025, 4, 14));
        dto.setCategory(ExpenseCategoryCode.PRODUCTS);
        dto.setExpenseItems(new ArrayList<>(List.of(product, invalid)));

        assertItemValidation("Ilość pozycji musi wynosić co najmniej 1.", dto);
    }

    @Test
    void createExpense_shouldValidateItemsBeforeMixedRules() {
        CompanyExpenseItemDTO noVat = mixedItem("Paliwo", "CAR", null, 200.0);
        noVat.setVatRate(null);

        assertItemValidation("Pozycja musi mieć stawkę VAT.", mixedExpenseDTO(
                mixedItem("Papier", "SUPPLIES", null, 10.0), noVat));
    }

    @Test
    void createExpense_shouldAcceptZeroPrice_whenSalaryTemplateItems() {
        Employee employee = Employee.builder().id(7L).name("Anna").lastName("Kowalska").build();
        when(employeeRepo.findOneById(7L)).thenReturn(Optional.of(employee));
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyExpenseItemDTO base = new CompanyExpenseItemDTO();
        base.setName("Wynagrodzenie podstawowe");
        base.setQuantity(1);
        base.setVatRate(VatRate.VAT_NP);
        base.setPrice(BigDecimal.valueOf(4000.0));
        base.setSalaryComponent(SalaryComponent.BASE);

        CompanyExpenseItemDTO bonus = new CompanyExpenseItemDTO();
        bonus.setName("Premia od usług");
        bonus.setQuantity(1);
        bonus.setVatRate(VatRate.VAT_NP);
        bonus.setPrice(BigDecimal.ZERO);
        bonus.setSalaryComponent(SalaryComponent.BONUS);

        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setSource("Wypłata za Wrzesień 2026");
        dto.setExpenseDate(LocalDate.of(2026, 10, 10));
        dto.setCategory(ExpenseCategoryCode.SALARY);
        dto.setEmployeeId(7L);
        dto.setExpenseItems(new ArrayList<>(List.of(base, bonus)));

        CompanyExpenseDTO result = expenseService.createExpense(dto);

        assertEquals(2, result.getExpenseItems().size());
        assertMoney(0.0, result.getExpenseItems().get(1).getPrice());
        verify(companyExpenseRepo).save(any());
    }

    @Test
    void getExpensePreview_shouldNotValidateItems() {
        CompanyExpenseDTO dto = rentExpenseWithItem(item -> item.setQuantity(0));

        assertDoesNotThrow(() -> expenseService.getExpensePreview(dto));
    }

    @Test
    void getExpensePreview_shouldNotTouchSellerRepo_whenSellerProvided() {
        CompanyExpenseDTO result = expenseService.getExpensePreview(
                rentExpenseDTO(sellerInput("5261040828", "Firma", "Adres")));

        assertNotNull(result.getTotalValue());
        verifyNoInteractions(ksefSellerRepo);
    }

    @Test
    void getExpenseById_shouldMapLinkedSeller() {
        CompanyExpense withSeller = expense(1L);
        withSeller.setSeller(KsefSeller.builder()
                .id(3L).nip("5261040828").name("Dostawca").address("Adres").email("x@y.pl").build());
        when(companyExpenseRepo.findOneByIdWithItems(1L)).thenReturn(Optional.of(withSeller));

        CompanyExpenseDTO result = expenseService.getExpenseById(1L);

        assertEquals(3L, result.getSellerId());
        assertEquals(3L, result.getSeller().getId());
        assertEquals("5261040828", result.getSeller().getNip());
        assertEquals("Dostawca", result.getSeller().getName());
        assertEquals("Adres", result.getSeller().getAddress());
    }

    private CompanyExpenseItemDTO mixedItem(String name, String category, Long productId, double price) {
        CompanyExpenseItemDTO item = new CompanyExpenseItemDTO();
        item.setName(name);
        item.setCategory(category);
        item.setProductId(productId);
        item.setQuantity(2);
        item.setVatRate(VatRate.VAT_23);
        item.setPrice(BigDecimal.valueOf(price));
        return item;
    }

    private CompanyExpenseDTO mixedExpenseDTO(CompanyExpenseItemDTO... items) {
        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setSource("Hurtownia");
        dto.setExpenseDate(LocalDate.of(2026, 9, 1));
        dto.setCategory(ExpenseCategoryCode.MIXED);
        dto.setExpenseItems(new ArrayList<>(List.of(items)));
        return dto;
    }

    private void assertMixedValidation(String expectedMessage, CompanyExpenseDTO dto) {
        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(dto));

        assertEquals(expectedMessage, ex.getMessage());
        verify(companyExpenseRepo, never()).save(any());
        verifyNoInteractions(productRepo, supplyHistoryService, ksefSellerRepo);
    }

    @Test
    void createExpense_shouldThrowValidationException_whenMixedItemWithoutCategory() {
        assertMixedValidation("Każda pozycja kosztu mieszanego musi mieć kategorię.", mixedExpenseDTO(
                mixedItem("Papier", "SUPPLIES", null, 10.0),
                mixedItem("Paliwo", null, null, 200.0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RENT", "SALARY", "ZUS", "TAXES", "MIXED"})
    void createExpense_shouldThrowValidationException_whenMixedItemCategoryForbidden(String forbidden) {
        assertMixedValidation("Niedozwolona kategoria pozycji.", mixedExpenseDTO(
                mixedItem("Papier", "SUPPLIES", null, 10.0),
                mixedItem("Zakazana", forbidden, null, 200.0)));
    }

    @Test
    void createExpense_shouldThrowValidationException_whenMixedProductsItemWithoutProduct() {
        assertMixedValidation("Pozycja z kategorią Produkty musi mieć wybrany produkt.", mixedExpenseDTO(
                mixedItem("Papier", "SUPPLIES", null, 10.0),
                mixedItem("Krem", ExpenseCategoryCode.PRODUCTS, null, 30.0)));
    }

    @Test
    void createExpense_shouldThrowValidationException_whenMixedNonProductsItemHasProduct() {
        assertMixedValidation("Pozycja z kategorią Produkty musi mieć wybrany produkt.", mixedExpenseDTO(
                mixedItem("Papier", "SUPPLIES", 5L, 10.0),
                mixedItem("Paliwo", "CAR", null, 200.0)));
    }

    @Test
    void createExpense_shouldThrowValidationException_whenMixedItemsShareOneCategory() {
        assertMixedValidation("Wszystkie pozycje mają tę samą kategorię - wybierz ją jako kategorię kosztu.",
                mixedExpenseDTO(
                        mixedItem("Papier", "SUPPLIES", null, 10.0),
                        mixedItem("Ręczniki", "SUPPLIES", null, 20.0)));
    }

    @Test
    void createExpense_shouldStoreItemCategoriesAndSupplyOnlyProductItems_whenMixedCategory() {
        Product product = Product.builder().id(5L).name("Krem do stóp").supply(10).build();
        when(productRepo.findByIdNotDeleted(5L)).thenReturn(Optional.of(product));
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> {
            CompanyExpense expense = invocation.getArgument(0);
            expense.setId(1L);
            return expense;
        });

        CompanyExpenseDTO result = expenseService.createExpense(mixedExpenseDTO(
                mixedItem("Krem", ExpenseCategoryCode.PRODUCTS, 5L, 30.0),
                mixedItem("Papier", "SUPPLIES", null, 10.0)));

        assertEquals(12, product.getSupply());
        verify(productRepo).save(product);
        verify(supplyHistoryService, times(1)).record(product, 2, SupplyChangeSource.COMPANY_EXPENSE,
                1L, "Koszt: Hurtownia");
        assertEquals(ExpenseCategoryCode.MIXED, result.getCategory());
        assertEquals("Krem do stóp", result.getExpenseItems().get(0).getName());
        assertEquals(5L, result.getExpenseItems().get(0).getProductId());
        assertEquals(ExpenseCategoryCode.PRODUCTS, result.getExpenseItems().get(0).getCategory());
        assertEquals("SUPPLIES", result.getExpenseItems().get(1).getCategory());
        assertNull(result.getExpenseItems().get(1).getProductId());
        assertEquals(List.of(ExpenseCategoryCode.PRODUCTS, "SUPPLIES"), result.getItemCategories());
        assertMoney(80.0, result.getTotalValue());
    }

    @Test
    void createExpense_shouldNotTouchSupply_whenMixedWithoutProductItems() {
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        CompanyExpenseDTO result = expenseService.createExpense(mixedExpenseDTO(
                mixedItem("Papier", "SUPPLIES", null, 10.0),
                mixedItem("Paliwo", "CAR", null, 200.0)));

        verifyNoInteractions(productRepo, supplyHistoryService);
        assertEquals(List.of("CAR", "SUPPLIES"), result.getItemCategories());
    }

    @Test
    void createExpense_shouldDropItemCategory_whenNonMixedCategory() {
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CompanyExpenseDTO dto = rentExpenseDTO(null);
        dto.getExpenseItems().get(0).setCategory("CAR");

        CompanyExpenseDTO result = expenseService.createExpense(dto);

        assertNull(result.getExpenseItems().get(0).getCategory());
        assertTrue(result.getItemCategories().isEmpty());
    }

    @Test
    void createExpense_shouldLinkSeller_whenMixedCategoryWithNip() {
        KsefSeller existing = KsefSeller.builder().id(3L).nip("5261040828").name("Hurtownia KSeF").build();
        when(ksefSellerRepo.findByNip("5261040828")).thenReturn(Optional.of(existing));
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        CompanyExpenseDTO dto = mixedExpenseDTO(
                mixedItem("Papier", "SUPPLIES", null, 10.0),
                mixedItem("Paliwo", "CAR", null, 200.0));
        dto.setSeller(sellerInput("5261040828", null, null));

        CompanyExpenseDTO result = expenseService.createExpense(dto);

        assertEquals(3L, result.getSellerId());
        assertEquals("Hurtownia KSeF", result.getSource());
    }

    private CompanyExpense mixedExpense(String... itemCategories) {
        CompanyExpense expense = CompanyExpense.builder()
                .id(1L)
                .source("Hurtownia")
                .expenseDate(LocalDate.of(2026, 9, 1))
                .category(ExpenseCategoryCode.MIXED)
                .build();
        for (String category : itemCategories) {
            expense.addExpenseItem(CompanyExpenseItem.builder()
                    .name(category)
                    .quantity(1)
                    .vatRate(VatRate.VAT_23)
                    .price(BigDecimal.TEN)
                    .category(category)
                    .build());
        }
        return expense;
    }

    @Test
    void deleteExpenseById_shouldDeleteAndAudit_whenMixedWithoutProductItems() {
        CompanyExpense existing = mixedExpense("CAR", "SUPPLIES");
        when(companyExpenseRepo.findOneByIdWithItems(1L)).thenReturn(Optional.of(existing));

        expenseService.deleteExpenseById(1L);

        verify(companyExpenseRepo).delete(existing);
        verify(auditLogService).logDelete(eq("CompanyExpense"), eq(1L), anyString(), any());
        verifyNoInteractions(productRepo, supplyHistoryService);
    }

    @Test
    void getExpenseById_shouldExposeItemCategories_whenMixed() {
        when(companyExpenseRepo.findOneByIdWithItems(1L)).thenReturn(Optional.of(
                mixedExpense("SUPPLIES", "CAR", "SUPPLIES")));

        CompanyExpenseDTO result = expenseService.getExpenseById(1L);

        assertEquals(List.of("CAR", "SUPPLIES"), result.getItemCategories());
        assertEquals("SUPPLIES", result.getExpenseItems().get(0).getCategory());
    }

    @Test
    void getExpenses_mapsItemCategories_fromUnifiedRow() {
        UnifiedExpenseProjection mixed = mock(UnifiedExpenseProjection.class);
        when(mixed.getSourceId()).thenReturn(1L);
        when(mixed.getSourceType()).thenReturn("MANUAL");
        when(mixed.getCategory()).thenReturn("MIXED");
        when(mixed.getItemCategories()).thenReturn("PRODUCTS,SUPPLIES");

        UnifiedExpenseProjection rent = mock(UnifiedExpenseProjection.class);
        when(rent.getSourceId()).thenReturn(2L);
        when(rent.getSourceType()).thenReturn("MANUAL");
        when(rent.getCategory()).thenReturn("RENT");
        when(rent.getItemCategories()).thenReturn(null);

        when(companyExpenseRepo.findUnifiedWithFilters(any(), any(), any(), anyInt(), any()))
                .thenReturn(new PageImpl<>(List.of(mixed, rent), PageRequest.of(0, 10), 2));

        Page<CompanyExpenseDTO> result = expenseService.getExpenses(null, 0, 10);

        assertEquals(ExpenseCategoryCode.MIXED, result.getContent().get(0).getCategory());
        assertEquals(List.of(ExpenseCategoryCode.PRODUCTS, "SUPPLIES"),
                result.getContent().get(0).getItemCategories());
        assertTrue(result.getContent().get(1).getItemCategories().isEmpty());
    }

    private Product stockProduct(Long id, String name, int supply) {
        return Product.builder().id(id).name(name).supply(supply).build();
    }

    private Product removedProduct(Long id, String name) {
        return Product.builder().id(id).name(name).supply(0).isDeleted(true).build();
    }

    private CompanyExpenseItem productItem(Product product, int quantity, String category) {
        return CompanyExpenseItem.builder()
                .name(product.getName())
                .quantity(quantity)
                .vatRate(VatRate.VAT_23)
                .price(BigDecimal.TEN)
                .category(category)
                .product(product)
                .build();
    }

    @Test
    void getDeletionPreview_shouldSumQuantitiesPerProduct_andSortByNameIgnoringCase() {
        Product serum = stockProduct(5L, "Serum", 10);
        Product cream = stockProduct(6L, "krem do stóp", 4);
        CompanyExpense existing = expense(1L);
        existing.addExpenseItem(productItem(serum, 3, null));
        existing.addExpenseItem(productItem(cream, 1, null));
        existing.addExpenseItem(productItem(serum, 2, null));
        when(companyExpenseRepo.findOneByIdWithItems(1L)).thenReturn(Optional.of(existing));
        when(productRepo.findAllById(Set.of(5L, 6L))).thenReturn(List.of(serum, cream));

        StockChangePreviewDTO preview = expenseService.getDeletionPreview(1L);

        assertTrue(preview.isCanApply());
        assertEquals(List.of("krem do stóp", "Serum"),
                preview.getProducts().stream().map(StockChangeProductDTO::getProductName).toList());
        StockChangeProductDTO serumPreview = preview.getProducts().get(1);
        assertEquals(5L, serumPreview.getProductId());
        assertEquals(-5, serumPreview.getQuantity());
        assertEquals(10, serumPreview.getCurrentSupply());
        assertEquals(5, serumPreview.getSupplyAfter());
        assertTrue(serumPreview.isSufficient());
        assertFalse(serumPreview.isSkipped());
        assertEquals(-1, preview.getProducts().get(0).getQuantity());
        assertEquals(3, preview.getProducts().get(0).getSupplyAfter());
        verify(productRepo, never()).findAllByIdInForUpdate(any());
    }

    @Test
    void getDeletionPreview_shouldBlock_whenAnyProductSupplyInsufficient() {
        Product cream = stockProduct(5L, "Krem", 1);
        Product serum = stockProduct(6L, "Serum", 10);
        CompanyExpense existing = expense(1L);
        existing.addExpenseItem(productItem(cream, 3, null));
        existing.addExpenseItem(productItem(serum, 2, null));
        when(companyExpenseRepo.findOneByIdWithItems(1L)).thenReturn(Optional.of(existing));
        when(productRepo.findAllById(Set.of(5L, 6L))).thenReturn(List.of(serum, cream));

        StockChangePreviewDTO preview = expenseService.getDeletionPreview(1L);

        assertFalse(preview.isCanApply());
        StockChangeProductDTO creamPreview = preview.getProducts().get(0);
        assertEquals("Krem", creamPreview.getProductName());
        assertEquals(-3, creamPreview.getQuantity());
        assertEquals(1, creamPreview.getCurrentSupply());
        assertEquals(-2, creamPreview.getSupplyAfter());
        assertFalse(creamPreview.isSufficient());
        assertFalse(creamPreview.isSkipped());
        assertTrue(preview.getProducts().get(1).isSufficient());
    }

    @Test
    void getDeletionPreview_shouldSkipProductRemovedFromStock_withoutBlocking() {
        Product removed = removedProduct(5L, "Krem");
        CompanyExpense existing = mixedExpense("SUPPLIES");
        existing.addExpenseItem(productItem(removed, 3, ExpenseCategoryCode.PRODUCTS));
        when(companyExpenseRepo.findOneByIdWithItems(1L)).thenReturn(Optional.of(existing));
        when(productRepo.findAllById(Set.of(5L))).thenReturn(List.of(removed));

        StockChangePreviewDTO preview = expenseService.getDeletionPreview(1L);

        assertTrue(preview.isCanApply());
        assertEquals(1, preview.getProducts().size());
        StockChangeProductDTO removedPreview = preview.getProducts().get(0);
        assertTrue(removedPreview.isSkipped());
        assertTrue(removedPreview.isSufficient());
        assertEquals(-3, removedPreview.getQuantity());
        assertEquals(0, removedPreview.getCurrentSupply());
        assertEquals(0, removedPreview.getSupplyAfter());
    }

    @Test
    void getDeletionPreview_shouldAllowWithEmptyProducts_whenExpenseHasNoProductItems() {
        when(companyExpenseRepo.findOneByIdWithItems(1L))
                .thenReturn(Optional.of(mixedExpense("CAR", "SUPPLIES")));

        StockChangePreviewDTO preview = expenseService.getDeletionPreview(1L);

        assertTrue(preview.isCanApply());
        assertTrue(preview.getProducts().isEmpty());
        verifyNoInteractions(productRepo);
    }

    @Test
    void getDeletionPreview_shouldThrowResourceNotFoundException_whenNotFound() {
        when(companyExpenseRepo.findOneByIdWithItems(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> expenseService.getDeletionPreview(99L));
        verifyNoInteractions(productRepo);
    }

    @Test
    void deleteExpenseById_shouldReduceSupplyRecordRevertEntryAndDelete_whenProductsCategory() {
        Product product = stockProduct(5L, "Krem", 10);
        CompanyExpense existing = expense(1L);
        existing.addExpenseItem(productItem(product, 3, null));
        existing.addExpenseItem(productItem(product, 2, null));
        when(companyExpenseRepo.findOneByIdWithItems(1L)).thenReturn(Optional.of(existing));
        when(productRepo.findAllByIdInForUpdate(Set.of(5L))).thenReturn(List.of(product));
        List<Integer> supplyAtRecord = new ArrayList<>();
        doAnswer(invocation -> supplyAtRecord.add(invocation.<Product>getArgument(0).getSupply()))
                .when(supplyHistoryService).record(any(), anyInt(), any(), any(), any());

        expenseService.deleteExpenseById(1L);

        assertEquals(5, product.getSupply());
        assertEquals(List.of(5), supplyAtRecord);
        InOrder inOrder = inOrder(productRepo, supplyHistoryService, companyExpenseRepo, auditLogService);
        inOrder.verify(productRepo).save(product);
        inOrder.verify(supplyHistoryService).record(product, -5, SupplyChangeSource.COMPANY_EXPENSE_DELETED,
                1L, "Koszt: Dostawca A");
        inOrder.verify(companyExpenseRepo).delete(existing);
        inOrder.verify(auditLogService).logDelete(eq("CompanyExpense"), eq(1L), anyString(), any());
        verify(supplyHistoryService, times(1)).record(any(), anyInt(), any(), any(), any());
        verify(productRepo, never()).findAllById(any());
    }

    @Test
    void deleteExpenseById_shouldReduceSupplyOfProductItemsOnly_whenMixedAndSupplyEqualsQuantity() {
        Product product = stockProduct(5L, "Krem", 4);
        CompanyExpense existing = mixedExpense("SUPPLIES");
        existing.addExpenseItem(productItem(product, 4, ExpenseCategoryCode.PRODUCTS));
        when(companyExpenseRepo.findOneByIdWithItems(1L)).thenReturn(Optional.of(existing));
        when(productRepo.findAllByIdInForUpdate(Set.of(5L))).thenReturn(List.of(product));

        expenseService.deleteExpenseById(1L);

        assertEquals(0, product.getSupply());
        verify(productRepo).save(product);
        verify(supplyHistoryService).record(product, -4, SupplyChangeSource.COMPANY_EXPENSE_DELETED,
                1L, "Koszt: Hurtownia");
        verify(companyExpenseRepo).delete(existing);
        verify(auditLogService).logDelete(eq("CompanyExpense"), eq(1L), anyString(), any());
    }

    @Test
    void deleteExpenseById_shouldThrowBlockedWithPreviewAndChangeNothing_whenSupplyInsufficient() {
        Product cream = stockProduct(5L, "Krem", 1);
        Product serum = stockProduct(6L, "Serum", 10);
        CompanyExpense existing = expense(1L);
        existing.addExpenseItem(productItem(cream, 3, null));
        existing.addExpenseItem(productItem(serum, 2, null));
        when(companyExpenseRepo.findOneByIdWithItems(1L)).thenReturn(Optional.of(existing));
        when(productRepo.findAllByIdInForUpdate(Set.of(5L, 6L))).thenReturn(List.of(cream, serum));

        StockChangeBlockedException ex = assertThrows(StockChangeBlockedException.class,
                () -> expenseService.deleteExpenseById(1L));

        StockChangePreviewDTO preview = ex.getPreview();
        assertFalse(preview.isCanApply());
        assertEquals(2, preview.getProducts().size());
        assertEquals(5L, preview.getProducts().get(0).getProductId());
        assertEquals(-2, preview.getProducts().get(0).getSupplyAfter());
        assertFalse(preview.getProducts().get(0).isSufficient());
        assertTrue(preview.getProducts().get(1).isSufficient());
        assertEquals(1, cream.getSupply());
        assertEquals(10, serum.getSupply());
        verify(productRepo, never()).save(any());
        verify(companyExpenseRepo, never()).delete(any());
        verifyNoInteractions(supplyHistoryService, auditLogService);
    }

    @Test
    void deleteExpenseById_shouldSkipProductRemovedFromStock_andRecordNoHistoryForIt() {
        Product removed = removedProduct(5L, "Krem");
        Product active = stockProduct(6L, "Serum", 10);
        CompanyExpense existing = expense(1L);
        existing.addExpenseItem(productItem(removed, 3, null));
        existing.addExpenseItem(productItem(active, 2, null));
        when(companyExpenseRepo.findOneByIdWithItems(1L)).thenReturn(Optional.of(existing));
        when(productRepo.findAllByIdInForUpdate(Set.of(5L, 6L))).thenReturn(List.of(removed, active));

        expenseService.deleteExpenseById(1L);

        assertEquals(0, removed.getSupply());
        assertEquals(8, active.getSupply());
        verify(productRepo, never()).save(removed);
        verify(productRepo).save(active);
        verify(supplyHistoryService, never()).record(eq(removed), anyInt(), any(), any(), any());
        verify(supplyHistoryService).record(active, -2, SupplyChangeSource.COMPANY_EXPENSE_DELETED,
                1L, "Koszt: Dostawca A");
        verify(companyExpenseRepo).delete(existing);
    }

    private CompanyExpense editableExpense(String category, KsefSeller seller) {
        CompanyExpense expense = CompanyExpense.builder()
                .id(5L)
                .source("Stary dostawca")
                .expenseDate(LocalDate.of(2026, 9, 1))
                .category(category)
                .seller(seller)
                .build();
        expense.addExpenseItem(CompanyExpenseItem.builder()
                .name("Pozycja")
                .quantity(2)
                .vatRate(VatRate.VAT_23)
                .price(BigDecimal.valueOf(123))
                .build());
        expense.calculateTotals();
        return expense;
    }

    private CompanyExpenseUpdateDTO updateInput(String category, String source, ExpenseSellerDTO seller) {
        return new CompanyExpenseUpdateDTO(LocalDate.of(2026, 10, 1), source, category, seller, null, null);
    }

    private void stubExpense(CompanyExpense expense) {
        when(companyExpenseRepo.findOneByIdWithItems(expense.getId())).thenReturn(Optional.of(expense));
    }

    private void stubSave() {
        when(companyExpenseRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updateExpense_shouldChangeDate_andKeepItemsAndTotals() {
        stubExpense(editableExpense("EQUIPMENT", null));
        stubSave();

        CompanyExpenseDTO result = expenseService.updateExpense(5L,
                new CompanyExpenseUpdateDTO(LocalDate.of(2027, 1, 15), "Stary dostawca", "EQUIPMENT", null, null, null));

        assertEquals(LocalDate.of(2027, 1, 15), result.getExpenseDate());
        assertEquals(1, result.getExpenseItems().size());
        assertEquals(2, result.getExpenseItems().get(0).getQuantity().intValue());
        assertMoney(246.0, result.getTotalValue());
        assertMoney(46.0, result.getTotalVat());
        assertMoney(200.0, result.getTotalNet());
        verify(auditLogService).logUpdate(eq("CompanyExpense"), eq(5L), eq("Koszt: Stary dostawca"), any(), any());
        verifyNoInteractions(productRepo, supplyHistoryService, ksefSellerRepo);
    }

    @Test
    void updateExpense_shouldAuditOldAndNewSnapshots() {
        stubExpense(editableExpense("EQUIPMENT", null));
        stubSave();

        expenseService.updateExpense(5L, updateInput("EQUIPMENT", "Nowy", null));

        ArgumentCaptor<CompanyExpenseDTO> oldCaptor = ArgumentCaptor.forClass(CompanyExpenseDTO.class);
        ArgumentCaptor<CompanyExpenseDTO> newCaptor = ArgumentCaptor.forClass(CompanyExpenseDTO.class);
        verify(auditLogService).logUpdate(eq("CompanyExpense"), eq(5L), anyString(), oldCaptor.capture(), newCaptor.capture());
        assertEquals("Stary dostawca", oldCaptor.getValue().getSource());
        assertEquals(LocalDate.of(2026, 9, 1), oldCaptor.getValue().getExpenseDate());
        assertEquals("Nowy", newCaptor.getValue().getSource());
        assertEquals(LocalDate.of(2026, 10, 1), newCaptor.getValue().getExpenseDate());
    }

    @Test
    void updateExpense_shouldChangeSourceName_whenNoNip() {
        stubExpense(editableExpense("EQUIPMENT", null));
        stubSave();

        CompanyExpenseDTO result = expenseService.updateExpense(5L,
                updateInput("EQUIPMENT", "  Hurtownia X  ", sellerInput("  ", null, null)));

        assertEquals("Hurtownia X", result.getSource());
        assertNull(result.getSeller());
        verifyNoInteractions(ksefSellerRepo);
    }

    @Test
    void updateExpense_shouldThrowValidationException_whenSourceBlankWithoutNip() {
        CompanyExpense existing = editableExpense("EQUIPMENT", null);
        stubExpense(existing);

        ValidationException ex = assertThrows(ValidationException.class, () ->
                expenseService.updateExpense(5L, updateInput("EQUIPMENT", "   ", null)));

        assertEquals("Nazwa dostawcy jest wymagana.", ex.getMessage());
        assertEquals("Stary dostawca", existing.getSource());
        verify(companyExpenseRepo, never()).save(any());
    }

    @Test
    void updateExpense_shouldThrowValidationException_whenDateMissing() {
        stubExpense(editableExpense("EQUIPMENT", null));

        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.updateExpense(5L,
                new CompanyExpenseUpdateDTO(null, "Dostawca", "EQUIPMENT", null, null, null)));

        assertEquals("Data kosztu jest wymagana.", ex.getMessage());
        verify(companyExpenseRepo, never()).save(any());
    }

    @Test
    void updateExpense_shouldThrowValidationException_whenCategoryMissing() {
        stubExpense(editableExpense("EQUIPMENT", null));

        ValidationException ex = assertThrows(ValidationException.class, () ->
                expenseService.updateExpense(5L, updateInput(null, "Dostawca", null)));

        assertEquals("Kategoria kosztu jest wymagana.", ex.getMessage());
        verify(companyExpenseRepo, never()).save(any());
    }

    @Test
    void updateExpense_shouldLinkExistingSellerWithoutModifyingIt_andUseItsNameAsSource() {
        KsefSeller existingSeller = KsefSeller.builder()
                .id(3L).nip("5261040828").name("Dostawca KSeF").address("Stary adres").build();
        stubExpense(editableExpense("EQUIPMENT", null));
        when(ksefSellerRepo.findByNip("5261040828")).thenReturn(Optional.of(existingSeller));
        stubSave();

        CompanyExpenseDTO result = expenseService.updateExpense(5L,
                updateInput("EQUIPMENT", "Wpisane", sellerInput("526-104-08-28", "Inna nazwa", "Inny adres")));

        assertEquals("Dostawca KSeF", result.getSource());
        assertEquals(3L, result.getSellerId());
        assertEquals("Dostawca KSeF", existingSeller.getName());
        assertEquals("Stary adres", existingSeller.getAddress());
        verify(ksefSellerRepo, never()).save(any());
        verify(auditLogService, never()).logCreate(eq("KsefSeller"), any(), any(), any());
    }

    @Test
    void updateExpense_shouldCreateNewSeller_whenNipUnknown() {
        stubExpense(editableExpense("EQUIPMENT", null));
        when(ksefSellerRepo.findByNip("5261040828")).thenReturn(Optional.empty());
        when(ksefSellerRepo.save(any(KsefSeller.class))).thenAnswer(invocation -> {
            KsefSeller seller = invocation.getArgument(0);
            seller.setId(11L);
            return seller;
        });
        stubSave();

        CompanyExpenseDTO result = expenseService.updateExpense(5L,
                updateInput("EQUIPMENT", null, sellerInput("5261040828", " Nowa Firma ", " ul. Prosta 1 ")));

        assertEquals("Nowa Firma", result.getSource());
        assertEquals(11L, result.getSellerId());
        assertEquals("ul. Prosta 1", result.getSeller().getAddress());
        verify(auditLogService).logCreate(eq("KsefSeller"), eq(11L), eq("Nowa Firma"), any());
    }

    @Test
    void updateExpense_shouldThrowValidationException_whenNewNipWithoutNameOrAddress() {
        stubExpense(editableExpense("EQUIPMENT", null));
        when(ksefSellerRepo.findByNip("5261040828")).thenReturn(Optional.empty());

        ValidationException noName = assertThrows(ValidationException.class, () -> expenseService.updateExpense(5L,
                updateInput("EQUIPMENT", "X", sellerInput("5261040828", null, "Adres"))));
        ValidationException noAddress = assertThrows(ValidationException.class, () -> expenseService.updateExpense(5L,
                updateInput("EQUIPMENT", "X", sellerInput("5261040828", "Firma", " "))));

        assertEquals("Nazwa sprzedawcy jest wymagana dla nowego NIP.", noName.getMessage());
        assertEquals("Adres sprzedawcy jest wymagany dla nowego NIP.", noAddress.getMessage());
        verify(ksefSellerRepo, never()).save(any());
        verify(companyExpenseRepo, never()).save(any());
    }

    @Test
    void updateExpense_shouldThrowValidationException_whenNipInvalid() {
        stubExpense(editableExpense("EQUIPMENT", null));

        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.updateExpense(5L,
                updateInput("EQUIPMENT", "X", sellerInput("1234567890", "Firma", "Adres"))));

        assertEquals("Niepoprawny NIP lub numer VAT UE sprzedawcy.", ex.getMessage());
        verifyNoInteractions(ksefSellerRepo);
        verify(companyExpenseRepo, never()).save(any());
    }

    @Test
    void updateExpense_shouldUnlinkSeller_whenSellerNull() {
        KsefSeller linked = KsefSeller.builder().id(3L).nip("5261040828").name("Dostawca KSeF").build();
        stubExpense(editableExpense("EQUIPMENT", linked));
        stubSave();

        CompanyExpenseDTO result = expenseService.updateExpense(5L,
                updateInput("EQUIPMENT", "Bez NIP", null));

        assertNull(result.getSeller());
        assertNull(result.getSellerId());
        assertEquals("Bez NIP", result.getSource());
        assertEquals("Dostawca KSeF", linked.getName());
    }

    @Test
    void updateExpense_shouldAllowChangingBetweenUnlockedCategories() {
        stubExpense(editableExpense("EQUIPMENT", null));
        stubSave();

        CompanyExpenseDTO result = expenseService.updateExpense(5L,
                updateInput("SUPPLIES", "Stary dostawca", null));

        assertEquals("SUPPLIES", result.getCategory());
    }

    @Test
    void updateExpense_shouldAllowSameLockedCategory() {
        stubExpense(editableExpense(ExpenseCategoryCode.RENT, null));
        stubSave();

        CompanyExpenseDTO result = expenseService.updateExpense(5L,
                updateInput(ExpenseCategoryCode.RENT, "Wynajmujący", null));

        assertEquals(ExpenseCategoryCode.RENT, result.getCategory());
        assertEquals("Wynajmujący", result.getSource());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRODUCTS", "MIXED", "RENT", "ZUS", "TAXES"})
    void updateExpense_shouldThrowConflictException_whenChangingFromLockedCategory(String locked) {
        CompanyExpense existing = editableExpense(locked, null);
        stubExpense(existing);

        ConflictException ex = assertThrows(ConflictException.class, () ->
                expenseService.updateExpense(5L, updateInput("EQUIPMENT", "X", null)));

        assertEquals("Nie można zmienić tej kategorii kosztu.", ex.getMessage());
        assertEquals(locked, existing.getCategory());
        verify(companyExpenseRepo, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PRODUCTS", "MIXED", "SALARY", "RENT", "ZUS", "TAXES"})
    void updateExpense_shouldThrowConflictException_whenChangingToLockedCategory(String locked) {
        stubExpense(editableExpense("EQUIPMENT", null));

        ConflictException ex = assertThrows(ConflictException.class, () ->
                expenseService.updateExpense(5L, updateInput(locked, "X", null)));

        assertEquals("Nie można zmienić tej kategorii kosztu.", ex.getMessage());
        verify(companyExpenseRepo, never()).save(any());
    }

    @Test
    void updateExpense_shouldThrowConflictException_whenSalaryExpense() {
        CompanyExpense existing = editableExpense(ExpenseCategoryCode.SALARY, null);
        stubExpense(existing);

        ConflictException ex = assertThrows(ConflictException.class, () ->
                expenseService.updateExpense(5L, updateInput(ExpenseCategoryCode.SALARY, "Nowe", null)));

        assertEquals("Nie można edytować kosztu wypłaty.", ex.getMessage());
        assertEquals("Stary dostawca", existing.getSource());
        verify(companyExpenseRepo, never()).save(any());
        verify(auditLogService, never()).logUpdate(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZUS", "TAXES"})
    void updateExpense_shouldKeepSourceAndIgnoreSeller_whenFixedSourceCategory(String category) {
        stubExpense(editableExpense(category, null));
        stubSave();

        CompanyExpenseDTO result = expenseService.updateExpense(5L,
                updateInput(category, "Inne źródło", sellerInput("5261040828", "Firma", "Adres")));

        assertEquals("Stary dostawca", result.getSource());
        assertNull(result.getSeller());
        assertEquals(LocalDate.of(2026, 10, 1), result.getExpenseDate());
        verifyNoInteractions(ksefSellerRepo);
    }

    @Test
    void updateExpense_shouldThrowResourceNotFoundException_whenMissing() {
        when(companyExpenseRepo.findOneByIdWithItems(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                expenseService.updateExpense(99L, updateInput("EQUIPMENT", "X", null)));

        verify(companyExpenseRepo, never()).save(any());
    }
    @Test
    void createExpense_shouldThrowValidationException_whenCategoryUnknown() {
        CompanyExpenseDTO dto = rentExpenseDTO(null);
        dto.setCategory("XYZ");
        doThrow(new ValidationException("Nieznana kategoria wydatku.")).when(expenseCategoryService).requireExistingCode("XYZ");

        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(dto));

        assertEquals("Nieznana kategoria wydatku.", ex.getMessage());
        verify(companyExpenseRepo, never()).save(any());
        verifyNoInteractions(ksefSellerRepo, productRepo, supplyHistoryService, auditLogService);
    }

    @Test
    void createExpense_shouldThrowValidationException_whenCategoryNull() {
        CompanyExpenseDTO dto = rentExpenseDTO(null);
        dto.setCategory(null);

        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(dto));

        assertEquals("Kategoria kosztu jest wymagana.", ex.getMessage());
        verify(companyExpenseRepo, never()).save(any());
        verifyNoInteractions(expenseCategoryService);
    }

    @Test
    void createExpense_shouldValidateEveryMixedItemCategoryCode() {
        lenient().doThrow(new ValidationException("Nieznana kategoria wydatku.")).when(expenseCategoryService).requireExistingCode("XYZ");

        assertMixedValidation("Nieznana kategoria wydatku.", mixedExpenseDTO(
                mixedItem("Papier", "SUPPLIES", null, 10.0),
                mixedItem("Nieznana", "XYZ", null, 200.0)));
        verify(expenseCategoryService).requireExistingCode("SUPPLIES");
    }

    @Test
    void createExpense_shouldStoreCustomCategoryCode() {
        String customCode = "C0123456789abcdef0123456789abcdef";
        CompanyExpenseDTO dto = rentExpenseDTO(null);
        dto.setCategory(customCode);
        stubSave();

        CompanyExpenseDTO result = expenseService.createExpense(dto);

        assertEquals(customCode, result.getCategory());
        verify(expenseCategoryService).requireExistingCode(customCode);
    }

    @Test
    void updateExpense_shouldThrowValidationException_whenCategoryUnknown() {
        CompanyExpense existing = editableExpense("EQUIPMENT", null);
        stubExpense(existing);
        doThrow(new ValidationException("Nieznana kategoria wydatku.")).when(expenseCategoryService).requireExistingCode("XYZ");

        ValidationException ex = assertThrows(ValidationException.class, () ->
                expenseService.updateExpense(5L, updateInput("XYZ", "X", null)));

        assertEquals("Nieznana kategoria wydatku.", ex.getMessage());
        assertEquals("EQUIPMENT", existing.getCategory());
        verify(companyExpenseRepo, never()).save(any());
    }

    private CompanyExpenseDTO invoiceNumberInput(String invoiceNumber) {
        CompanyExpenseItemDTO itemDTO = new CompanyExpenseItemDTO();
        itemDTO.setName("Narzędzie");
        itemDTO.setQuantity(1);
        itemDTO.setVatRate(VatRate.VAT_23);
        itemDTO.setPrice(BigDecimal.valueOf(123.0));

        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setSource("Dostawca A");
        dto.setExpenseDate(LocalDate.of(2026, 10, 1));
        dto.setCategory("EQUIPMENT");
        dto.setExpenseItems(new ArrayList<>(List.of(itemDTO)));
        dto.setInvoiceNumber(invoiceNumber);
        return dto;
    }

    private CompanyExpense captureSavedExpense() {
        ArgumentCaptor<CompanyExpense> captor = ArgumentCaptor.forClass(CompanyExpense.class);
        verify(companyExpenseRepo).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void createExpense_shouldTrimInvoiceNumber() {
        stubSave();

        CompanyExpenseDTO result = expenseService.createExpense(invoiceNumberInput("  FV/12/2026  "));

        assertEquals("FV/12/2026", captureSavedExpense().getInvoiceNumber());
        assertEquals("FV/12/2026", result.getInvoiceNumber());
    }

    @Test
    void createExpense_shouldStoreNullInvoiceNumber_whenBlank() {
        stubSave();

        CompanyExpenseDTO result = expenseService.createExpense(invoiceNumberInput("   "));

        assertNull(captureSavedExpense().getInvoiceNumber());
        assertNull(result.getInvoiceNumber());
    }

    @Test
    void createExpense_shouldSaveExpenseNormally_whenInvoiceNumberNull() {
        stubSave();

        CompanyExpenseDTO result = expenseService.createExpense(invoiceNumberInput(null));

        CompanyExpense saved = captureSavedExpense();
        assertNull(saved.getInvoiceNumber());
        assertEquals("Dostawca A", saved.getSource());
        assertEquals(LocalDate.of(2026, 10, 1), saved.getExpenseDate());
        assertEquals("EQUIPMENT", saved.getCategory());
        assertEquals(1, saved.getExpenseItems().size());
        assertMoney(123.0, saved.getTotalValue());
        assertNull(result.getInvoiceNumber());
        verify(auditLogService).logCreate(eq("CompanyExpense"), any(), eq("Koszt: Dostawca A"), any());
    }

    @Test
    void createExpense_shouldThrowValidationException_whenInvoiceNumberTooLong() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                expenseService.createExpense(invoiceNumberInput("A".repeat(65))));

        assertEquals("Numer faktury może mieć maksymalnie 64 znaki.", ex.getMessage());
        verify(companyExpenseRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void createExpense_shouldAcceptInvoiceNumberWithExactly64Chars() {
        stubSave();

        CompanyExpenseDTO result = expenseService.createExpense(invoiceNumberInput("A".repeat(64)));

        assertEquals("A".repeat(64), captureSavedExpense().getInvoiceNumber());
        assertEquals("A".repeat(64), result.getInvoiceNumber());
    }

    @Test
    void createExpense_shouldValidateInvoiceNumberLengthAfterTrim() {
        stubSave();

        CompanyExpenseDTO result = expenseService.createExpense(invoiceNumberInput("  " + "B".repeat(64) + "  "));

        assertEquals("B".repeat(64), result.getInvoiceNumber());
    }

    private CompanyExpenseUpdateDTO invoiceNumberUpdate(String invoiceNumber) {
        return new CompanyExpenseUpdateDTO(LocalDate.of(2026, 10, 1), "Stary dostawca", "EQUIPMENT", null, invoiceNumber, null);
    }

    @Test
    void updateExpense_shouldChangeInvoiceNumber() {
        CompanyExpense existing = editableExpense("EQUIPMENT", null);
        existing.setInvoiceNumber("FV/1");
        stubExpense(existing);
        stubSave();

        CompanyExpenseDTO result = expenseService.updateExpense(5L, invoiceNumberUpdate("FV/2"));

        assertEquals("FV/2", existing.getInvoiceNumber());
        assertEquals("FV/2", result.getInvoiceNumber());
    }

    @Test
    void updateExpense_shouldTrimInvoiceNumber() {
        CompanyExpense existing = editableExpense("EQUIPMENT", null);
        stubExpense(existing);
        stubSave();

        expenseService.updateExpense(5L, invoiceNumberUpdate("  FV/3  "));

        assertEquals("FV/3", existing.getInvoiceNumber());
    }

    @Test
    void updateExpense_shouldClearInvoiceNumber_whenBlank() {
        CompanyExpense existing = editableExpense("EQUIPMENT", null);
        existing.setInvoiceNumber("FV/1");
        stubExpense(existing);
        stubSave();

        CompanyExpenseDTO result = expenseService.updateExpense(5L, invoiceNumberUpdate("  "));

        assertNull(existing.getInvoiceNumber());
        assertNull(result.getInvoiceNumber());
    }

    @Test
    void updateExpense_shouldClearInvoiceNumber_whenNull() {
        CompanyExpense existing = editableExpense("EQUIPMENT", null);
        existing.setInvoiceNumber("FV/1");
        stubExpense(existing);
        stubSave();

        expenseService.updateExpense(5L, invoiceNumberUpdate(null));

        assertNull(existing.getInvoiceNumber());
    }

    @Test
    void updateExpense_shouldThrowValidationException_whenInvoiceNumberTooLong() {
        CompanyExpense existing = editableExpense("EQUIPMENT", null);
        existing.setInvoiceNumber("FV/1");
        stubExpense(existing);

        ValidationException ex = assertThrows(ValidationException.class, () ->
                expenseService.updateExpense(5L, invoiceNumberUpdate("A".repeat(65))));

        assertEquals("Numer faktury może mieć maksymalnie 64 znaki.", ex.getMessage());
        assertEquals("FV/1", existing.getInvoiceNumber());
        assertEquals(LocalDate.of(2026, 9, 1), existing.getExpenseDate());
        verify(companyExpenseRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void updateExpense_shouldAuditOldAndNewInvoiceNumber() {
        CompanyExpense existing = editableExpense("EQUIPMENT", null);
        existing.setInvoiceNumber("FV/1");
        stubExpense(existing);
        stubSave();

        expenseService.updateExpense(5L, invoiceNumberUpdate("FV/2"));

        ArgumentCaptor<CompanyExpenseDTO> oldCaptor = ArgumentCaptor.forClass(CompanyExpenseDTO.class);
        ArgumentCaptor<CompanyExpenseDTO> newCaptor = ArgumentCaptor.forClass(CompanyExpenseDTO.class);
        verify(auditLogService).logUpdate(eq("CompanyExpense"), eq(5L), anyString(), oldCaptor.capture(), newCaptor.capture());
        assertEquals("FV/1", oldCaptor.getValue().getInvoiceNumber());
        assertEquals("FV/2", newCaptor.getValue().getInvoiceNumber());
    }

    @Test
    void getExpenseById_shouldReturnInvoiceNumber() {
        CompanyExpense existing = expense(1L);
        existing.setInvoiceNumber("FV/7/2026");
        when(companyExpenseRepo.findOneByIdWithItems(1L)).thenReturn(Optional.of(existing));

        assertEquals("FV/7/2026", expenseService.getExpenseById(1L).getInvoiceNumber());
    }

    private static final ExchangeRateDTO EUR_RATE_30_10 =
            new ExchangeRateDTO(LocalDate.of(2026, 10, 30), new BigDecimal("4.2512"), "211/A/NBP/2026");

    private CompanyExpenseDTO foreignExpenseDTO(String category, ExpenseCurrency currency) {
        CompanyExpenseItemDTO itemDTO = new CompanyExpenseItemDTO();
        itemDTO.setName("Usługa zagraniczna");
        itemDTO.setQuantity(1);
        itemDTO.setVatRate(VatRate.VAT_23);
        itemDTO.setPrice(new BigDecimal("100.00"));

        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setSource("Dostawca EU");
        dto.setExpenseDate(LocalDate.of(2026, 11, 2));
        dto.setCategory(category);
        dto.setCurrency(currency);
        dto.setExpenseItems(new ArrayList<>(List.of(itemDTO)));
        return dto;
    }

    private CompanyExpense eurExpense() {
        CompanyExpense expense = editableExpense("EQUIPMENT", null);
        expense.setCurrency(ExpenseCurrency.EUR);
        expense.applyExchangeRate(new BigDecimal("4.0000"), LocalDate.of(2026, 8, 31), "168/A/NBP/2026");
        expense.calculateTotals();
        return expense;
    }

    @Test
    void createExpense_shouldStoreRateAndBothTotals_andIgnoreRequestRate_whenEur() {
        CompanyExpenseDTO dto = foreignExpenseDTO("OTHER", ExpenseCurrency.EUR);
        dto.setExchangeRate(new BigDecimal("9.9999"));
        dto.setExchangeRateDate(LocalDate.of(2020, 1, 1));
        dto.setExchangeRateTable("FAKE");
        dto.setTotalValue(new BigDecimal("1.00"));
        when(exchangeRateService.resolveRate(ExpenseCurrency.EUR, LocalDate.of(2026, 11, 2)))
                .thenReturn(Optional.of(EUR_RATE_30_10));
        stubSave();

        CompanyExpenseDTO result = expenseService.createExpense(dto);

        ArgumentCaptor<CompanyExpense> captor = ArgumentCaptor.forClass(CompanyExpense.class);
        verify(companyExpenseRepo).save(captor.capture());
        CompanyExpense saved = captor.getValue();
        assertEquals(ExpenseCurrency.EUR, saved.getCurrency());
        assertMoney(4.2512, saved.getExchangeRate());
        assertEquals(LocalDate.of(2026, 10, 30), saved.getExchangeRateDate());
        assertEquals("211/A/NBP/2026", saved.getExchangeRateTable());
        assertMoney(100.00, saved.getTotalValueCurrency());
        assertMoney(18.70, saved.getTotalVatCurrency());
        assertMoney(81.30, saved.getTotalNetCurrency());
        assertMoney(425.12, saved.getTotalValue());
        assertMoney(79.50, saved.getTotalVat());
        assertMoney(345.62, saved.getTotalNet());
        assertMoney(100.00, saved.getExpenseItems().get(0).getPrice());

        assertEquals(ExpenseCurrency.EUR, result.getCurrency());
        assertMoney(4.2512, result.getExchangeRate());
        assertEquals(LocalDate.of(2026, 10, 30), result.getExchangeRateDate());
        assertEquals("211/A/NBP/2026", result.getExchangeRateTable());
        assertMoney(425.12, result.getTotalValue());
        assertMoney(100.00, result.getTotalValueCurrency());
        verify(auditLogService).logCreate(eq("CompanyExpense"), any(), anyString(),
                argThat(logged -> logged instanceof CompanyExpenseDTO d
                        && d.getCurrency() == ExpenseCurrency.EUR
                        && d.getExchangeRate().compareTo(new BigDecimal("4.2512")) == 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {ExpenseCategoryCode.ZUS, ExpenseCategoryCode.TAXES, ExpenseCategoryCode.SALARY})
    void createExpense_shouldThrowValidationException_whenPlnOnlyCategoryInForeignCurrency(String category) {
        CompanyExpenseDTO dto = foreignExpenseDTO(category, ExpenseCurrency.EUR);

        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.createExpense(dto));

        assertEquals("Ta kategoria kosztu może być tylko w PLN.", ex.getMessage());
        verifyNoInteractions(exchangeRateService);
        verify(companyExpenseRepo, never()).save(any());
    }

    @Test
    void createExpense_shouldDefaultToPlnWithoutRate_whenCurrencyMissing() {
        CompanyExpenseDTO dto = foreignExpenseDTO("OTHER", null);
        dto.setExchangeRate(new BigDecimal("9.9999"));
        stubSave();

        CompanyExpenseDTO result = expenseService.createExpense(dto);

        ArgumentCaptor<CompanyExpense> captor = ArgumentCaptor.forClass(CompanyExpense.class);
        verify(companyExpenseRepo).save(captor.capture());
        CompanyExpense saved = captor.getValue();
        assertEquals(ExpenseCurrency.PLN, saved.getCurrency());
        assertNull(saved.getExchangeRate());
        assertNull(saved.getExchangeRateDate());
        assertNull(saved.getExchangeRateTable());
        assertNull(saved.getTotalValueCurrency());
        assertNull(saved.getTotalVatCurrency());
        assertNull(saved.getTotalNetCurrency());
        assertMoney(100.00, saved.getTotalValue());
        assertEquals(ExpenseCurrency.PLN, result.getCurrency());
        verifyNoInteractions(exchangeRateService);
    }

    @Test
    void createExpense_shouldPropagateServiceUnavailableAndNotSave_whenNbpDown() {
        CompanyExpenseDTO dto = foreignExpenseDTO("OTHER", ExpenseCurrency.USD);
        when(exchangeRateService.resolveRate(ExpenseCurrency.USD, LocalDate.of(2026, 11, 2)))
                .thenThrow(new ExternalServiceUnavailableException("Nie udało się pobrać kursu z NBP. Spróbuj ponownie później."));

        ExternalServiceUnavailableException ex = assertThrows(ExternalServiceUnavailableException.class,
                () -> expenseService.createExpense(dto));

        assertEquals("Nie udało się pobrać kursu z NBP. Spróbuj ponownie później.", ex.getMessage());
        verify(companyExpenseRepo, never()).save(any());
    }

    @Test
    void createExpense_shouldResolveRateBeforeTouchingStock_whenNoRateForForeignProducts() {
        Product product = stockProduct(7L, "Krem", 5);
        CompanyExpenseDTO dto = foreignExpenseDTO(ExpenseCategoryCode.PRODUCTS, ExpenseCurrency.EUR);
        dto.getExpenseItems().get(0).setProductId(7L);
        when(exchangeRateService.resolveRate(ExpenseCurrency.EUR, LocalDate.of(2026, 11, 2)))
                .thenThrow(new ValidationException("Brak kursu NBP EUR w 7 dniach przed 02.11.2026."));

        assertThrows(ValidationException.class, () -> expenseService.createExpense(dto));

        assertEquals(5, product.getSupply());
        verifyNoInteractions(productRepo, supplyHistoryService);
    }

    @Test
    void updateExpense_shouldFetchNewRateAndRecalculate_whenEurDateChanged() {
        CompanyExpense expense = eurExpense();
        assertMoney(984.00, expense.getTotalValue());
        stubExpense(expense);
        stubSave();
        when(exchangeRateService.resolveRate(ExpenseCurrency.EUR, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.of(new ExchangeRateDTO(LocalDate.of(2026, 9, 30), new BigDecimal("4.2512"), "190/A/NBP/2026")));

        CompanyExpenseDTO result = expenseService.updateExpense(5L,
                new CompanyExpenseUpdateDTO(LocalDate.of(2026, 10, 1), "Stary dostawca", "EQUIPMENT", null, null,
                        ExpenseCurrency.EUR));

        assertEquals(ExpenseCurrency.EUR, result.getCurrency());
        assertMoney(4.2512, result.getExchangeRate());
        assertEquals(LocalDate.of(2026, 9, 30), result.getExchangeRateDate());
        assertEquals("190/A/NBP/2026", result.getExchangeRateTable());
        assertMoney(246.00, result.getTotalValueCurrency());
        assertMoney(46.00, result.getTotalVatCurrency());
        assertMoney(200.00, result.getTotalNetCurrency());
        assertMoney(1045.80, result.getTotalValue());
        assertMoney(195.56, result.getTotalVat());
        assertMoney(850.24, result.getTotalNet());
        assertMoney(123.00, expense.getExpenseItems().get(0).getPrice());
        verify(auditLogService).logUpdate(eq("CompanyExpense"), eq(5L), anyString(),
                argThat(old -> old instanceof CompanyExpenseDTO d
                        && d.getExchangeRate().compareTo(new BigDecimal("4.0000")) == 0),
                argThat(updated -> updated instanceof CompanyExpenseDTO d
                        && d.getExchangeRate().compareTo(new BigDecimal("4.2512")) == 0));
    }

    @Test
    void updateExpense_shouldKeepRateAndTotals_whenEurDateAndCurrencyUnchanged() {
        CompanyExpense expense = eurExpense();
        stubExpense(expense);
        stubSave();

        CompanyExpenseDTO result = expenseService.updateExpense(5L,
                new CompanyExpenseUpdateDTO(LocalDate.of(2026, 9, 1), "Nowa nazwa", "EQUIPMENT", null, "FV/1",
                        ExpenseCurrency.EUR));

        verifyNoInteractions(exchangeRateService);
        assertEquals("Nowa nazwa", result.getSource());
        assertMoney(4.0, result.getExchangeRate());
        assertEquals(LocalDate.of(2026, 8, 31), result.getExchangeRateDate());
        assertMoney(984.00, result.getTotalValue());
        assertMoney(184.00, result.getTotalVat());
        assertMoney(800.00, result.getTotalNet());
        assertMoney(246.00, result.getTotalValueCurrency());
    }

    @Test
    void updateExpense_shouldClearRateAndUseItemTotals_whenEurChangedToPln() {
        CompanyExpense expense = eurExpense();
        stubExpense(expense);
        stubSave();

        CompanyExpenseDTO result = expenseService.updateExpense(5L,
                new CompanyExpenseUpdateDTO(LocalDate.of(2026, 9, 1), "Stary dostawca", "EQUIPMENT", null, null,
                        ExpenseCurrency.PLN));

        verifyNoInteractions(exchangeRateService);
        assertEquals(ExpenseCurrency.PLN, result.getCurrency());
        assertNull(result.getExchangeRate());
        assertNull(result.getExchangeRateDate());
        assertNull(result.getExchangeRateTable());
        assertNull(result.getTotalValueCurrency());
        assertNull(result.getTotalVatCurrency());
        assertNull(result.getTotalNetCurrency());
        assertMoney(246.00, result.getTotalValue());
        assertMoney(46.00, result.getTotalVat());
        assertMoney(200.00, result.getTotalNet());
        assertEquals(ExpenseCurrency.PLN, expense.getCurrency());
        assertNull(expense.getExchangeRate());
    }

    @Test
    void updateExpense_shouldFetchRateAndConvert_whenPlnChangedToUsd() {
        stubExpense(editableExpense("EQUIPMENT", null));
        stubSave();
        when(exchangeRateService.resolveRate(ExpenseCurrency.USD, LocalDate.of(2026, 10, 1)))
                .thenReturn(Optional.of(new ExchangeRateDTO(LocalDate.of(2026, 9, 30), new BigDecimal("3.9876"), "190/A/NBP/2026")));

        CompanyExpenseDTO result = expenseService.updateExpense(5L,
                new CompanyExpenseUpdateDTO(LocalDate.of(2026, 10, 1), "Stary dostawca", "EQUIPMENT", null, null,
                        ExpenseCurrency.USD));

        assertEquals(ExpenseCurrency.USD, result.getCurrency());
        assertMoney(246.00, result.getTotalValueCurrency());
        assertMoney(980.95, result.getTotalValue());
        assertMoney(183.43, result.getTotalVat());
        assertMoney(797.52, result.getTotalNet());
    }

    @Test
    void updateExpense_shouldNotCallRateService_whenPlnDateChanged() {
        stubExpense(editableExpense("EQUIPMENT", null));
        stubSave();

        CompanyExpenseDTO result = expenseService.updateExpense(5L,
                new CompanyExpenseUpdateDTO(LocalDate.of(2027, 2, 1), "Stary dostawca", "EQUIPMENT", null, null, null));

        verifyNoInteractions(exchangeRateService);
        assertEquals(ExpenseCurrency.PLN, result.getCurrency());
        assertMoney(246.00, result.getTotalValue());
    }

    @Test
    void updateExpense_shouldThrowValidationException_whenZusChangedToEur() {
        CompanyExpense zus = editableExpense(ExpenseCategoryCode.ZUS, null);
        stubExpense(zus);

        ValidationException ex = assertThrows(ValidationException.class, () -> expenseService.updateExpense(5L,
                new CompanyExpenseUpdateDTO(LocalDate.of(2026, 9, 1), "ZUS", ExpenseCategoryCode.ZUS, null, null,
                        ExpenseCurrency.EUR)));

        assertEquals("Ta kategoria kosztu może być tylko w PLN.", ex.getMessage());
        assertEquals(ExpenseCurrency.PLN, zus.getCurrency());
        verify(companyExpenseRepo, never()).save(any());
        verifyNoInteractions(exchangeRateService);
    }

    @Test
    void getExpensePreview_shouldReturnRateAndBothTotals_whenEur() {
        CompanyExpenseDTO dto = foreignExpenseDTO("OTHER", ExpenseCurrency.EUR);
        dto.setExchangeRate(new BigDecimal("9.9999"));
        when(exchangeRateService.resolveRate(ExpenseCurrency.EUR, LocalDate.of(2026, 11, 2)))
                .thenReturn(Optional.of(EUR_RATE_30_10));

        CompanyExpenseDTO result = expenseService.getExpensePreview(dto);

        assertEquals(ExpenseCurrency.EUR, result.getCurrency());
        assertMoney(4.2512, result.getExchangeRate());
        assertEquals(LocalDate.of(2026, 10, 30), result.getExchangeRateDate());
        assertEquals("211/A/NBP/2026", result.getExchangeRateTable());
        assertMoney(100.00, result.getTotalValueCurrency());
        assertMoney(18.70, result.getTotalVatCurrency());
        assertMoney(81.30, result.getTotalNetCurrency());
        assertMoney(425.12, result.getTotalValue());
        assertMoney(79.50, result.getTotalVat());
        assertMoney(345.62, result.getTotalNet());
        verifyNoInteractions(companyExpenseRepo, productRepo);
    }

    @Test
    void getExpensePreview_shouldReturnPlnWithoutRate_whenCurrencyMissing() {
        CompanyExpenseDTO dto = foreignExpenseDTO("OTHER", null);
        dto.setExchangeRate(new BigDecimal("9.9999"));

        CompanyExpenseDTO result = expenseService.getExpensePreview(dto);

        assertEquals(ExpenseCurrency.PLN, result.getCurrency());
        assertNull(result.getExchangeRate());
        assertNull(result.getTotalValueCurrency());
        assertMoney(100.00, result.getTotalValue());
        verifyNoInteractions(exchangeRateService);
    }

    @Test
    void getExpensePreview_shouldPropagateServiceUnavailable_whenNbpDown() {
        CompanyExpenseDTO dto = foreignExpenseDTO("OTHER", ExpenseCurrency.EUR);
        when(exchangeRateService.resolveRate(ExpenseCurrency.EUR, LocalDate.of(2026, 11, 2)))
                .thenThrow(new ExternalServiceUnavailableException("Nie udało się pobrać kursu z NBP. Spróbuj ponownie później."));

        assertThrows(ExternalServiceUnavailableException.class, () -> expenseService.getExpensePreview(dto));
    }

    private static final Clock RATE_CLOCK = Clock.fixed(Instant.parse("2027-01-15T10:00:00Z"), ZoneId.of("Europe/Warsaw"));
    private static final LocalDate RATE_TODAY = LocalDate.now(RATE_CLOCK);

    private CompanyExpenseServiceImpl serviceWithRealExchangeRates() {
        return new CompanyExpenseServiceImpl(companyExpenseRepo, companyExpenseItemRepo, productRepo, employeeRepo,
                ksefSellerRepo, auditLogService, supplyHistoryService, expenseCategoryService,
                new ExchangeRateServiceImpl(exchangeRateRepo, nbpClient, RATE_CLOCK));
    }

    @Test
    void createExpense_shouldThrowValidationExceptionAndNotSave_whenEurDateIsTomorrow() {
        CompanyExpenseDTO dto = foreignExpenseDTO("OTHER", ExpenseCurrency.EUR);
        dto.setExpenseDate(RATE_TODAY.plusDays(1));

        ValidationException ex = assertThrows(ValidationException.class,
                () -> serviceWithRealExchangeRates().createExpense(dto));

        assertEquals("Kurs NBP nie jest dostępny dla daty z przyszłości.", ex.getMessage());
        verify(companyExpenseRepo, never()).save(any());
        verifyNoInteractions(nbpClient, exchangeRateRepo, auditLogService);
    }

    @Test
    void createExpense_shouldSaveWithoutRate_whenPlnDateIsTomorrow() {
        CompanyExpenseDTO dto = foreignExpenseDTO("OTHER", ExpenseCurrency.PLN);
        dto.setExpenseDate(RATE_TODAY.plusDays(1));
        stubSave();

        CompanyExpenseDTO result = serviceWithRealExchangeRates().createExpense(dto);

        ArgumentCaptor<CompanyExpense> captor = ArgumentCaptor.forClass(CompanyExpense.class);
        verify(companyExpenseRepo).save(captor.capture());
        CompanyExpense saved = captor.getValue();
        assertEquals(ExpenseCurrency.PLN, saved.getCurrency());
        assertEquals(RATE_TODAY.plusDays(1), saved.getExpenseDate());
        assertNull(saved.getExchangeRate());
        assertMoney(100.00, saved.getTotalValue());
        assertEquals(RATE_TODAY.plusDays(1), result.getExpenseDate());
        verifyNoInteractions(nbpClient, exchangeRateRepo);
    }

    @Test
    void updateExpense_shouldThrowValidationExceptionAndNotSave_whenPlnChangedToEurWithTomorrowDate() {
        stubExpense(editableExpense("EQUIPMENT", null));

        ValidationException ex = assertThrows(ValidationException.class, () -> serviceWithRealExchangeRates()
                .updateExpense(5L, new CompanyExpenseUpdateDTO(RATE_TODAY.plusDays(1), "Stary dostawca", "EQUIPMENT",
                        null, null, ExpenseCurrency.EUR)));

        assertEquals("Kurs NBP nie jest dostępny dla daty z przyszłości.", ex.getMessage());
        verify(companyExpenseRepo, never()).save(any());
        verifyNoInteractions(nbpClient, exchangeRateRepo, auditLogService);
    }

    @Test
    void getExpensePreview_shouldReturnPlnTotals_whenPlnDateIsTomorrow() {
        CompanyExpenseDTO dto = foreignExpenseDTO("OTHER", ExpenseCurrency.PLN);
        dto.setExpenseDate(RATE_TODAY.plusDays(1));

        CompanyExpenseDTO result = serviceWithRealExchangeRates().getExpensePreview(dto);

        assertEquals(ExpenseCurrency.PLN, result.getCurrency());
        assertNull(result.getExchangeRate());
        assertMoney(100.00, result.getTotalValue());
        verifyNoInteractions(nbpClient, exchangeRateRepo);
    }
}

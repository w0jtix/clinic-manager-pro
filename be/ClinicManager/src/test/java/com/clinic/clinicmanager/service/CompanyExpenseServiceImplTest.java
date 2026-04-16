package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.CompanyExpenseDTO;
import com.clinic.clinicmanager.DTO.CompanyExpenseItemDTO;
import com.clinic.clinicmanager.DTO.request.CompanyExpenseFilterDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.ExpenseCategory;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.repo.CompanyExpenseItemRepo;
import com.clinic.clinicmanager.repo.CompanyExpenseRepo;
import com.clinic.clinicmanager.repo.OrderRepo;
import com.clinic.clinicmanager.service.impl.CompanyExpenseServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyExpenseServiceImplTest {

    @Mock CompanyExpenseRepo companyExpenseRepo;
    @Mock CompanyExpenseItemRepo companyExpenseItemRepo;
    @Mock OrderRepo orderRepo;
    @Mock AuditLogService auditLogService;

    @InjectMocks
    CompanyExpenseServiceImpl expenseService;

    private CompanyExpense expense(Long id) {
        return CompanyExpense.builder()
                .id(id)
                .source("Dostawca A")
                .expenseDate(LocalDate.of(2025, 4, 14))
                .category(ExpenseCategory.PRODUCTS)
                .build();
    }

    private Order order(Long id, Double shippingCost) {
        Supplier supplier = Supplier.builder().id(1L).name("Dostawca A").build();
        OrderProduct product = OrderProduct.builder()
                .name("Produkt").quantity(2).vatRate(VatRate.VAT_23).price(100.0).build();
        return Order.builder()
                .id(id)
                .supplier(supplier)
                .orderProducts(new ArrayList<>(List.of(product)))
                .shippingCost(shippingCost)
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
        when(companyExpenseRepo.findFirstByCategoryOrderByExpenseDateDesc(ExpenseCategory.PRODUCTS))
                .thenReturn(Optional.of(expense(1L)));

        assertTrue(expenseService.getLatestExpenseByCategory(ExpenseCategory.PRODUCTS).isPresent());
    }

    @Test
    void getLatestExpenseByCategory_shouldReturnEmpty_whenNotFound() {
        when(companyExpenseRepo.findFirstByCategoryOrderByExpenseDateDesc(ExpenseCategory.PRODUCTS))
                .thenReturn(Optional.empty());

        assertTrue(expenseService.getLatestExpenseByCategory(ExpenseCategory.PRODUCTS).isEmpty());
    }

    @Test
    void getExpenses_shouldUseYearAndMonthRange_whenBothProvided() {
        CompanyExpenseFilterDTO filter = new CompanyExpenseFilterDTO();
        filter.setYear(2025);
        filter.setMonth(4);
        when(companyExpenseRepo.findAllWithFilters(any(), any(), any(), any())).thenReturn(Page.empty());

        expenseService.getExpenses(filter, 0, 10);

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(companyExpenseRepo).findAllWithFilters(isNull(), fromCaptor.capture(), toCaptor.capture(), any());
        assertEquals(LocalDate.of(2025, 4, 1), fromCaptor.getValue());
        assertEquals(LocalDate.of(2025, 4, 30), toCaptor.getValue());
    }

    @Test
    void getExpenses_shouldUseCurrentMonthOfYear_whenOnlyYearProvided() {
        CompanyExpenseFilterDTO filter = new CompanyExpenseFilterDTO();
        filter.setYear(2025);
        when(companyExpenseRepo.findAllWithFilters(any(), any(), any(), any())).thenReturn(Page.empty());

        expenseService.getExpenses(filter, 0, 10);

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(companyExpenseRepo).findAllWithFilters(isNull(), fromCaptor.capture(), any(), any());
        assertEquals(2025, fromCaptor.getValue().getYear());
    }

    @Test
    void getExpenses_shouldUseCurrentMonth_whenFilterIsNull() {
        when(companyExpenseRepo.findAllWithFilters(any(), any(), any(), any())).thenReturn(Page.empty());

        expenseService.getExpenses(null, 0, 10);

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(companyExpenseRepo).findAllWithFilters(isNull(), fromCaptor.capture(), any(), any());
        assertEquals(1, fromCaptor.getValue().getDayOfMonth());
    }

    @Test
    void getExpensePreview_shouldCalculateTotalsAndSetOnDTO() {
        CompanyExpenseItemDTO item = new CompanyExpenseItemDTO();
        item.setPrice(100.0);
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
        dto.setCategory(ExpenseCategory.PRODUCTS);
        dto.setExpenseItems(new ArrayList<>());

        when(companyExpenseRepo.save(any())).thenReturn(expense(1L));

        CompanyExpenseDTO result = expenseService.createExpense(dto);

        assertEquals(1L, result.getId());
        verify(auditLogService).logCreate(eq("CompanyExpense"), eq(1L), anyString(), any());
        verify(orderRepo, never()).findOneByIdWithProducts(any());
    }

    @Test
    void createExpense_shouldPopulateFromOrder_whenOrderIdProvided() {
        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setExpenseDate(LocalDate.of(2025, 4, 14));
        dto.setCategory(ExpenseCategory.PRODUCTS);
        dto.setOrderId(5L);
        dto.setExpenseItems(new ArrayList<>());

        when(orderRepo.findOneByIdWithProducts(5L)).thenReturn(Optional.of(order(5L, 0.0)));
        when(companyExpenseRepo.existsByOrderId(5L)).thenReturn(false);
        when(companyExpenseRepo.save(any())).thenReturn(expense(1L));

        expenseService.createExpense(dto);

        ArgumentCaptor<CompanyExpense> captor = ArgumentCaptor.forClass(CompanyExpense.class);
        verify(companyExpenseRepo).save(captor.capture());
        assertEquals("Dostawca A", captor.getValue().getSource());
    }

    @Test
    void createExpense_shouldAddShippingItem_whenShippingCostPositive() {
        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setExpenseDate(LocalDate.of(2025, 4, 14));
        dto.setCategory(ExpenseCategory.PRODUCTS);
        dto.setOrderId(5L);
        dto.setExpenseItems(new ArrayList<>());

        when(orderRepo.findOneByIdWithProducts(5L)).thenReturn(Optional.of(order(5L, 20.0)));
        when(companyExpenseRepo.existsByOrderId(5L)).thenReturn(false);
        when(companyExpenseRepo.save(any())).thenReturn(expense(1L));

        expenseService.createExpense(dto);

        ArgumentCaptor<CompanyExpense> captor = ArgumentCaptor.forClass(CompanyExpense.class);
        verify(companyExpenseRepo).save(captor.capture());
        assertTrue(captor.getValue().getExpenseItems().stream().anyMatch(i -> "Wysyłka".equals(i.getName())));
    }

    @Test
    void createExpense_shouldThrowResourceNotFoundException_whenOrderNotFound() {
        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setOrderId(99L);
        dto.setExpenseItems(new ArrayList<>());

        when(orderRepo.findOneByIdWithProducts(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> expenseService.createExpense(dto));
    }

    @Test
    void createExpense_shouldThrowConflictException_whenOrderAlreadyLinked() {
        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setOrderId(5L);
        dto.setExpenseItems(new ArrayList<>());

        when(orderRepo.findOneByIdWithProducts(5L)).thenReturn(Optional.of(order(5L, 0.0)));
        when(companyExpenseRepo.existsByOrderId(5L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> expenseService.createExpense(dto));
    }

    @Test
    void updateExpense_shouldThrowResourceNotFoundException_whenNotFound() {
        when(companyExpenseRepo.findOneByIdWithItems(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> expenseService.updateExpense(99L, new CompanyExpenseDTO()));
    }

    @Test
    void updateExpense_shouldSaveWithManualItems_whenNoOrderId() {
        CompanyExpense existing = expense(1L);
        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setSource("Nowy dostawca");
        dto.setExpenseDate(LocalDate.of(2025, 4, 20));
        dto.setCategory(ExpenseCategory.RENT);
        dto.setExpenseItems(new ArrayList<>());

        when(companyExpenseRepo.findOneByIdWithItems(1L)).thenReturn(Optional.of(existing));
        when(companyExpenseRepo.save(any())).thenReturn(expense(1L));

        expenseService.updateExpense(1L, dto);

        verify(companyExpenseRepo).save(any());
        verify(auditLogService).logUpdate(eq("CompanyExpense"), eq(1L), anyString(), any(), any());
    }

    @Test
    void updateExpense_shouldThrowConflictException_whenOrderAlreadyLinkedToAnotherExpense() {
        CompanyExpense existing = expense(1L);
        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setOrderId(5L);
        dto.setExpenseItems(new ArrayList<>());

        when(companyExpenseRepo.findOneByIdWithItems(1L)).thenReturn(Optional.of(existing));
        when(orderRepo.findOneByIdWithProducts(5L)).thenReturn(Optional.of(order(5L, 0.0)));
        when(companyExpenseRepo.existsByOrderIdAndIdNot(5L, 1L)).thenReturn(true);

        assertThrows(ConflictException.class, () -> expenseService.updateExpense(1L, dto));
    }

    @Test
    void deleteExpenseById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(companyExpenseRepo.findOneByIdWithItems(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> expenseService.deleteExpenseById(99L));
    }

    @Test
    void deleteExpenseById_shouldDeleteAndAudit() {
        CompanyExpense existing = expense(1L);
        when(companyExpenseRepo.findOneByIdWithItems(1L)).thenReturn(Optional.of(existing));

        expenseService.deleteExpenseById(1L);

        verify(companyExpenseRepo).delete(existing);
        verify(auditLogService).logDelete(eq("CompanyExpense"), eq(1L), anyString(), any());
    }
}

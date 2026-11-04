package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.CompanyExpenseDTO;
import com.clinic.clinicmanager.DTO.CompanyExpenseItemDTO;
import com.clinic.clinicmanager.DTO.ExpenseSellerDTO;
import com.clinic.clinicmanager.DTO.StockChangePreviewDTO;
import com.clinic.clinicmanager.DTO.StockChangeProductDTO;
import com.clinic.clinicmanager.DTO.request.CompanyExpenseFilterDTO;
import com.clinic.clinicmanager.DTO.request.CompanyExpenseUpdateDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ExternalServiceUnavailableException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.StockChangeBlockedException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.constants.ExpenseCategoryCode;
import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import com.clinic.clinicmanager.service.CompanyExpenseService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CompanyExpenseController.class)
@Import(WebSecurityConfig.class)
class CompanyExpenseControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean CompanyExpenseService companyExpenseService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private CompanyExpenseDTO buildExpenseDTO(Long id) {
        CompanyExpenseDTO dto = new CompanyExpenseDTO();
        dto.setId(id);
        return dto;
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getExpenses_shouldReturn200_whenAdmin() throws Exception {
        Page<CompanyExpenseDTO> page = new PageImpl<>(List.of(buildExpenseDTO(1L)));
        when(companyExpenseService.getExpenses(any(), eq(0), eq(30))).thenReturn(page);

        mockMvc.perform(post("/api/company-expenses/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CompanyExpenseFilterDTO())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getExpenses_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/api/company-expenses/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CompanyExpenseFilterDTO())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getExpenseById_shouldReturn200_whenAdmin() throws Exception {
        when(companyExpenseService.getExpenseById(1L)).thenReturn(buildExpenseDTO(1L));

        mockMvc.perform(get("/api/company-expenses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getExpenseById_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/company-expenses/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getExpenseById_shouldReturn404_whenNotFound() throws Exception {
        when(companyExpenseService.getExpenseById(99L))
                .thenThrow(new ResourceNotFoundException("Expense not found with ID: 99"));

        mockMvc.perform(get("/api/company-expenses/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getLatestExpenseByCategory_shouldReturn200_whenFound() throws Exception {
        when(companyExpenseService.getLatestExpenseByCategory(ExpenseCategoryCode.RENT))
                .thenReturn(Optional.of(buildExpenseDTO(1L)));

        mockMvc.perform(get("/api/company-expenses/latest")
                        .param("category", "RENT"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getLatestExpenseByCategory_shouldReturn204_whenNotFound() throws Exception {
        when(companyExpenseService.getLatestExpenseByCategory(any()))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/company-expenses/latest")
                        .param("category", "RENT"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getLatestExpenseByCategory_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/company-expenses/latest")
                        .param("category", "RENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createExpense_shouldReturn201_whenAdmin() throws Exception {
        when(companyExpenseService.createExpense(any())).thenReturn(buildExpenseDTO(1L));

        mockMvc.perform(post("/api/company-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildExpenseDTO(null))))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createExpense_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/api/company-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildExpenseDTO(null))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteExpense_shouldReturn204_whenAdmin() throws Exception {
        mockMvc.perform(delete("/api/company-expenses/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteExpense_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(delete("/api/company-expenses/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createExpense_shouldReturn400WithPlainMessage_whenMixedValidationFails() throws Exception {
        when(companyExpenseService.createExpense(any()))
                .thenThrow(new ValidationException("Niedozwolona kategoria pozycji."));

        mockMvc.perform(post("/api/company-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildExpenseDTO(null))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Niedozwolona kategoria pozycji."));
    }

    private StockChangePreviewDTO blockedPreview() {
        return new StockChangePreviewDTO(false, List.of(
                new StockChangeProductDTO(5L, "Krem X", -3, 1, -2, false, false),
                new StockChangeProductDTO(6L, "Serum", -2, 0, 0, true, true)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteExpense_shouldReturn409WithPreviewBody_whenSupplyInsufficient() throws Exception {
        doThrow(new StockChangeBlockedException("Insufficient supply to remove CompanyExpense with ID: 1",
                blockedPreview()))
                .when(companyExpenseService).deleteExpenseById(1L);

        mockMvc.perform(delete("/api/company-expenses/1"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.canApply").value(false))
                .andExpect(jsonPath("$.products[0].productId").value(5))
                .andExpect(jsonPath("$.products[0].productName").value("Krem X"))
                .andExpect(jsonPath("$.products[0].quantity").value(-3))
                .andExpect(jsonPath("$.products[0].currentSupply").value(1))
                .andExpect(jsonPath("$.products[0].supplyAfter").value(-2))
                .andExpect(jsonPath("$.products[0].sufficient").value(false))
                .andExpect(jsonPath("$.products[0].skipped").value(false))
                .andExpect(jsonPath("$.products[1].skipped").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDeletionPreview_shouldReturn200WithPreview_whenAdmin() throws Exception {
        when(companyExpenseService.getDeletionPreview(1L)).thenReturn(blockedPreview());

        mockMvc.perform(get("/api/company-expenses/1/deletion-preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canApply").value(false))
                .andExpect(jsonPath("$.products.length()").value(2))
                .andExpect(jsonPath("$.products[0].supplyAfter").value(-2))
                .andExpect(jsonPath("$.products[1].sufficient").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDeletionPreview_shouldReturnEmptyProducts_whenExpenseHasNoProducts() throws Exception {
        when(companyExpenseService.getDeletionPreview(1L))
                .thenReturn(new StockChangePreviewDTO(true, List.of()));

        mockMvc.perform(get("/api/company-expenses/1/deletion-preview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canApply").value(true))
                .andExpect(jsonPath("$.products").isEmpty());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getDeletionPreview_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/company-expenses/1/deletion-preview"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getDeletionPreview_shouldReturn404_whenNotFound() throws Exception {
        when(companyExpenseService.getDeletionPreview(99L))
                .thenThrow(new ResourceNotFoundException("Company expense not found with ID: 99"));

        mockMvc.perform(get("/api/company-expenses/99/deletion-preview"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getExpenseById_shouldSerializeItemCategoryAndItemCategories() throws Exception {
        CompanyExpenseItemDTO item = new CompanyExpenseItemDTO();
        item.setCategory("CAR");
        CompanyExpenseDTO dto = buildExpenseDTO(1L);
        dto.setCategory(ExpenseCategoryCode.MIXED);
        dto.setExpenseItems(List.of(item));
        dto.setItemCategories(List.of("CAR", "SUPPLIES"));
        when(companyExpenseService.getExpenseById(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/company-expenses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("MIXED"))
                .andExpect(jsonPath("$.expenseItems[0].category").value("CAR"))
                .andExpect(jsonPath("$.itemCategories[0]").value("CAR"))
                .andExpect(jsonPath("$.itemCategories[1]").value("SUPPLIES"));
    }

    private CompanyExpenseUpdateDTO buildUpdateDTO() {
        return new CompanyExpenseUpdateDTO(LocalDate.of(2026, 10, 1), "Hurtownia X", "EQUIPMENT",
                new ExpenseSellerDTO(null, "5261040828", "Firma", "Adres"), null, null);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateExpense_shouldReturn200WithExpense_whenAdmin() throws Exception {
        CompanyExpenseDTO updated = buildExpenseDTO(1L);
        updated.setSource("Firma");
        updated.setCategory("EQUIPMENT");
        updated.setSellerId(7L);
        updated.setSeller(new ExpenseSellerDTO(7L, "5261040828", "Firma", "Adres"));
        when(companyExpenseService.updateExpense(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/company-expenses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUpdateDTO())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.source").value("Firma"))
                .andExpect(jsonPath("$.seller.nip").value("5261040828"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateExpense_shouldDeserializeRequestBody() throws Exception {
        when(companyExpenseService.updateExpense(eq(1L), any())).thenReturn(buildExpenseDTO(1L));

        mockMvc.perform(put("/api/company-expenses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expenseDate\":\"2026-10-01\",\"source\":\"Hurtownia X\",\"category\":\"SUPPLIES\","
                                + "\"seller\":{\"nip\":\"5261040828\",\"name\":\"Firma\",\"address\":\"Adres\"}}"))
                .andExpect(status().isOk());

        ArgumentCaptor<CompanyExpenseUpdateDTO> captor = ArgumentCaptor.forClass(CompanyExpenseUpdateDTO.class);
        verify(companyExpenseService).updateExpense(eq(1L), captor.capture());
        assertEquals(LocalDate.of(2026, 10, 1), captor.getValue().getExpenseDate());
        assertEquals("SUPPLIES", captor.getValue().getCategory());
        assertEquals("Hurtownia X", captor.getValue().getSource());
        assertEquals("5261040828", captor.getValue().getSeller().getNip());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateExpense_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(put("/api/company-expenses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUpdateDTO())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(companyExpenseService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateExpense_shouldReturn404_whenNotFound() throws Exception {
        when(companyExpenseService.updateExpense(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Company expense not found with ID: 99"));

        mockMvc.perform(put("/api/company-expenses/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUpdateDTO())))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateExpense_shouldReturn409WithPlainMessage_whenCategoryLocked() throws Exception {
        when(companyExpenseService.updateExpense(eq(1L), any()))
                .thenThrow(new ConflictException("Nie można zmienić tej kategorii kosztu."));

        mockMvc.perform(put("/api/company-expenses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUpdateDTO())))
                .andExpect(status().isConflict())
                .andExpect(content().string("Nie można zmienić tej kategorii kosztu."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateExpense_shouldReturn400WithPlainMessage_whenValidationFails() throws Exception {
        when(companyExpenseService.updateExpense(eq(1L), any()))
                .thenThrow(new ValidationException("Niepoprawny NIP sprzedawcy."));

        mockMvc.perform(put("/api/company-expenses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildUpdateDTO())))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Niepoprawny NIP sprzedawcy."));
    }

    @Test
    @WithMockUser(roles = {"MASTER", "COMPANY"})
    void getExpenses_shouldReturn200_whenCompanyMode() throws Exception {
        when(companyExpenseService.getExpenses(any(), eq(0), eq(30))).thenReturn(new PageImpl<>(List.of(buildExpenseDTO(1L))));

        mockMvc.perform(post("/api/company-expenses/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CompanyExpenseFilterDTO())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MASTER")
    void getExpenses_shouldReturn403_whenMasterWithoutCompanyMode() throws Exception {
        mockMvc.perform(post("/api/company-expenses/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CompanyExpenseFilterDTO())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createExpense_shouldReturnInvoiceNumberInResponse() throws Exception {
        CompanyExpenseDTO created = buildExpenseDTO(1L);
        created.setInvoiceNumber("FV/12/2026");
        when(companyExpenseService.createExpense(any())).thenReturn(created);

        mockMvc.perform(post("/api/company-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"Dostawca A\",\"invoiceNumber\":\"FV/12/2026\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceNumber").value("FV/12/2026"));

        ArgumentCaptor<CompanyExpenseDTO> captor = ArgumentCaptor.forClass(CompanyExpenseDTO.class);
        verify(companyExpenseService).createExpense(captor.capture());
        assertEquals("FV/12/2026", captor.getValue().getInvoiceNumber());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createExpense_shouldReturn400WithMessage_whenInvoiceNumberTooLong() throws Exception {
        when(companyExpenseService.createExpense(any()))
                .thenThrow(new ValidationException("Numer faktury może mieć maksymalnie 64 znaki."));
        CompanyExpenseDTO request = buildExpenseDTO(null);
        request.setInvoiceNumber("A".repeat(65));

        mockMvc.perform(post("/api/company-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Numer faktury może mieć maksymalnie 64 znaki."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateExpense_shouldReturnInvoiceNumberInResponse() throws Exception {
        CompanyExpenseDTO updated = buildExpenseDTO(1L);
        updated.setInvoiceNumber("FV/2");
        when(companyExpenseService.updateExpense(eq(1L), any())).thenReturn(updated);

        mockMvc.perform(put("/api/company-expenses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expenseDate\":\"2026-10-01\",\"source\":\"Hurtownia X\",\"category\":\"EQUIPMENT\","
                                + "\"invoiceNumber\":\"FV/2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").value("FV/2"));

        ArgumentCaptor<CompanyExpenseUpdateDTO> captor = ArgumentCaptor.forClass(CompanyExpenseUpdateDTO.class);
        verify(companyExpenseService).updateExpense(eq(1L), captor.capture());
        assertEquals("FV/2", captor.getValue().getInvoiceNumber());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createExpense_shouldReturnCurrencyAndRateFieldsInJson_whenEur() throws Exception {
        CompanyExpenseDTO request = buildExpenseDTO(null);
        request.setCurrency(ExpenseCurrency.EUR);
        request.setExpenseDate(LocalDate.of(2026, 11, 2));
        CompanyExpenseDTO response = buildExpenseDTO(1L);
        response.setCurrency(ExpenseCurrency.EUR);
        response.setExchangeRate(new BigDecimal("4.2512"));
        response.setExchangeRateDate(LocalDate.of(2026, 10, 30));
        response.setExchangeRateTable("211/A/NBP/2026");
        response.setTotalValue(new BigDecimal("425.12"));
        response.setTotalVat(new BigDecimal("79.50"));
        response.setTotalNet(new BigDecimal("345.62"));
        response.setTotalValueCurrency(new BigDecimal("100.00"));
        response.setTotalVatCurrency(new BigDecimal("18.70"));
        response.setTotalNetCurrency(new BigDecimal("81.30"));
        when(companyExpenseService.createExpense(any())).thenReturn(response);

        mockMvc.perform(post("/api/company-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.exchangeRate").value(4.2512))
                .andExpect(jsonPath("$.exchangeRateDate").value("2026-10-30"))
                .andExpect(jsonPath("$.exchangeRateTable").value("211/A/NBP/2026"))
                .andExpect(jsonPath("$.totalValue").value(425.12))
                .andExpect(jsonPath("$.totalValueCurrency").value(100.00))
                .andExpect(jsonPath("$.totalVatCurrency").value(18.70))
                .andExpect(jsonPath("$.totalNetCurrency").value(81.30));

        ArgumentCaptor<CompanyExpenseDTO> captor = ArgumentCaptor.forClass(CompanyExpenseDTO.class);
        verify(companyExpenseService).createExpense(captor.capture());
        assertEquals(ExpenseCurrency.EUR, captor.getValue().getCurrency());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createExpense_shouldReturn400_whenCurrencyUnsupported() throws Exception {
        mockMvc.perform(post("/api/company-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"GBP\",\"expenseItems\":[]}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(companyExpenseService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createExpense_shouldReturn503WithMessage_whenNbpUnavailable() throws Exception {
        when(companyExpenseService.createExpense(any()))
                .thenThrow(new ExternalServiceUnavailableException("Nie udało się pobrać kursu z NBP. Spróbuj ponownie później."));

        mockMvc.perform(post("/api/company-expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildExpenseDTO(null))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("Nie udało się pobrać kursu z NBP. Spróbuj ponownie później."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateExpense_shouldPassCurrencyToService() throws Exception {
        when(companyExpenseService.updateExpense(eq(1L), any())).thenReturn(buildExpenseDTO(1L));

        mockMvc.perform(put("/api/company-expenses/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expenseDate\":\"2026-11-02\",\"source\":\"X\",\"category\":\"OTHER\",\"currency\":\"USD\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<CompanyExpenseUpdateDTO> captor = ArgumentCaptor.forClass(CompanyExpenseUpdateDTO.class);
        verify(companyExpenseService).updateExpense(eq(1L), captor.capture());
        assertEquals(ExpenseCurrency.USD, captor.getValue().getCurrency());
    }
}

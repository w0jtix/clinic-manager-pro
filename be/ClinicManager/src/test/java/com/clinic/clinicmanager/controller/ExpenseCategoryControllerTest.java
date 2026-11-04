package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.CategoryUsageDTO;
import com.clinic.clinicmanager.DTO.ExpenseCategoryDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.ExpenseCategory;
import com.clinic.clinicmanager.service.ExpenseCategoryService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseCategoryController.class)
@Import(WebSecurityConfig.class)
class ExpenseCategoryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ExpenseCategoryService expenseCategoryService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private ExpenseCategoryDTO rent() {
        return new ExpenseCategoryDTO(ExpenseCategory.builder()
                .id(1L).code("RENT").name("Czynsz").color("255,70,162").isSystem(true).isInternal(false)
                .build());
    }

    private String body(String name, String color) throws Exception {
        return objectMapper.writeValueAsString(new ExpenseCategoryDTO(name, color));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getCategories_returnsContractFields() throws Exception {
        when(expenseCategoryService.getCategories()).thenReturn(List.of(rent()));

        mockMvc.perform(get("/api/expense-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].code").value("RENT"))
                .andExpect(jsonPath("$[0].name").value("Czynsz"))
                .andExpect(jsonPath("$[0].color").value("255,70,162"))
                .andExpect(jsonPath("$[0].system").value(true))
                .andExpect(jsonPath("$[0].internal").value(false));
    }

    @Test
    @WithMockUser(roles = {"MASTER", "COMPANY"})
    void getCategories_returns200_forMasterInCompanyMode() throws Exception {
        when(expenseCategoryService.getCategories()).thenReturn(List.of(rent()));

        mockMvc.perform(get("/api/expense-categories")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "MASTER")
    void getCategories_returns403_forMasterOutsideCompanyMode() throws Exception {
        mockMvc.perform(get("/api/expense-categories")).andExpect(status().isForbidden());
        verifyNoInteractions(expenseCategoryService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void getCategories_returns403_forUser() throws Exception {
        mockMvc.perform(get("/api/expense-categories")).andExpect(status().isForbidden());
        verifyNoInteractions(expenseCategoryService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getUsage_returnsCounts() throws Exception {
        when(expenseCategoryService.getCategoryUsage()).thenReturn(List.of(new CategoryUsageDTO(1L, 2L)));

        mockMvc.perform(get("/api/expense-categories/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").value(1))
                .andExpect(jsonPath("$[0].count").value(2));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getUsage_returns403_forUser() throws Exception {
        mockMvc.perform(get("/api/expense-categories/usage")).andExpect(status().isForbidden());
        verifyNoInteractions(expenseCategoryService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCategory_returns201() throws Exception {
        when(expenseCategoryService.createCategory(any())).thenReturn(rent());

        mockMvc.perform(post("/api/expense-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Czynsz", "255,70,162")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("RENT"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createCategory_returns403_forUser() throws Exception {
        mockMvc.perform(post("/api/expense-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Leasing", "1,2,3")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(expenseCategoryService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCategory_returns409_withPolishMessage() throws Exception {
        when(expenseCategoryService.createCategory(any()))
                .thenThrow(new ConflictException("Kategoria o tej nazwie już istnieje."));

        mockMvc.perform(post("/api/expense-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(" czynsz ", "1,2,3")))
                .andExpect(status().isConflict())
                .andExpect(content().string("Kategoria o tej nazwie już istnieje."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCategory_returns400_onValidationError() throws Exception {
        when(expenseCategoryService.createCategory(any()))
                .thenThrow(new ValidationException("Nazwa kategorii jest wymagana."));

        mockMvc.perform(post("/api/expense-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("  ", "1,2,3")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Nazwa kategorii jest wymagana."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateCategory_returns200() throws Exception {
        when(expenseCategoryService.updateCategory(eq(1L), any())).thenReturn(rent());

        mockMvc.perform(put("/api/expense-categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Czynsz", "255,70,162")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RENT"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateCategory_returns403_forUser() throws Exception {
        mockMvc.perform(put("/api/expense-categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Najem", "1,2,3")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(expenseCategoryService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateCategory_returns404_whenMissing() throws Exception {
        when(expenseCategoryService.updateCategory(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Nie znaleziono kategorii o id: 99"));

        mockMvc.perform(put("/api/expense-categories/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Najem", "1,2,3")))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteCategory_returns204() throws Exception {
        mockMvc.perform(delete("/api/expense-categories/14")).andExpect(status().isNoContent());
        verify(expenseCategoryService).deleteCategoryById(14L);
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteCategory_returns403_forUser() throws Exception {
        mockMvc.perform(delete("/api/expense-categories/14")).andExpect(status().isForbidden());
        verifyNoInteractions(expenseCategoryService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteCategory_returns409_whenUsed() throws Exception {
        doThrow(new ConflictException("Kategoria jest używana w wydatkach. Nie można jej usunąć."))
                .when(expenseCategoryService).deleteCategoryById(anyLong());

        mockMvc.perform(delete("/api/expense-categories/8"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Kategoria jest używana w wydatkach. Nie można jej usunąć."));
    }
}

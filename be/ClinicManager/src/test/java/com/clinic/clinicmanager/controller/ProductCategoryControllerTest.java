package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.ProductCategoryDTO;
import com.clinic.clinicmanager.DTO.CategoryUsageDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.service.ProductCategoryService;
import com.clinic.clinicmanager.exceptions.ConflictException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductCategoryController.class)
@Import(WebSecurityConfig.class)
class ProductCategoryControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ProductCategoryService productCategoryService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void createCategory_shouldReturn201_whenAdmin() throws Exception {
        when(productCategoryService.createCategory(any())).thenReturn(new ProductCategoryDTO());

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductCategoryDTO())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createCategory_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductCategoryDTO())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateCategory_shouldReturn200_whenAdmin() throws Exception {
        when(productCategoryService.updateCategory(eq(1L), any())).thenReturn(new ProductCategoryDTO());

        mockMvc.perform(put("/api/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductCategoryDTO())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateCategory_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(put("/api/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductCategoryDTO())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteCategory_shouldReturn204_whenAdmin() throws Exception {
        mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteCategory_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COMPANY")
    void getCategoryUsage_shouldReturn200WithCounts_whenCompany() throws Exception {
        when(productCategoryService.getCategoryUsage()).thenReturn(List.of(new CategoryUsageDTO(1L, 3L), new CategoryUsageDTO(2L, 0L)));

        mockMvc.perform(get("/api/categories/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].categoryId").value(1))
                .andExpect(jsonPath("$[0].count").value(3))
                .andExpect(jsonPath("$[1].categoryId").value(2))
                .andExpect(jsonPath("$[1].count").value(0));

        verify(productCategoryService, never()).getCategoryById(anyLong());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getCategoryUsage_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/categories/usage"))
                .andExpect(status().isForbidden());

        verify(productCategoryService, never()).getCategoryUsage();
        verify(productCategoryService, never()).getCategoryById(anyLong());
    }

    @Test
    @WithMockUser(roles = "COMPANY")
    void deleteCategory_shouldReturn409WithPolishMessage_whenConflict() throws Exception {
        doThrow(new ConflictException("Kategorii systemowej nie można usunąć."))
                .when(productCategoryService).deleteCategoryById(1L);

        mockMvc.perform(delete("/api/categories/1"))
                .andExpect(status().isConflict())
                .andExpect(content().string("Kategorii systemowej nie można usunąć."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getCategoryById_shouldExposeSystemFlagInJson() throws Exception {
        ProductCategoryDTO dto = new ProductCategoryDTO(1L, "Produkty", "0,255,4");
        dto.setSystem(true);
        when(productCategoryService.getCategoryById(1L)).thenReturn(dto);

        mockMvc.perform(get("/api/categories/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.system").value(true))
                .andExpect(jsonPath("$.restored").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "COMPANY")
    void createCategory_shouldReturnRestoredFlag_whenHiddenCategoryRestored() throws Exception {
        ProductCategoryDTO restored = new ProductCategoryDTO(5L, "Jednorazowe", "1,2,3");
        restored.setSystem(false);
        restored.setRestored(true);
        when(productCategoryService.createCategory(any())).thenReturn(restored);

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jednorazowe\",\"color\":\"1,2,3\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.restored").value(true))
                .andExpect(jsonPath("$.system").value(false));
    }
}

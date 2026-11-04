package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.ProductDTO;
import com.clinic.clinicmanager.DTO.SupplyChartDTO;
import com.clinic.clinicmanager.DTO.SupplyHistoryDTO;
import com.clinic.clinicmanager.DTO.SupplySummaryDTO;
import com.clinic.clinicmanager.DTO.request.ProductFilterDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.service.ProductPdfService;
import com.clinic.clinicmanager.service.ProductService;
import com.clinic.clinicmanager.service.SupplyHistoryService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
@Import(WebSecurityConfig.class)
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ProductService productService;
    @MockBean ProductPdfService productPdfService;
    @MockBean SupplyHistoryService supplyHistoryService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private ProductDTO buildProductDTO(Long id) {
        ProductDTO dto = new ProductDTO();
        dto.setId(id);
        dto.setName("Frez 2mm");
        dto.setSupply(10);
        return dto;
    }

    private ProductDTO buildProductWithPurchaseData(Long id) {
        ProductDTO dto = buildProductDTO(id);
        dto.setLowStockThreshold(3);
        dto.setFallbackNetPurchasePrice(new BigDecimal("12.50"));
        dto.setFallbackVatRate(VatRate.VAT_8);
        return dto;
    }

    @Test
    @WithMockUser(roles = "USER")
    void searchProducts_shouldReturn200_whenResultsFound() throws Exception {
        Page<ProductDTO> page = new PageImpl<>(List.of(buildProductDTO(1L), buildProductDTO(2L)));
        when(productService.getProducts(any(ProductFilterDTO.class), eq(0), eq(30))).thenReturn(page);

        mockMvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductFilterDTO())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Frez 2mm"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void searchProducts_shouldReturn204_whenNoResults() throws Exception {
        Page<ProductDTO> emptyPage = new PageImpl<>(Collections.emptyList());
        when(productService.getProducts(any(ProductFilterDTO.class), eq(0), eq(30))).thenReturn(emptyPage);

        mockMvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductFilterDTO())))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getProductById_shouldReturn200_whenFound() throws Exception {
        when(productService.getProductById(1L)).thenReturn(buildProductDTO(1L));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Frez 2mm"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getProductById_shouldMaskPurchaseData_whenUserRole() throws Exception {
        when(productService.getProductById(1L)).thenReturn(buildProductWithPurchaseData(1L));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Frez 2mm"))
                .andExpect(jsonPath("$.lowStockThreshold").value(3))
                .andExpect(jsonPath("$.fallbackNetPurchasePrice").value(nullValue()))
                .andExpect(jsonPath("$.fallbackVatRate").value(nullValue()));
    }

    @Test
    @WithMockUser(roles = "MASTER")
    void searchProducts_shouldMaskPurchaseData_whenMasterRole() throws Exception {
        Page<ProductDTO> page = new PageImpl<>(List.of(buildProductWithPurchaseData(1L)));
        when(productService.getProducts(any(ProductFilterDTO.class), eq(0), eq(30))).thenReturn(page);

        mockMvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductFilterDTO())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fallbackNetPurchasePrice").value(nullValue()))
                .andExpect(jsonPath("$.content[0].fallbackVatRate").value(nullValue()));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateProduct_shouldMaskPurchaseDataInResponse_whenUserRole() throws Exception {
        when(productService.updateProduct(eq(1L), any(ProductDTO.class))).thenReturn(buildProductWithPurchaseData(1L));

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildProductDTO(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallbackNetPurchasePrice").value(nullValue()))
                .andExpect(jsonPath("$.fallbackVatRate").value(nullValue()));
    }

    @Test
    @WithMockUser(roles = "COMPANY")
    void getProductById_shouldReturnPurchaseData_whenCompanyRole() throws Exception {
        when(productService.getProductById(1L)).thenReturn(buildProductWithPurchaseData(1L));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fallbackNetPurchasePrice").value(12.5))
                .andExpect(jsonPath("$.fallbackVatRate").value("VAT_8"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void searchProducts_shouldReturnPurchaseData_whenAdminRole() throws Exception {
        Page<ProductDTO> page = new PageImpl<>(List.of(buildProductWithPurchaseData(1L)));
        when(productService.getProducts(any(ProductFilterDTO.class), eq(0), eq(30))).thenReturn(page);

        mockMvc.perform(post("/api/products/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductFilterDTO())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].fallbackNetPurchasePrice").value(12.5))
                .andExpect(jsonPath("$.content[0].fallbackVatRate").value("VAT_8"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getProductById_shouldReturn404_whenNotFound() throws Exception {
        when(productService.getProductById(99L))
                .thenThrow(new ResourceNotFoundException("Product not found with ID: 99"));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createProduct_shouldReturn201_whenValid() throws Exception {
        ProductDTO input = buildProductDTO(null);
        ProductDTO saved = buildProductDTO(1L);
        when(productService.createProduct(any(ProductDTO.class))).thenReturn(saved);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Frez 2mm"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateProduct_shouldReturn200_whenValid() throws Exception {
        ProductDTO input = buildProductDTO(null);
        ProductDTO saved = buildProductDTO(1L);
        when(productService.updateProduct(eq(1L), any(ProductDTO.class))).thenReturn(saved);

        mockMvc.perform(put("/api/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteProduct_shouldReturn204_whenDeleted() throws Exception {
        mockMvc.perform(delete("/api/products/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void generateInventoryReport_shouldReturn200WithPdfContent() throws Exception {
        byte[] pdfBytes = "PDF_CONTENT".getBytes();
        when(productPdfService.generateInventoryReport(any())).thenReturn(pdfBytes);

        mockMvc.perform(post("/api/products/inventory-report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductFilterDTO())))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(content().bytes(pdfBytes));
    }

    private SupplyHistoryDTO buildSupplyHistoryDTO(Long id) {
        return new SupplyHistoryDTO(id, 5, 15, SupplyChangeSource.KSEF_INVOICE, 1L, "FV/1",
                LocalDateTime.now(), "wojtek", true);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSupplyHistory_shouldReturn200_whenAdminAndResultsFound() throws Exception {
        Page<SupplyHistoryDTO> page = new PageImpl<>(List.of(buildSupplyHistoryDTO(1L)));
        when(supplyHistoryService.getHistory(eq(1L), isNull(), eq(PageRequest.of(0, 30)))).thenReturn(page);

        mockMvc.perform(get("/api/products/1/supply-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSupplyHistory_shouldPassCommaSeparatedSourcesAsEnumList() throws Exception {
        Page<SupplyHistoryDTO> page = new PageImpl<>(List.of(buildSupplyHistoryDTO(1L)));
        List<SupplyChangeSource> expectedSources = List.of(
                SupplyChangeSource.KSEF_INVOICE, SupplyChangeSource.KSEF_CORRECTION);
        when(supplyHistoryService.getHistory(eq(1L), eq(expectedSources), eq(PageRequest.of(0, 30))))
                .thenReturn(page);

        mockMvc.perform(get("/api/products/1/supply-history")
                        .param("sources", "KSEF_INVOICE,KSEF_CORRECTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));

        verify(supplyHistoryService).getHistory(1L, expectedSources, PageRequest.of(0, 30));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSupplyHistory_shouldReturn400_whenSourceValueIsUnknown() throws Exception {
        mockMvc.perform(get("/api/products/1/supply-history")
                        .param("sources", "NIE_ISTNIEJE"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(supplyHistoryService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void getSupplyHistory_shouldReturn403_whenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/products/1/supply-history"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(supplyHistoryService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSupplyHistory_shouldReturn404_whenProductNotFound() throws Exception {
        when(supplyHistoryService.getHistory(eq(99L), isNull(), eq(PageRequest.of(0, 30))))
                .thenThrow(new ResourceNotFoundException("Product not found with given id: 99"));

        mockMvc.perform(get("/api/products/99/supply-history"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSupplyChart_shouldReturn200_whenAdmin() throws Exception {
        SupplyChartDTO chart = new SupplyChartDTO(10, 2026, 2026, List.of());
        when(supplyHistoryService.getChart(1L, 2026)).thenReturn(chart);

        mockMvc.perform(get("/api/products/1/supply-chart").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openingSupply").value(10));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getSupplyChart_shouldReturn403_whenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/products/1/supply-chart"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(supplyHistoryService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSupplyChart_shouldReturn404_whenProductNotFound() throws Exception {
        when(supplyHistoryService.getChart(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("Product not found with given id: 99"));

        mockMvc.perform(get("/api/products/99/supply-chart"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSupplySummary_shouldReturn200_whenAdmin() throws Exception {
        SupplySummaryDTO summary = new SupplySummaryDTO(10, 20, -5, -3, -2,
                BigDecimal.valueOf(15.0), false, BigDecimal.valueOf(150.0));
        when(supplyHistoryService.getSummary(1L)).thenReturn(summary);

        mockMvc.perform(get("/api/products/1/supply-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSupply").value(10));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getSupplySummary_shouldReturn403_whenNotAdmin() throws Exception {
        mockMvc.perform(get("/api/products/1/supply-summary"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(supplyHistoryService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getSupplySummary_shouldReturn404_whenProductNotFound() throws Exception {
        when(supplyHistoryService.getSummary(99L))
                .thenThrow(new ResourceNotFoundException("Product not found with given id: 99"));

        mockMvc.perform(get("/api/products/99/supply-summary"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createProduct_shouldReturn400WithoutCauseDetails_whenCreationFails() throws Exception {
        when(productService.createProduct(any(ProductDTO.class)))
                .thenThrow(new CreationException("Failed to create Product.", new RuntimeException("SQL secret")));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildProductDTO(null))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Failed to create Product."))
                .andExpect(content().string(not(containsString("SQL secret"))));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createProduct_shouldReturn409WithAlreadyExistsMessage_whenProductNameTaken() throws Exception {
        when(productService.createProduct(any(ProductDTO.class)))
                .thenThrow(new ConflictException("Product already exists: Frez 2mm"));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildProductDTO(null))))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("already exists")));
    }
}

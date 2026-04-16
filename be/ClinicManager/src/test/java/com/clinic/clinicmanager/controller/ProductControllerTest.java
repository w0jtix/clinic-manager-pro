package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.ProductDTO;
import com.clinic.clinicmanager.DTO.request.ProductFilterDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.service.ProductPdfService;
import com.clinic.clinicmanager.service.ProductService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
}

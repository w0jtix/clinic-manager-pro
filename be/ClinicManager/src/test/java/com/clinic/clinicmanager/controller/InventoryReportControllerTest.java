package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.InventoryReportDTO;
import com.clinic.clinicmanager.DTO.InventoryReportItemDTO;
import com.clinic.clinicmanager.DTO.ProductDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.model.constants.VatRate;
import com.clinic.clinicmanager.service.InventoryReportService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryReportController.class)
@Import(WebSecurityConfig.class)
class InventoryReportControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean InventoryReportService inventoryReportService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private InventoryReportDTO reportWithPricedProduct() {
        ProductDTO product = new ProductDTO();
        product.setId(7L);
        product.setName("Frez 2mm");
        product.setFallbackNetPurchasePrice(new BigDecimal("12.50"));
        product.setFallbackVatRate(VatRate.VAT_8);

        InventoryReportItemDTO item = new InventoryReportItemDTO();
        item.setId(1L);
        item.setProduct(product);
        item.setSupplyBefore(4);
        item.setSupplyAfter(3);

        InventoryReportDTO report = new InventoryReportDTO();
        report.setId(1L);
        report.setItems(List.of(item));
        return report;
    }

    @Test
    @WithMockUser(roles = "USER")
    void getReportById_shouldMaskNestedProductPurchaseData_whenUserRole() throws Exception {
        when(inventoryReportService.getReportById(1L)).thenReturn(reportWithPricedProduct());

        mockMvc.perform(get("/api/inventory-reports/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].product.name").value("Frez 2mm"))
                .andExpect(jsonPath("$.items[0].supplyAfter").value(3))
                .andExpect(jsonPath("$.items[0].product.fallbackNetPurchasePrice").value(nullValue()))
                .andExpect(jsonPath("$.items[0].product.fallbackVatRate").value(nullValue()));
    }

    @Test
    @WithMockUser(roles = "COMPANY")
    void getReportById_shouldReturnNestedProductPurchaseData_whenCompanyRole() throws Exception {
        when(inventoryReportService.getReportById(1L)).thenReturn(reportWithPricedProduct());

        mockMvc.perform(get("/api/inventory-reports/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].product.fallbackNetPurchasePrice").value(12.5))
                .andExpect(jsonPath("$.items[0].product.fallbackVatRate").value("VAT_8"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void approveReport_shouldReturn200_whenAdmin() throws Exception {
        when(inventoryReportService.approveReport(1L)).thenReturn(new InventoryReportDTO());

        mockMvc.perform(patch("/api/inventory-reports/1/approve"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void approveReport_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(patch("/api/inventory-reports/1/approve"))
                .andExpect(status().isForbidden());
    }
}

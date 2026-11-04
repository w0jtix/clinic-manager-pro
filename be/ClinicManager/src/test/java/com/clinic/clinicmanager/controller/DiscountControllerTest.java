package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.DiscountDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.service.DiscountService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DiscountController.class)
@Import(WebSecurityConfig.class)
class DiscountControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean DiscountService discountService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteDiscount_shouldReturn204_whenAdmin() throws Exception {
        mockMvc.perform(delete("/api/discounts/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteDiscount_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(delete("/api/discounts/1"))
                .andExpect(status().isForbidden());
    }

    private DiscountDTO discount(Long id) {
        DiscountDTO dto = new DiscountDTO();
        dto.setId(id);
        dto.setName("VIP10");
        dto.setPercentageValue(10);
        return dto;
    }

    @Test
    @WithMockUser(roles = "USER")
    void createDiscount_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/api/discounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(discount(null))))
                .andExpect(status().isForbidden());

        verify(discountService, never()).createDiscount(any());
    }

    @Test
    @WithMockUser(roles = "MASTER")
    void createDiscount_shouldReturn201_whenMaster() throws Exception {
        when(discountService.createDiscount(any())).thenReturn(discount(1L));

        mockMvc.perform(post("/api/discounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(discount(null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("VIP10"))
                .andExpect(jsonPath("$.percentageValue").value(10));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateDiscount_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(put("/api/discounts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(discount(1L))))
                .andExpect(status().isForbidden());

        verify(discountService, never()).updateDiscount(any(), any());
    }

    @Test
    @WithMockUser(roles = "MASTER")
    void updateDiscount_shouldReturn200_whenMaster() throws Exception {
        when(discountService.updateDiscount(eq(1L), any())).thenReturn(discount(1L));

        mockMvc.perform(put("/api/discounts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(discount(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("VIP10"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getDiscounts_shouldReturn200_whenUserRole() throws Exception {
        when(discountService.getDiscounts()).thenReturn(List.of(discount(1L)));

        mockMvc.perform(get("/api/discounts/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].percentageValue").value(10));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getDiscountById_shouldReturn200_whenUserRole() throws Exception {
        when(discountService.getDiscountById(1L)).thenReturn(discount(1L));

        mockMvc.perform(get("/api/discounts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("VIP10"));
    }
}

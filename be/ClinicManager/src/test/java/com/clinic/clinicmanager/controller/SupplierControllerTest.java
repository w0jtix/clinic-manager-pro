package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.SupplierDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.service.SupplierService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SupplierController.class)
@Import(WebSecurityConfig.class)
class SupplierControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    SupplierService supplierService;

    @MockBean
    UserDetailsServiceImpl userDetailsService;

    @MockBean
    AuthEntryPointJwt authEntryPointJwt;

    @MockBean
    JwtUtils jwtUtils;

    @MockBean
    TokenBlacklistService tokenBlacklistService;

    @Test
    @WithMockUser
    void getSuppliers_shouldReturn200_whenSuppliersExist() throws Exception {
        List<SupplierDTO> suppliers = List.of(
                new SupplierDTO(1L, "Dostawca A", "https://a.com"),
                new SupplierDTO(2L, "Dostawca B", "https://b.com")
        );
        when(supplierService.getSuppliers()).thenReturn(suppliers);

        mockMvc.perform(post("/api/suppliers/search")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Dostawca A"));
    }

    @Test
    @WithMockUser
    void getSuppliers_shouldReturn204_whenNoSuppliers() throws Exception {
        when(supplierService.getSuppliers()).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/suppliers/search")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void getSupplierById_shouldReturn200WithBody_whenFound() throws Exception {
        SupplierDTO supplier = new SupplierDTO(1L, "Dostawca A", "https://a.com");
        when(supplierService.getSupplierById(1L)).thenReturn(supplier);

        mockMvc.perform(get("/api/suppliers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Dostawca A"))
                .andExpect(jsonPath("$.websiteUrl").value("https://a.com"));
    }

    @Test
    @WithMockUser
    void getSupplierById_shouldReturn404_whenNotFound() throws Exception {
        when(supplierService.getSupplierById(99L))
                .thenThrow(new ResourceNotFoundException("Supplier not found with ID: 99"));

        mockMvc.perform(get("/api/suppliers/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void createSupplier_shouldReturn201WithBody_whenValid() throws Exception {
        SupplierDTO inputDTO = new SupplierDTO(null, "Nowy Dostawca", "https://new.com");
        SupplierDTO savedDTO = new SupplierDTO(3L, "Nowy Dostawca", "https://new.com");
        when(supplierService.createSupplier(any(SupplierDTO.class))).thenReturn(savedDTO);

        mockMvc.perform(post("/api/suppliers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Nowy Dostawca"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateSupplier_shouldReturn200_whenAdmin() throws Exception {
        SupplierDTO inputDTO = new SupplierDTO(null, "Zaktualizowany", "https://updated.com");
        SupplierDTO savedDTO = new SupplierDTO(1L, "Zaktualizowany", "https://updated.com");
        when(supplierService.updateSupplier(eq(1L), any(SupplierDTO.class))).thenReturn(savedDTO);

        mockMvc.perform(put("/api/suppliers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Zaktualizowany"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateSupplier_shouldReturn403_whenUserRole() throws Exception {
        SupplierDTO inputDTO = new SupplierDTO(null, "Zaktualizowany", "https://updated.com");

        mockMvc.perform(put("/api/suppliers/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDTO)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void deleteSupplier_shouldReturn204_whenAdmin() throws Exception {
        mockMvc.perform(delete("/api/suppliers/1"))
                .andExpect(status().isNoContent());

        verify(supplierService).deleteSupplierById(1L);
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteSupplier_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(delete("/api/suppliers/1"))
                .andExpect(status().isForbidden());
    }
}

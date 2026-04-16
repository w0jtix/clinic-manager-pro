package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.BrandDTO;
import com.clinic.clinicmanager.DTO.request.KeywordFilterDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.service.BrandService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(BrandController.class)
@Import(WebSecurityConfig.class)
public class BrandControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    BrandService brandService;

    @MockBean
    UserDetailsServiceImpl userDetailsService;

    @MockBean
    AuthEntryPointJwt authEntryPointJwt;

    @MockBean
    JwtUtils jwtUtils;

    @MockBean
    TokenBlacklistService tokenBlacklistService;

    @Test
    @WithMockUser(roles = "USER")
    void getBrands_shouldReturn200_whenExists() throws Exception {
        List<BrandDTO> brandList = List.of(
        new BrandDTO(1L, "Brand1"),
        new BrandDTO(2L, "Brand2"));

        KeywordFilterDTO filter = new KeywordFilterDTO();
        filter.setKeyword("");

        when(brandService.getBrands(any())).thenReturn(brandList);

        mockMvc.perform(post("/api/brands/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(filter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Brand1"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getBrands_shouldReturn204_whenNoBrands() throws Exception {
        KeywordFilterDTO filter = new KeywordFilterDTO();
        filter.setKeyword("");

        when(brandService.getBrands(any())).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/brands/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(filter)))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getBrandById_shouldReturn200WithBody_whenBrandExists() throws Exception {
        BrandDTO brand = new BrandDTO(3L, "Brand3");

        when(brandService.getBrandById(3L)).thenReturn(brand);

        mockMvc.perform(get("/api/brands/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Brand3"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getBrandById_shouldReturn204_whenBrandNotFound() throws Exception {

        when(brandService.getBrandById(99L))
                .thenThrow(new ResourceNotFoundException("Brand not found with Id: 99"));

        mockMvc.perform(get("/api/brands/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createBrand_shouldReturn201WithBody_whenValid() throws Exception {
        BrandDTO inputDTO = new BrandDTO(null, "Brand4");
        BrandDTO savedBrand = new BrandDTO(4L, "Brand4");

        when(brandService.createBrand(any(BrandDTO.class))).thenReturn(savedBrand);

        mockMvc.perform(post("/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.name").value("Brand4"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createBrand_shouldReturn400_whenCreationFails() throws Exception {
        BrandDTO inputDTO = new BrandDTO(null, "Brand4");

        when(brandService.createBrand(any(BrandDTO.class)))
                .thenThrow(new CreationException("Failed to create Brand."));

        mockMvc.perform(post("/api/brands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inputDTO)))
                .andExpect(status().isBadRequest());
    }
}

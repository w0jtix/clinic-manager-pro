package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.OrderDTO;
import com.clinic.clinicmanager.DTO.request.OrderFilterDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.service.OrderService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(WebSecurityConfig.class)
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean OrderService orderService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private OrderDTO buildOrderDTO(Long id) {
        OrderDTO dto = new OrderDTO();
        dto.setId(id);
        dto.setOrderNumber(100L);
        return dto;
    }

    @Test
    @WithMockUser(roles = "USER")
    void getOrders_shouldReturn200_whenResultsFound() throws Exception {
        Page<OrderDTO> page = new PageImpl<>(List.of(buildOrderDTO(1L), buildOrderDTO(2L)));
        when(orderService.getOrders(any(OrderFilterDTO.class), eq(0), eq(30))).thenReturn(page);

        mockMvc.perform(post("/api/orders/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderFilterDTO())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getOrders_shouldReturn204_whenNoResults() throws Exception {
        Page<OrderDTO> emptyPage = new PageImpl<>(Collections.emptyList());
        when(orderService.getOrders(any(OrderFilterDTO.class), eq(0), eq(30))).thenReturn(emptyPage);

        mockMvc.perform(post("/api/orders/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OrderFilterDTO())))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getOrderById_shouldReturn200_whenFound() throws Exception {
        when(orderService.getOrderById(1L)).thenReturn(buildOrderDTO(1L));

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.orderNumber").value(100));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getOrderById_shouldReturn404_whenNotFound() throws Exception {
        when(orderService.getOrderById(99L))
                .thenThrow(new ResourceNotFoundException("Order not found with ID: 99"));

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getOrderPreview_shouldReturn201_whenValid() throws Exception {
        OrderDTO input = buildOrderDTO(null);
        when(orderService.getOrderPreview(any(OrderDTO.class))).thenReturn(buildOrderDTO(null));

        mockMvc.perform(post("/api/orders/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getOrderPreview_shouldReturn404_whenSupplierNotFound() throws Exception {
        when(orderService.getOrderPreview(any(OrderDTO.class)))
                .thenThrow(new ResourceNotFoundException("Supplier not found"));

        mockMvc.perform(post("/api/orders/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildOrderDTO(null))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createOrder_shouldReturn201_whenValid() throws Exception {
        when(orderService.createOrder(any(OrderDTO.class))).thenReturn(buildOrderDTO(1L));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildOrderDTO(null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createOrder_shouldReturn400_whenCreationFails() throws Exception {
        when(orderService.createOrder(any(OrderDTO.class)))
                .thenThrow(new CreationException("Failed to create order"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildOrderDTO(null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateOrder_shouldReturn200_whenValid() throws Exception {
        when(orderService.updateOrder(eq(1L), any(OrderDTO.class))).thenReturn(buildOrderDTO(1L));

        mockMvc.perform(put("/api/orders/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildOrderDTO(null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateOrder_shouldReturn400_whenUpdateFails() throws Exception {
        when(orderService.updateOrder(eq(1L), any(OrderDTO.class)))
                .thenThrow(new UpdateException("Failed to update order"));

        mockMvc.perform(put("/api/orders/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildOrderDTO(null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteOrder_shouldReturn204_whenDeleted() throws Exception {
        mockMvc.perform(delete("/api/orders/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteOrder_shouldReturn400_whenDeletionFails() throws Exception {
        doThrow(new DeletionException("Failed to delete order"))
                .when(orderService).deleteOrderById(1L);

        mockMvc.perform(delete("/api/orders/1"))
                .andExpect(status().isBadRequest());
    }
}

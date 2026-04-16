package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.DTO.request.ClientFilterDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.service.ClientService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ClientController.class)
@Import(WebSecurityConfig.class)
class ClientControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ClientService clientService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private ClientDTO buildClientDTO(Long id) {
        ClientDTO dto = new ClientDTO();
        dto.setId(id);
        dto.setFirstName("Jan");
        dto.setLastName("Kowalski");
        dto.setIsDeleted(false);
        return dto;
    }

    @Test
    @WithMockUser(roles = "USER")
    void getClientById_shouldReturn200_whenFound() throws Exception {
        when(clientService.getClientById(1L)).thenReturn(buildClientDTO(1L));

        mockMvc.perform(get("/api/clients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Jan"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getClientById_shouldReturn404_whenNotFound() throws Exception {
        when(clientService.getClientById(99L))
                .thenThrow(new ResourceNotFoundException("Client not found with ID: 99"));

        mockMvc.perform(get("/api/clients/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "USER")
    void getClients_shouldReturn200_whenResultsFound() throws Exception {
        when(clientService.getClients(any(ClientFilterDTO.class)))
                .thenReturn(List.of(buildClientDTO(1L), buildClientDTO(2L)));

        mockMvc.perform(post("/api/clients/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClientFilterDTO())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getClients_shouldReturn204_whenNoResults() throws Exception {
        when(clientService.getClients(any())).thenReturn(Collections.emptyList());

        mockMvc.perform(post("/api/clients/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ClientFilterDTO())))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void createClient_shouldReturn201_whenValid() throws Exception {
        ClientDTO input = buildClientDTO(null);
        ClientDTO saved = buildClientDTO(1L);
        when(clientService.createClient(any(ClientDTO.class))).thenReturn(saved);

        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firstName").value("Jan"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void createClient_shouldReturn400_whenCreationFails() throws Exception {
        when(clientService.createClient(any(ClientDTO.class)))
                .thenThrow(new CreationException("Failed to create client"));

        mockMvc.perform(post("/api/clients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildClientDTO(null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateClient_shouldReturn200_whenValid() throws Exception {
        ClientDTO input = buildClientDTO(null);
        ClientDTO saved = buildClientDTO(1L);
        when(clientService.updateClient(eq(1L), any(ClientDTO.class))).thenReturn(saved);

        mockMvc.perform(put("/api/clients/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void updateClient_shouldReturn400_whenUpdateFails() throws Exception {
        when(clientService.updateClient(eq(1L), any(ClientDTO.class)))
                .thenThrow(new UpdateException("Failed to update client"));

        mockMvc.perform(put("/api/clients/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildClientDTO(null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteClient_shouldReturn204_whenDeleted() throws Exception {
        mockMvc.perform(delete("/api/clients/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "USER")
    void deleteClient_shouldReturn400_whenDeletionFails() throws Exception {
        doThrow(new DeletionException("Failed to delete client"))
                .when(clientService).deleteClientById(1L);

        mockMvc.perform(delete("/api/clients/1"))
                .andExpect(status().isBadRequest());
    }
}

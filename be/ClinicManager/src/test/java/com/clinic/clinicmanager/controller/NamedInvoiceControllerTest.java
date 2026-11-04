package com.clinic.clinicmanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.NamedInvoiceDTO;
import com.clinic.clinicmanager.DTO.request.SendNamedInvoiceRequest;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.EmailSendException;
import com.clinic.clinicmanager.exceptions.GlobalExceptionHandler;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.service.NamedInvoiceEmailService;
import com.clinic.clinicmanager.service.NamedInvoicePdfService;
import com.clinic.clinicmanager.service.NamedInvoiceService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NamedInvoiceController.class)
@Import({WebSecurityConfig.class, GlobalExceptionHandler.class, AuthEntryPointJwt.class})
class NamedInvoiceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean NamedInvoiceService namedInvoiceService;
    @MockBean NamedInvoicePdfService namedInvoicePdfService;
    @MockBean NamedInvoiceEmailService namedInvoiceEmailService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private NamedInvoiceDTO invoiceDTO() {
        NamedInvoiceDTO dto = new NamedInvoiceDTO();
        dto.setId(5L);
        dto.setInvoiceNumber("1/02/2026");
        return dto;
    }

    private SendNamedInvoiceRequest sendRequest(String email, boolean updateClientEmail) {
        SendNamedInvoiceRequest request = new SendNamedInvoiceRequest();
        request.setEmail(email);
        request.setUpdateClientEmail(updateClientEmail);
        return request;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getById_shouldReturn200_whenAdmin() throws Exception {
        when(namedInvoiceService.getById(5L)).thenReturn(invoiceDTO());

        mockMvc.perform(get("/api/named-invoices/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").value("1/02/2026"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getById_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/named-invoices/5"))
                .andExpect(status().isForbidden());

        verify(namedInvoiceService, never()).getById(any());
    }

    @Test
    void getById_shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/named-invoices/5"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getById_shouldReturn404_whenInvoiceDoesNotExist() throws Exception {
        when(namedInvoiceService.getById(5L)).thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/api/named-invoices/5"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void delete_shouldReturn204_whenAdmin() throws Exception {
        mockMvc.perform(delete("/api/named-invoices/5"))
                .andExpect(status().isNoContent());

        verify(namedInvoiceService).delete(5L);
    }

    @Test
    @WithMockUser(roles = "USER")
    void delete_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(delete("/api/named-invoices/5"))
                .andExpect(status().isForbidden());

        verify(namedInvoiceService, never()).delete(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void byPeriod_shouldPassYearAndMonth() throws Exception {
        when(namedInvoiceService.getByPeriod(2026, 2)).thenReturn(java.util.List.of(invoiceDTO()));

        mockMvc.perform(get("/api/named-invoices/by-period").param("year", "2026").param("month", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5));

        verify(namedInvoiceService).getByPeriod(2026, 2);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void send_shouldReturn200_andPassRequestToService() throws Exception {
        NamedInvoiceDTO sent = invoiceDTO();
        sent.setSentTo("klient@example.com");
        sent.setEmailSentAt(LocalDateTime.of(2026, 2, 1, 12, 0));
        when(namedInvoiceEmailService.send(eq(5L), any(SendNamedInvoiceRequest.class))).thenReturn(sent);

        mockMvc.perform(post("/api/named-invoices/5/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(sendRequest("klient@example.com", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo").value("klient@example.com"))
                .andExpect(jsonPath("$.emailSentAt").exists());

        verify(namedInvoiceEmailService).send(eq(5L), any(SendNamedInvoiceRequest.class));
    }

    @Test
    @WithMockUser(roles = "USER")
    void send_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(post("/api/named-invoices/5/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(sendRequest("klient@example.com", false))))
                .andExpect(status().isForbidden());

        verify(namedInvoiceEmailService, never()).send(any(), any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void send_shouldReturn400_whenEmailIsRejected() throws Exception {
        when(namedInvoiceEmailService.send(eq(5L), any(SendNamedInvoiceRequest.class)))
                .thenThrow(new ValidationException("Niepoprawny adres email."));

        mockMvc.perform(post("/api/named-invoices/5/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(sendRequest("zly-adres", false))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void send_shouldReturn400WithReason_whenDeliveryFails() throws Exception {
        when(namedInvoiceEmailService.send(eq(5L), any(SendNamedInvoiceRequest.class)))
                .thenThrow(new EmailSendException("Nie udało się wysłać faktury."));

        mockMvc.perform(post("/api/named-invoices/5/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(sendRequest("klient@example.com", false))))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nie udało się wysłać faktury")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void pdf_shouldReturnAttachmentWithPdfContentType() throws Exception {
        byte[] content = "pdf".getBytes(StandardCharsets.UTF_8);
        when(namedInvoicePdfService.generateInvoicePdf(5L)).thenReturn(content);

        mockMvc.perform(get("/api/named-invoices/5/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("faktura-5.pdf")))
                .andExpect(content().bytes(content));
    }

    @Test
    @WithMockUser(roles = "USER")
    void pdf_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/named-invoices/5/pdf"))
                .andExpect(status().isForbidden());

        verify(namedInvoicePdfService, never()).generateInvoicePdf(any());
    }
}

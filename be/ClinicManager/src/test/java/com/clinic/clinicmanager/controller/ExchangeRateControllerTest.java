package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.ExchangeRateDTO;
import com.clinic.clinicmanager.DTO.ExchangeRateWindowDTO;
import com.clinic.clinicmanager.config.security.WebSecurityConfig;
import com.clinic.clinicmanager.config.security.jwt.AuthEntryPointJwt;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.exceptions.ExternalServiceUnavailableException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import com.clinic.clinicmanager.service.ExchangeRateService;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ExchangeRateController.class)
@Import(WebSecurityConfig.class)
class ExchangeRateControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean ExchangeRateService exchangeRateService;
    @MockBean UserDetailsServiceImpl userDetailsService;
    @MockBean AuthEntryPointJwt authEntryPointJwt;
    @MockBean JwtUtils jwtUtils;
    @MockBean TokenBlacklistService tokenBlacklistService;

    private static ExchangeRateWindowDTO eurWindow() {
        ExchangeRateDTO selected = new ExchangeRateDTO(LocalDate.of(2026, 10, 30), new BigDecimal("4.2512"), "211/A/NBP/2026");
        return new ExchangeRateWindowDTO(
                ExpenseCurrency.EUR,
                LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 10, 26),
                LocalDate.of(2026, 11, 1),
                List.of(
                        new ExchangeRateDTO(LocalDate.of(2026, 10, 27), new BigDecimal("4.2410"), "208/A/NBP/2026"),
                        new ExchangeRateDTO(LocalDate.of(2026, 10, 28), new BigDecimal("4.2455"), "209/A/NBP/2026"),
                        new ExchangeRateDTO(LocalDate.of(2026, 10, 29), new BigDecimal("4.2490"), "210/A/NBP/2026"),
                        selected),
                selected);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRateWindow_shouldReturn200WithContractStructure() throws Exception {
        when(exchangeRateService.getRateWindow(ExpenseCurrency.EUR, LocalDate.of(2026, 11, 2))).thenReturn(eurWindow());

        mockMvc.perform(get("/api/exchange-rates").param("currency", "EUR").param("date", "2026-11-02"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.date").value("2026-11-02"))
                .andExpect(jsonPath("$.windowFrom").value("2026-10-26"))
                .andExpect(jsonPath("$.windowTo").value("2026-11-01"))
                .andExpect(jsonPath("$.rates.length()").value(4))
                .andExpect(jsonPath("$.rates[0].rateDate").value("2026-10-27"))
                .andExpect(jsonPath("$.rates[0].mid").value(4.2410))
                .andExpect(jsonPath("$.rates[0].tableNo").value("208/A/NBP/2026"))
                .andExpect(jsonPath("$.rates[3].rateDate").value("2026-10-30"))
                .andExpect(jsonPath("$.selected.rateDate").value("2026-10-30"))
                .andExpect(jsonPath("$.selected.mid").value(4.2512))
                .andExpect(jsonPath("$.selected.tableNo").value("211/A/NBP/2026"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRateWindow_shouldReturn400_whenPln() throws Exception {
        when(exchangeRateService.getRateWindow(ExpenseCurrency.PLN, LocalDate.of(2026, 11, 2)))
                .thenThrow(new ValidationException("Kurs NBP jest dostępny tylko dla walut obcych."));

        mockMvc.perform(get("/api/exchange-rates").param("currency", "PLN").param("date", "2026-11-02"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Kurs NBP jest dostępny tylko dla walut obcych."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRateWindow_shouldReturn400_whenCurrencyMissing() throws Exception {
        when(exchangeRateService.getRateWindow(isNull(), any()))
                .thenThrow(new ValidationException("Waluta jest wymagana."));

        mockMvc.perform(get("/api/exchange-rates").param("date", "2026-11-02"))
                .andExpect(status().isBadRequest());

        verify(exchangeRateService).getRateWindow(null, LocalDate.of(2026, 11, 2));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRateWindow_shouldReturn400_whenCurrencyUnsupported() throws Exception {
        mockMvc.perform(get("/api/exchange-rates").param("currency", "GBP").param("date", "2026-11-02"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(exchangeRateService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRateWindow_shouldReturn400_whenDateInvalid() throws Exception {
        mockMvc.perform(get("/api/exchange-rates").param("currency", "EUR").param("date", "02.11.2026"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(exchangeRateService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRateWindow_shouldReturn400WithMessage_whenNoRateInWindow() throws Exception {
        when(exchangeRateService.getRateWindow(ExpenseCurrency.EUR, LocalDate.of(2026, 11, 2)))
                .thenThrow(new ValidationException("Brak kursu NBP EUR w 7 dniach przed 02.11.2026."));

        mockMvc.perform(get("/api/exchange-rates").param("currency", "EUR").param("date", "2026-11-02"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Brak kursu NBP EUR w 7 dniach przed 02.11.2026."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRateWindow_shouldReturn400WithMessage_whenDateIsTomorrow() throws Exception {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        when(exchangeRateService.getRateWindow(ExpenseCurrency.EUR, tomorrow))
                .thenThrow(new ValidationException("Kurs NBP nie jest dostępny dla daty z przyszłości."));

        mockMvc.perform(get("/api/exchange-rates").param("currency", "EUR").param("date", tomorrow.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Kurs NBP nie jest dostępny dla daty z przyszłości."));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getRateWindow_shouldReturn503WithMessage_whenNbpUnavailable() throws Exception {
        when(exchangeRateService.getRateWindow(ExpenseCurrency.USD, LocalDate.of(2026, 11, 2)))
                .thenThrow(new ExternalServiceUnavailableException("Nie udało się pobrać kursu z NBP. Spróbuj ponownie później."));

        mockMvc.perform(get("/api/exchange-rates").param("currency", "USD").param("date", "2026-11-02"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("Nie udało się pobrać kursu z NBP. Spróbuj ponownie później."));
    }

    @Test
    @WithMockUser(roles = "USER")
    void getRateWindow_shouldReturn403_whenUserRole() throws Exception {
        mockMvc.perform(get("/api/exchange-rates").param("currency", "EUR").param("date", "2026-11-02"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(exchangeRateService);
    }
}

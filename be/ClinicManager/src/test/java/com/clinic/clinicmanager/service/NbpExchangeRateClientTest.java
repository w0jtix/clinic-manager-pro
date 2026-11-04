package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.exceptions.ExternalServiceUnavailableException;
import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import com.clinic.clinicmanager.service.impl.NbpExchangeRateClient;
import com.clinic.clinicmanager.service.impl.NbpExchangeRateClient.NbpRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NbpExchangeRateClientTest {

    private static final String BASE_URL = "https://api.nbp.test/api";
    private static final String EUR_URL =
            BASE_URL + "/exchangerates/rates/a/eur/2026-10-26/2026-11-01/?format=json";

    private MockRestServiceServer server;
    private NbpExchangeRateClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new NbpExchangeRateClient(builder.build());
    }

    @Test
    void fetchRates_shouldParseRatesFromNbpResponse() {
        server.expect(requestTo(EUR_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"table":"A","currency":"euro","code":"EUR","rates":[
                          {"no":"208/A/NBP/2026","effectiveDate":"2026-10-27","mid":4.2410},
                          {"no":"211/A/NBP/2026","effectiveDate":"2026-10-30","mid":4.2512}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<NbpRate> rates = client.fetchRates(ExpenseCurrency.EUR,
                LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1));

        server.verify();
        assertEquals(2, rates.size());
        assertEquals("208/A/NBP/2026", rates.get(0).no());
        assertEquals(LocalDate.of(2026, 10, 27), rates.get(0).effectiveDate());
        assertEquals(0, new BigDecimal("4.2410").compareTo(rates.get(0).mid()));
        assertEquals(LocalDate.of(2026, 10, 30), rates.get(1).effectiveDate());
        assertEquals(0, new BigDecimal("4.2512").compareTo(rates.get(1).mid()));
    }

    @Test
    void fetchRates_shouldUseLowercaseUsdCode() {
        server.expect(requestTo(BASE_URL + "/exchangerates/rates/a/usd/2026-12-21/2026-12-27/?format=json"))
                .andRespond(withSuccess("{\"table\":\"A\",\"code\":\"USD\",\"rates\":[]}", MediaType.APPLICATION_JSON));

        List<NbpRate> rates = client.fetchRates(ExpenseCurrency.USD,
                LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 27));

        server.verify();
        assertTrue(rates.isEmpty());
    }

    @Test
    void fetchRates_shouldReturnEmptyList_whenNbpReturns404() {
        server.expect(requestTo(EUR_URL))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).body("404 NotFound - Not Found - Brak danych"));

        List<NbpRate> rates = client.fetchRates(ExpenseCurrency.EUR,
                LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1));

        assertTrue(rates.isEmpty());
    }

    @Test
    void fetchRates_shouldThrowServiceUnavailable_whenNbpReturns500() {
        server.expect(requestTo(EUR_URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        ExternalServiceUnavailableException ex = assertThrows(ExternalServiceUnavailableException.class,
                () -> client.fetchRates(ExpenseCurrency.EUR, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)));

        assertEquals("Nie udało się pobrać kursu z NBP. Spróbuj ponownie później.", ex.getMessage());
    }

    @Test
    void fetchRates_shouldThrowServiceUnavailable_whenNbpTimesOut() {
        server.expect(requestTo(EUR_URL)).andRespond(withException(new SocketTimeoutException("Read timed out")));

        ExternalServiceUnavailableException ex = assertThrows(ExternalServiceUnavailableException.class,
                () -> client.fetchRates(ExpenseCurrency.EUR, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)));

        assertEquals("Nie udało się pobrać kursu z NBP. Spróbuj ponownie później.", ex.getMessage());
    }

    @Test
    void fetchRates_shouldThrowServiceUnavailable_whenNbpReturns429() {
        server.expect(requestTo(EUR_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThrows(ExternalServiceUnavailableException.class,
                () -> client.fetchRates(ExpenseCurrency.EUR, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)));
    }
}

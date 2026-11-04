package com.clinic.clinicmanager.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.clinic.clinicmanager.exceptions.ExternalServiceUnavailableException;
import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class NbpExchangeRateClient {

    public static final String UNAVAILABLE_MESSAGE = "Nie udało się pobrać kursu z NBP. Spróbuj ponownie później.";

    private final RestClient nbpRestClient;

    public List<NbpRate> fetchRates(ExpenseCurrency currency, LocalDate from, LocalDate to) {
        try {
            NbpRatesResponse response = nbpRestClient.get()
                    .uri("/exchangerates/rates/a/{code}/{from}/{to}/?format=json",
                            currency.name().toLowerCase(Locale.ROOT), from, to)
                    .retrieve()
                    .body(NbpRatesResponse.class);
            return response == null || response.rates() == null ? List.of() : response.rates();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)
                    || e.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
                return List.of();
            }
            log.warn("NBP rate request failed for {}: HTTP {}", currency, e.getStatusCode().value());
            throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE, e);
        } catch (RestClientException e) {
            log.warn("NBP rate request failed for {}: {}", currency, e.getClass().getSimpleName());
            throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE, e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NbpRatesResponse(String table, String code, List<NbpRate> rates) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NbpRate(String no, LocalDate effectiveDate, BigDecimal mid) {
    }
}

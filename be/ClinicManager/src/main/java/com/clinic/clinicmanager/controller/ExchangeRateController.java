package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.ExchangeRateWindowDTO;
import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import com.clinic.clinicmanager.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exchange-rates")
public class ExchangeRateController {
    private final ExchangeRateService exchangeRateService;

    @GetMapping
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<ExchangeRateWindowDTO> getRateWindow(
            @RequestParam(required = false) ExpenseCurrency currency,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ExchangeRateWindowDTO window = exchangeRateService.getRateWindow(currency, date);
        return new ResponseEntity<>(window, HttpStatus.OK);
    }
}

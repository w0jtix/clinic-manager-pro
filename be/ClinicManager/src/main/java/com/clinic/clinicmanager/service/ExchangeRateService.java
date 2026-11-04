package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ExchangeRateDTO;
import com.clinic.clinicmanager.DTO.ExchangeRateWindowDTO;
import com.clinic.clinicmanager.model.constants.ExpenseCurrency;

import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateService {

    Optional<ExchangeRateDTO> resolveRate(ExpenseCurrency currency, LocalDate date);

    ExchangeRateWindowDTO getRateWindow(ExpenseCurrency currency, LocalDate date);
}

package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ExchangeRateWindowDTO {
    private ExpenseCurrency currency;
    private LocalDate date;
    private LocalDate windowFrom;
    private LocalDate windowTo;
    private List<ExchangeRateDTO> rates;
    private ExchangeRateDTO selected;
}

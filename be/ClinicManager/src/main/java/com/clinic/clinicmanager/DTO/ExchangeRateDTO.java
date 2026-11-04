package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.ExchangeRate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ExchangeRateDTO {
    private LocalDate rateDate;
    private BigDecimal mid;
    private String tableNo;

    public ExchangeRateDTO(ExchangeRate exchangeRate) {
        this.rateDate = exchangeRate.getRateDate();
        this.mid = exchangeRate.getMid();
        this.tableNo = exchangeRate.getTableNo();
    }
}

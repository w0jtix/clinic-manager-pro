package com.clinic.clinicmanager.model;

import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "exchange_rate")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private ExpenseCurrency currency;

    @Column(name = "rate_date", nullable = false)
    private LocalDate rateDate;

    @Column(nullable = false)
    private BigDecimal mid;

    @Column(name = "table_no", nullable = false, length = 32)
    private String tableNo;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;
}

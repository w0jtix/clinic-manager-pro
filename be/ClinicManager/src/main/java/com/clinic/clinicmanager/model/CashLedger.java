package com.clinic.clinicmanager.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cash_ledger", uniqueConstraints = {
        @UniqueConstraint(columnNames = "date")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CashLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate date;

    @Column(nullable = false)
    private BigDecimal openingAmount;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal deposit = BigDecimal.ZERO;

    @Column(nullable = true)
    private BigDecimal closingAmount;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal cashOutAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Employee createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_id")
    private Employee closedBy;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isClosed = false;
}

package com.clinic.clinicmanager.model;

import com.clinic.clinicmanager.model.constants.VatRate;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "named_invoice_item")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NamedInvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private NamedInvoice invoice;

    @Column(nullable = false)
    private String name;

    @Column(precision = 15, scale = 4)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(precision = 15, scale = 4)
    private BigDecimal unitNetPrice;

    @Column(precision = 15, scale = 2)
    private BigDecimal netValue;

    @Column(precision = 15, scale = 4)
    private BigDecimal unitGrossPrice;

    @Column(precision = 15, scale = 2)
    private BigDecimal grossValue;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private VatRate vatRate;

    private String sourceType;
}

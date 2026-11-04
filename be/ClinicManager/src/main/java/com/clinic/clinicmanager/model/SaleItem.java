package com.clinic.clinicmanager.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "sale_item")
public class SaleItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id")
    private Voucher voucher;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal netValue = BigDecimal.ZERO;
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal vatValue = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal price;
}

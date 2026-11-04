package com.clinic.clinicmanager.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "sale")
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "sale_id")
    private List<SaleItem> items = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private BigDecimal totalNet = BigDecimal.ZERO;
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal totalVat = BigDecimal.ZERO;
    @Column(nullable = false)
    @Builder.Default
    private BigDecimal totalValue = BigDecimal.ZERO;
}

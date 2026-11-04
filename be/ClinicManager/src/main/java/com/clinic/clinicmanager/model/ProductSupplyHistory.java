package com.clinic.clinicmanager.model;

import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "product_supply_history")
public class ProductSupplyHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer delta;

    @Column(name = "supply_after", nullable = false)
    private Integer supplyAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupplyChangeSource source;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "source_label")
    private String sourceLabel;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "performed_by", nullable = false)
    private String performedBy;
}

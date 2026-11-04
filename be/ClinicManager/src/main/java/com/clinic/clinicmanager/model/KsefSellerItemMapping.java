package com.clinic.clinicmanager.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "ksef_seller_item_mapping",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_ksef_seller_item_mapping_seller_name",
                columnNames = {"seller_id", "ksef_item_name"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KsefSellerItemMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private KsefSeller seller;

    @Column(name = "ksef_item_name", nullable = false, length = 500)
    private String ksefItemName;

    @Column(name = "ksef_gtin", length = 50)
    private String ksefGtin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
}

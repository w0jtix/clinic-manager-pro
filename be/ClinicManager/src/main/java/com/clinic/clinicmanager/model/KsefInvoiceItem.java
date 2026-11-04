package com.clinic.clinicmanager.model;

import com.clinic.clinicmanager.model.constants.VatRate;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "ksef_invoice_item")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KsefInvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private KsefInvoice invoice;

    @Column(name = "ksef_item_name", nullable = false, length = 500)
    private String ksefItemName;

    @Column(name = "ksef_gtin", length = 50)
    private String ksefGtin;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "line_uuid", length = 100)
    private String lineUuid;

    @Column(length = 20)
    private String unit;

    @Column(precision = 15, scale = 4)
    private BigDecimal quantity;

    @Column(name = "declared_quantity")
    private Integer declaredQuantity;

    @Column(name = "unit_net_price", precision = 15, scale = 4)
    private BigDecimal unitNetPrice;

    @Column(name = "net_value", precision = 15, scale = 2)
    private BigDecimal netValue;

    @Column(name = "unit_gross_price", precision = 15, scale = 4)
    private BigDecimal unitGrossPrice;

    @Column(name = "gross_value", precision = 15, scale = 2)
    private BigDecimal grossValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "vat_rate", length = 10)
    private VatRate vatRate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;
}

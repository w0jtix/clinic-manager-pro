package com.clinic.clinicmanager.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ksef_invoice")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KsefInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String ksefReferenceNumber;

    private String invoiceNumber;
    private String invoiceType;
    private LocalDate issueDate;
    private LocalDate paymentDueDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private KsefSeller seller;

    private String buyerNip;
    private String buyerName;
    private String buyerAddress;

    @Column(precision = 15, scale = 2)
    private BigDecimal netAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal vatAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal grossAmount;

    @Builder.Default
    private String currency = "PLN";

    @Builder.Default
    @Column(nullable = false)
    private Boolean isPaid = false;

    @Builder.Default
    @Column(name = "awaiting_product_verification", nullable = false)
    private boolean awaitingProductVerification = false;

    @Column(length = 40)
    private String category;

    @Column(name = "corrected_invoice_id")
    private Long correctedInvoiceId;

    @Column(name = "corrected_ksef_number", length = 100)
    private String correctedKsefNumber;

    private LocalDate paymentDate;
    private String paymentMethod;

    @Column(columnDefinition = "TEXT")
    private String rawXml;

    private LocalDateTime fetchedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    private List<KsefInvoiceItem> items = new ArrayList<>();

    public void addItem(KsefInvoiceItem item) {
        items.add(item);
        item.setInvoice(this);
    }
}

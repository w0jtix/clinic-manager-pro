package com.clinic.clinicmanager.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ksef_issued_invoice")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KsefIssuedInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String ksefReferenceNumber;

    private String invoiceNumber;
    private String invoiceType;
    private LocalDate issueDate;
    private LocalDate paymentDueDate;

    private String sellerName;
    private String sellerNip;
    private String sellerAddress;

    private String buyerName;
    private String buyerNip;
    private String buyerAddress;

    @Column(precision = 15, scale = 2)
    private BigDecimal netAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal vatAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal grossAmount;

    @Builder.Default
    private String currency = "PLN";

    @Column(name = "corrected_ksef_number", length = 100)
    private String correctedKsefNumber;

    @Column(columnDefinition = "TEXT")
    private String rawXml;

    private LocalDateTime fetchedAt;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    @Builder.Default
    private List<KsefIssuedInvoiceItem> items = new ArrayList<>();

    public void addItem(KsefIssuedInvoiceItem item) {
        items.add(item);
        item.setInvoice(this);
    }
}

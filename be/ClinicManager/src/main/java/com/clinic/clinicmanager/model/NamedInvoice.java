package com.clinic.clinicmanager.model;

import com.clinic.clinicmanager.model.constants.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "named_invoice")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NamedInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String invoiceNumber;

    @Column(nullable = false)
    private Integer sequenceInMonth;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "month", nullable = false)
    private Integer month;

    @Column(nullable = false)
    private LocalDate issueDate;

    private LocalDate saleDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "named_invoice_payment_method",
                     joinColumns = @JoinColumn(name = "named_invoice_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    @Builder.Default
    private Set<PaymentMethod> paymentMethods = new LinkedHashSet<>();

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(nullable = false)
    private Long sourceVisitId;

    @Column(nullable = false)
    private String buyerName;

    private String buyerAddress;
    private String buyerPostalCode;
    private String buyerCity;

    @Column(precision = 15, scale = 2)
    private BigDecimal netAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal vatAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal grossAmount;

    private LocalDateTime emailSentAt;

    private String sentTo;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Long createdByUserId;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<NamedInvoiceItem> items = new ArrayList<>();

    public void addItem(NamedInvoiceItem item) {
        items.add(item);
        item.setInvoice(this);
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

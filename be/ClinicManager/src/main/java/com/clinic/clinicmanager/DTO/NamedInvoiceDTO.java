package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.NamedInvoice;
import com.clinic.clinicmanager.model.NamedInvoiceItem;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import com.clinic.clinicmanager.model.constants.PaymentMethod;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class NamedInvoiceDTO {

    private Long id;
    private String invoiceNumber;
    private Integer sequenceInMonth;
    private Integer year;
    private Integer month;
    private LocalDate issueDate;
    private LocalDate saleDate;
    private Set<PaymentMethod> paymentMethods;
    private String notes;
    private Long clientId;
    private Long sourceVisitId;
    private String buyerName;
    private String buyerAddress;
    private String buyerPostalCode;
    private String buyerCity;
    private String sentTo;
    private String clientEmail;
    private BigDecimal netAmount;
    private BigDecimal vatAmount;
    private BigDecimal grossAmount;
    private LocalDateTime emailSentAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long createdByUserId;
    private List<NamedInvoiceItemDTO> items;

    public NamedInvoiceDTO(NamedInvoice invoice) {
        this(invoice, false);
    }

    public NamedInvoiceDTO(NamedInvoice invoice, boolean includeItems) {
        this.id = invoice.getId();
        this.invoiceNumber = invoice.getInvoiceNumber();
        this.sequenceInMonth = invoice.getSequenceInMonth();
        this.year = invoice.getYear();
        this.month = invoice.getMonth();
        this.issueDate = invoice.getIssueDate();
        this.saleDate = invoice.getSaleDate();
        this.paymentMethods = invoice.getPaymentMethods() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(invoice.getPaymentMethods());
        this.notes = invoice.getNotes();
        this.clientId = invoice.getClient() != null ? invoice.getClient().getId() : null;
        this.sourceVisitId = invoice.getSourceVisitId();
        this.buyerName = invoice.getBuyerName();
        this.buyerAddress = invoice.getBuyerAddress();
        this.buyerPostalCode = invoice.getBuyerPostalCode();
        this.buyerCity = invoice.getBuyerCity();
        this.sentTo = invoice.getSentTo();
        this.clientEmail = invoice.getClient() != null ? invoice.getClient().getEmail() : null;
        this.netAmount = invoice.getNetAmount();
        this.vatAmount = invoice.getVatAmount();
        this.grossAmount = invoice.getGrossAmount();
        this.emailSentAt = invoice.getEmailSentAt();
        this.createdAt = invoice.getCreatedAt();
        this.updatedAt = invoice.getUpdatedAt();
        this.createdByUserId = invoice.getCreatedByUserId();
        if (includeItems) {
            List<NamedInvoiceItem> entityItems = invoice.getItems();
            this.items = entityItems == null
                    ? List.of()
                    : entityItems.stream().map(NamedInvoiceItemDTO::new).toList();
        }
    }
}

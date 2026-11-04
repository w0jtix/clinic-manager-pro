package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.KsefInvoice;
import com.clinic.clinicmanager.model.KsefInvoiceItem;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class KsefInvoiceDTO {

    private Long id;
    private String ksefReferenceNumber;
    private String invoiceNumber;
    private String invoiceType;
    private LocalDate issueDate;
    private LocalDate paymentDueDate;
    private KsefSellerDTO seller;
    private String buyerNip;
    private String buyerName;
    private String buyerAddress;
    private BigDecimal netAmount;
    private BigDecimal vatAmount;
    private BigDecimal grossAmount;
    private String currency;
    private Boolean isPaid;
    private boolean awaitingProductVerification;
    private String category;
    private Long correctedInvoiceId;
    private String correctedKsefNumber;
    private LocalDate paymentDate;
    private String paymentMethod;
    private LocalDateTime fetchedAt;
    private List<KsefInvoiceItemDTO> items;
    private List<KsefCorrectionRefDTO> corrections;

    public KsefInvoiceDTO(KsefInvoice invoice) {
        this(invoice, false);
    }

    public KsefInvoiceDTO(KsefInvoice invoice, boolean includeItems) {
        this.id = invoice.getId();
        this.ksefReferenceNumber = invoice.getKsefReferenceNumber();
        this.invoiceNumber = invoice.getInvoiceNumber();
        this.invoiceType = invoice.getInvoiceType();
        this.issueDate = invoice.getIssueDate();
        this.paymentDueDate = invoice.getPaymentDueDate();
        this.seller = invoice.getSeller() != null ? new KsefSellerDTO(invoice.getSeller()) : null;
        this.buyerNip = invoice.getBuyerNip();
        this.buyerName = invoice.getBuyerName();
        this.buyerAddress = invoice.getBuyerAddress();
        this.netAmount = invoice.getNetAmount();
        this.vatAmount = invoice.getVatAmount();
        this.grossAmount = invoice.getGrossAmount();
        this.currency = invoice.getCurrency();
        this.isPaid = invoice.getIsPaid();
        this.awaitingProductVerification = invoice.isAwaitingProductVerification();
        this.category = invoice.getCategory();
        this.correctedInvoiceId = invoice.getCorrectedInvoiceId();
        this.correctedKsefNumber = invoice.getCorrectedKsefNumber();
        this.paymentDate = invoice.getPaymentDate();
        this.paymentMethod = invoice.getPaymentMethod();
        this.fetchedAt = invoice.getFetchedAt();
        if (includeItems) {
            List<KsefInvoiceItem> entityItems = invoice.getItems();
            this.items = entityItems == null
                    ? List.of()
                    : entityItems.stream().map(KsefInvoiceItemDTO::new).toList();
        }
    }
}

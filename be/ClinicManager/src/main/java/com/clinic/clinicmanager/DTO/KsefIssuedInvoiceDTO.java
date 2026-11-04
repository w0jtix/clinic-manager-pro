package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.KsefIssuedInvoice;
import com.clinic.clinicmanager.model.KsefIssuedInvoiceItem;
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
public class KsefIssuedInvoiceDTO {

    private Long id;
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
    private BigDecimal netAmount;
    private BigDecimal vatAmount;
    private BigDecimal grossAmount;
    private String currency;
    private String correctedKsefNumber;
    private LocalDateTime fetchedAt;
    private List<KsefIssuedInvoiceItemDTO> items;

    public KsefIssuedInvoiceDTO(KsefIssuedInvoice invoice) {
        this(invoice, false);
    }

    public KsefIssuedInvoiceDTO(KsefIssuedInvoice invoice, boolean includeItems) {
        this.id = invoice.getId();
        this.ksefReferenceNumber = invoice.getKsefReferenceNumber();
        this.invoiceNumber = invoice.getInvoiceNumber();
        this.invoiceType = invoice.getInvoiceType();
        this.issueDate = invoice.getIssueDate();
        this.paymentDueDate = invoice.getPaymentDueDate();
        this.sellerName = invoice.getSellerName();
        this.sellerNip = invoice.getSellerNip();
        this.sellerAddress = invoice.getSellerAddress();
        this.buyerName = invoice.getBuyerName();
        this.buyerNip = invoice.getBuyerNip();
        this.buyerAddress = invoice.getBuyerAddress();
        this.netAmount = invoice.getNetAmount();
        this.vatAmount = invoice.getVatAmount();
        this.grossAmount = invoice.getGrossAmount();
        this.currency = invoice.getCurrency();
        this.correctedKsefNumber = invoice.getCorrectedKsefNumber();
        this.fetchedAt = invoice.getFetchedAt();
        if (includeItems) {
            List<KsefIssuedInvoiceItem> entityItems = invoice.getItems();
            this.items = entityItems == null
                    ? List.of()
                    : entityItems.stream().map(KsefIssuedInvoiceItemDTO::new).toList();
        }
    }
}

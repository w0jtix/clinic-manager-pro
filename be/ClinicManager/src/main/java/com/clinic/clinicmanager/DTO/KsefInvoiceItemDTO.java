package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.KsefInvoiceItem;
import com.clinic.clinicmanager.model.constants.VatRate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KsefInvoiceItemDTO {

    private Long id;
    private String ksefItemName;
    private String ksefGtin;
    private Integer lineNumber;
    private String lineUuid;
    private String unit;
    private BigDecimal quantity;
    private Integer declaredQuantity;
    private BigDecimal unitNetPrice;
    private BigDecimal netValue;
    private BigDecimal unitGrossPrice;
    private BigDecimal grossValue;
    private VatRate vatRate;
    private ProductDTO product;
    private Long suggestedProductId;
    private KsefCorrectionMatchDTO correctionMatch;

    public KsefInvoiceItemDTO(KsefInvoiceItem item) {
        this.id = item.getId();
        this.ksefItemName = item.getKsefItemName();
        this.ksefGtin = item.getKsefGtin();
        this.lineNumber = item.getLineNumber();
        this.lineUuid = item.getLineUuid();
        this.unit = item.getUnit();
        this.quantity = item.getQuantity();
        this.declaredQuantity = item.getDeclaredQuantity();
        this.unitNetPrice = item.getUnitNetPrice();
        this.netValue = item.getNetValue();
        this.unitGrossPrice = item.getUnitGrossPrice();
        this.grossValue = item.getGrossValue();
        this.vatRate = item.getVatRate();
        this.product = item.getProduct() != null ? new ProductDTO(item.getProduct()) : null;
    }
}

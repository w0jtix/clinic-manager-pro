package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.KsefIssuedInvoiceItem;
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
public class KsefIssuedInvoiceItemDTO {

    private Long id;
    private String ksefItemName;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal unitNetPrice;
    private BigDecimal netValue;
    private BigDecimal unitGrossPrice;
    private BigDecimal grossValue;
    private VatRate vatRate;

    public KsefIssuedInvoiceItemDTO(KsefIssuedInvoiceItem item) {
        this.id = item.getId();
        this.ksefItemName = item.getKsefItemName();
        this.unit = item.getUnit();
        this.quantity = item.getQuantity();
        this.unitNetPrice = item.getUnitNetPrice();
        this.netValue = item.getNetValue();
        this.unitGrossPrice = item.getUnitGrossPrice();
        this.grossValue = item.getGrossValue();
        this.vatRate = item.getVatRate();
    }
}

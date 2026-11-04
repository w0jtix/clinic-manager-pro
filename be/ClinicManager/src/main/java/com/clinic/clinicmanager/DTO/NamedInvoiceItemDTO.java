package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.NamedInvoiceItem;
import com.clinic.clinicmanager.model.constants.VatRate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class NamedInvoiceItemDTO {

    private Long id;
    private String name;
    private BigDecimal quantity;
    private BigDecimal unitNetPrice;
    private BigDecimal netValue;
    private BigDecimal unitGrossPrice;
    private BigDecimal grossValue;
    private VatRate vatRate;
    private String sourceType;

    public NamedInvoiceItemDTO(NamedInvoiceItem item) {
        this.id = item.getId();
        this.name = item.getName();
        this.quantity = item.getQuantity();
        this.unitNetPrice = item.getUnitNetPrice();
        this.netValue = item.getNetValue();
        this.unitGrossPrice = item.getUnitGrossPrice();
        this.grossValue = item.getGrossValue();
        this.vatRate = item.getVatRate();
        this.sourceType = item.getSourceType();
    }
}

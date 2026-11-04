package com.clinic.clinicmanager.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class StockChangeProductDTO {

    private Long productId;
    private String productName;
    private int quantity;
    private int currentSupply;
    private int supplyAfter;
    private boolean sufficient;
    private boolean skipped;
}

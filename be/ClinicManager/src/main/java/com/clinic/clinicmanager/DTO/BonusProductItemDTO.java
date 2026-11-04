package com.clinic.clinicmanager.DTO;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BonusProductItemDTO {
    private Long visitId;
    private Double purchaseNetPrice;
    private Double saleNetPrice;
    private Double margin;
    private Double bonusPerUnit;
}

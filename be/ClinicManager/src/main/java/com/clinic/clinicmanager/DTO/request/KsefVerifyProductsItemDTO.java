package com.clinic.clinicmanager.DTO.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KsefVerifyProductsItemDTO {

    private Long itemId;
    private Long productId;
    private Integer quantity;
    private boolean rememberForSeller;
}

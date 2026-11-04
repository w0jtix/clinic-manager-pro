package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.KsefSellerItemMapping;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KsefSellerItemMappingDTO {

    private Long id;
    private String ksefItemName;
    private ProductDTO product;

    public KsefSellerItemMappingDTO(KsefSellerItemMapping mapping) {
        this.id = mapping.getId();
        this.ksefItemName = mapping.getKsefItemName();
        this.product = mapping.getProduct() != null ? new ProductDTO(mapping.getProduct()) : null;
    }
}

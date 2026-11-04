package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.KsefSeller;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ExpenseSellerDTO {
    private Long id;
    private String nip;
    private String name;
    private String address;

    public ExpenseSellerDTO(KsefSeller seller) {
        this.id = seller.getId();
        this.nip = seller.getNip();
        this.name = seller.getName();
        this.address = seller.getAddress();
    }
}

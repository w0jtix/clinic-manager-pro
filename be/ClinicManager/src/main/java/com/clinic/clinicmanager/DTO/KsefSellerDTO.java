package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.KsefSeller;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KsefSellerDTO {

    private Long id;
    private String nip;
    private String name;
    private String address;
    private String email;
    private String phone;
    private Boolean autoMarkProducts;
    private String defaultExpenseCategory;

    public KsefSellerDTO(KsefSeller seller) {
        this.id = seller.getId();
        this.nip = seller.getNip();
        this.name = seller.getName();
        this.address = seller.getAddress();
        this.email = seller.getEmail();
        this.phone = seller.getPhone();
        this.autoMarkProducts = seller.getAutoMarkProducts();
        this.defaultExpenseCategory = seller.getDefaultExpenseCategory();
    }
}

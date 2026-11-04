package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.model.KsefSeller;
import com.clinic.clinicmanager.model.KsefSellerItemMapping;
import com.clinic.clinicmanager.model.Product;

import java.util.Optional;

public interface KsefSellerItemMappingService {

    Optional<KsefSellerItemMapping> findFor(Long sellerId, String ksefItemName, String ksefGtin);

    void saveOrUpdate(KsefSeller seller, String ksefItemName, String ksefGtin, Product product);
}

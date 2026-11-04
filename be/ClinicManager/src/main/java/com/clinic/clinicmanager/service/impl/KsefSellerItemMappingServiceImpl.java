package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.model.KsefSeller;
import com.clinic.clinicmanager.model.KsefSellerItemMapping;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.repo.KsefSellerItemMappingRepo;
import com.clinic.clinicmanager.service.KsefSellerItemMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class KsefSellerItemMappingServiceImpl implements KsefSellerItemMappingService {

    private final KsefSellerItemMappingRepo mappingRepo;

    @Override
    @Transactional(readOnly = true)
    public Optional<KsefSellerItemMapping> findFor(Long sellerId, String ksefItemName, String ksefGtin) {
        if (sellerId == null) return Optional.empty();

        if (ksefGtin != null && !ksefGtin.isBlank()) {
            Optional<KsefSellerItemMapping> byGtin = mappingRepo
                    .findBySellerIdAndKsefGtin(sellerId, ksefGtin)
                    .filter(this::isProductActive);
            if (byGtin.isPresent()) return byGtin;
        }
        if (ksefItemName == null || ksefItemName.isBlank()) return Optional.empty();
        return mappingRepo.findBySellerIdAndKsefItemName(sellerId, ksefItemName)
                .filter(this::isProductActive);
    }

    private boolean isProductActive(KsefSellerItemMapping mapping) {
        return mapping.getProduct() != null
                && !Boolean.TRUE.equals(mapping.getProduct().getIsDeleted());
    }

    @Override
    public void saveOrUpdate(KsefSeller seller, String ksefItemName, String ksefGtin, Product product) {
        if (seller == null || ksefItemName == null || ksefItemName.isBlank() || product == null) return;

        KsefSellerItemMapping mapping = findByNaturalKey(seller.getId(), ksefItemName, ksefGtin)
                .orElseGet(() -> KsefSellerItemMapping.builder()
                        .seller(seller)
                        .build());

        mapping.setKsefItemName(ksefItemName);
        mapping.setKsefGtin(ksefGtin);
        mapping.setProduct(product);
        mappingRepo.save(mapping);
    }

    private Optional<KsefSellerItemMapping> findByNaturalKey(Long sellerId, String ksefItemName, String ksefGtin) {
        if (ksefGtin != null && !ksefGtin.isBlank()) {
            Optional<KsefSellerItemMapping> byGtin = mappingRepo.findBySellerIdAndKsefGtin(sellerId, ksefGtin);
            if (byGtin.isPresent()) return byGtin;
        }
        return mappingRepo.findBySellerIdAndKsefItemName(sellerId, ksefItemName);
    }
}

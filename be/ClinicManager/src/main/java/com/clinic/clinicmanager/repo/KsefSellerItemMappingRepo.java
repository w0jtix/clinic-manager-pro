package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.KsefSellerItemMapping;
import com.clinic.clinicmanager.service.ProductReferenceChecker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KsefSellerItemMappingRepo extends JpaRepository<KsefSellerItemMapping, Long>, ProductReferenceChecker {

    @Query("""
            SELECT m FROM KsefSellerItemMapping m
            JOIN FETCH m.product p
            LEFT JOIN FETCH p.category
            LEFT JOIN FETCH p.brand
            WHERE m.seller.id = :sellerId
            ORDER BY m.ksefItemName ASC
            """)
    List<KsefSellerItemMapping> findBySellerIdWithProduct(@Param("sellerId") Long sellerId);

    Optional<KsefSellerItemMapping> findBySellerIdAndKsefItemName(Long sellerId, String ksefItemName);

    Optional<KsefSellerItemMapping> findBySellerIdAndKsefGtin(Long sellerId, String ksefGtin);

    boolean existsByProductId(Long productId);
}

package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.Sale;
import com.clinic.clinicmanager.model.SaleItem;
import com.clinic.clinicmanager.service.ProductReferenceChecker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SaleItemRepo extends JpaRepository<SaleItem, Long>, ProductReferenceChecker {
    Optional<Sale> findOneById(Long id);

    Boolean existsByVoucherId(Long voucherId);

    boolean existsByProductId(Long productId);
}

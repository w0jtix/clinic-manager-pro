package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.InventoryReportItem;
import com.clinic.clinicmanager.service.ProductReferenceChecker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryReportItemRepo extends JpaRepository<InventoryReportItem, Long>, ProductReferenceChecker {

    boolean existsByProductId(Long productId);
}

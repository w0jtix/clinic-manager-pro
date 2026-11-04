package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.CompanyExpenseItem;
import com.clinic.clinicmanager.service.ProductReferenceChecker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyExpenseItemRepo extends JpaRepository<CompanyExpenseItem, Long>, ProductReferenceChecker {

    boolean existsByProductId(Long productId);
}

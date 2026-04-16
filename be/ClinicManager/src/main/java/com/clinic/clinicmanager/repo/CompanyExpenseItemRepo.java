package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.CompanyExpenseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyExpenseItemRepo extends JpaRepository<CompanyExpenseItem, Long> {
}

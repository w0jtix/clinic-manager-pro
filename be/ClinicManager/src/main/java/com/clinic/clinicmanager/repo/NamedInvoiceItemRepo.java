package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.NamedInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NamedInvoiceItemRepo extends JpaRepository<NamedInvoiceItem, Long> {
}

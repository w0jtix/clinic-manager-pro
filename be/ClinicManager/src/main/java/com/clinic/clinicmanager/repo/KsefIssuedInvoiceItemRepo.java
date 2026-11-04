package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.KsefIssuedInvoiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KsefIssuedInvoiceItemRepo extends JpaRepository<KsefIssuedInvoiceItem, Long> {
}

package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SaleRepo extends JpaRepository<Sale, Long> {
    Optional<Sale> findOneById(Long id);

}

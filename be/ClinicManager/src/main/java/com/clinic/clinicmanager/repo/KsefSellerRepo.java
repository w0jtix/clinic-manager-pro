package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.KsefSeller;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KsefSellerRepo extends JpaRepository<KsefSeller, Long> {

    Optional<KsefSeller> findByNip(String nip);
}

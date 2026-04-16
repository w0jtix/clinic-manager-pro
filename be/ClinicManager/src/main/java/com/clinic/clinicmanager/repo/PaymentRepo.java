package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.DTO.PaymentDTO;
import com.clinic.clinicmanager.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepo  extends JpaRepository<Payment, Long> {

    Optional<PaymentDTO> findOneById(Long id);
}

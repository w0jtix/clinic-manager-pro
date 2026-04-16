package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.VisitDiscount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VisitDiscountRepo extends JpaRepository<VisitDiscount, Long> {

    Optional<VisitDiscount> findOneById(Long id);

    boolean existsByReviewId(Long reviewId);
}

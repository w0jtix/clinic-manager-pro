package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.DTO.DiscountDTO;
import com.clinic.clinicmanager.model.Discount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DiscountRepo extends JpaRepository<Discount, Long> {

    Optional<Discount> findOneById(Long id);

    @Query("""
    SELECT new com.clinic.clinicmanager.DTO.DiscountDTO(
        d.id,
        d.name,
        d.percentageValue,
        COUNT(c.id)
    )
    FROM Discount d
    LEFT JOIN Client c ON c.discount.id = d.id
    GROUP BY d.id, d.name, d.percentageValue
    """)
    List<DiscountDTO> findAllWithClientCount();
}

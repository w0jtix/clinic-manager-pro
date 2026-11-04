package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.DTO.CategoryUsageDTO;
import com.clinic.clinicmanager.model.BaseServiceCategory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BaseServiceCategoryRepo extends SoftDeletableCategoryRepo<BaseServiceCategory> {

    Optional<BaseServiceCategory> findOneById(Long id);

    @Query("""
            SELECT new com.clinic.clinicmanager.DTO.CategoryUsageDTO(sc.id, COUNT(s.id))
            FROM BaseServiceCategory sc
            LEFT JOIN BaseService s ON s.category = sc AND s.isDeleted = false
            WHERE sc.isDeleted = false
            GROUP BY sc.id
            """)
    List<CategoryUsageDTO> findActiveCategoryUsage();
}

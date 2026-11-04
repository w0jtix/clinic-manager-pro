package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.DTO.CategoryUsageDTO;
import com.clinic.clinicmanager.model.ProductCategory;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductCategoryRepo extends SoftDeletableCategoryRepo<ProductCategory> {

    @Query(value = "SELECT COUNT(*) FROM (SELECT pg_advisory_xact_lock(:key)) AS lock_holder", nativeQuery = true)
    long acquireTransactionLock(@Param("key") long key);

    List<ProductCategory> findAllByIsDeletedFalse();

    @Query("SELECT pc.color FROM ProductCategory pc WHERE pc.isDeleted = false")
    List<String> findActiveColors();

    @Query("""
            SELECT new com.clinic.clinicmanager.DTO.CategoryUsageDTO(pc.id, COUNT(p.id))
            FROM ProductCategory pc
            LEFT JOIN Product p ON p.category = pc AND p.isDeleted = false
            WHERE pc.isDeleted = false
            GROUP BY pc.id
            """)
    List<CategoryUsageDTO> findActiveCategoryUsage();
}

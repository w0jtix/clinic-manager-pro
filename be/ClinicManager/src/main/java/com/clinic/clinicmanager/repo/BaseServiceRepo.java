package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.BaseService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BaseServiceRepo extends JpaRepository<BaseService, Long> {

    Optional<BaseService> findOneById(Long id);

    @Query("SELECT s FROM BaseService s " +
            "WHERE s.category.id = :categoryId " +
            "ORDER BY s.displayOrder ASC, s.name ASC"
    )
    List<BaseService> findAllByCategoryId(@Param("categoryId") Long categoryId);

    Optional<BaseService> findByName(String name);

    @Query("SELECT s FROM BaseService s " +
            "LEFT JOIN FETCH s.category c " +
            "WHERE s.isDeleted = false " +
            "AND (COALESCE(:keyword, '') = '' OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')))" +
            "AND (COALESCE(:categoryIds, NULL) IS NULL OR s.category.id IN :categoryIds) " +
            "ORDER BY s.displayOrder ASC, s.name ASC"
    )
    List<BaseService> findAllWithFilters(
            @Param("keyword") String keyword,
            @Param("categoryIds") List<Long> categoryIds
            );

    Boolean existsByName(String name);

    @Query("""
        SELECT COUNT(v) > 0
        FROM BaseService s
        JOIN s.variants v
        WHERE s.id = :serviceId AND v.id = :variantId
    """)
    Boolean serviceHasVariant(Long serviceId, Long variantId);

    @Query("SELECT COALESCE(MAX(s.displayOrder), -1) FROM BaseService s")
    Integer findMaxDisplayOrder();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE BaseService s SET s.displayOrder = s.displayOrder + 1 " +
            "WHERE s.displayOrder >= :to AND s.displayOrder < :from")
    void shiftUp(@Param("from") int from, @Param("to") int to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE BaseService s SET s.displayOrder = s.displayOrder - 1 " +
            "WHERE s.displayOrder > :from AND s.displayOrder <= :to")
    void shiftDown(@Param("from") int from, @Param("to") int to);

    long countByCategoryIdAndIsDeletedFalse(Long categoryId);

    long countByCategoryId(Long categoryId);
}

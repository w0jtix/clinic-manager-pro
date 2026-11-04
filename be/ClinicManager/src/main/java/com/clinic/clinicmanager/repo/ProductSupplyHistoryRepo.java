package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.ProductSupplyHistory;
import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import com.clinic.clinicmanager.repo.projection.SupplySourceTotalProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface ProductSupplyHistoryRepo extends JpaRepository<ProductSupplyHistory, Long> {

    @Query("""
    SELECT h FROM ProductSupplyHistory h
    WHERE h.product.id = :productId
      AND (:sources IS NULL OR h.source IN :sources)
    ORDER BY h.occurredAt DESC, h.id DESC
""")
    Page<ProductSupplyHistory> findAllByProductId(
            @Param("productId") Long productId,
            @Param("sources") Collection<SupplyChangeSource> sources,
            Pageable pageable
    );

    @Query("""
    SELECT h FROM ProductSupplyHistory h
    WHERE h.product.id = :productId
      AND h.occurredAt >= :from
      AND h.occurredAt < :to
    ORDER BY h.occurredAt ASC, h.id ASC
""")
    List<ProductSupplyHistory> findAllByProductIdInRange(
            @Param("productId") Long productId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    @Query("""
    SELECT h.supplyAfter FROM ProductSupplyHistory h
    WHERE h.product.id = :productId
      AND h.occurredAt < :before
    ORDER BY h.occurredAt DESC, h.id DESC
""")
    List<Integer> findSupplyBefore(
            @Param("productId") Long productId,
            @Param("before") LocalDateTime before,
            Pageable pageable
    );

    @Query("SELECT MIN(h.occurredAt) FROM ProductSupplyHistory h WHERE h.product.id = :productId")
    LocalDateTime findFirstOccurredAt(@Param("productId") Long productId);

    @Query("SELECT MAX(h.occurredAt) FROM ProductSupplyHistory h WHERE h.product.id = :productId")
    LocalDateTime findLastOccurredAt(@Param("productId") Long productId);

    @Query("""
    SELECT h.source AS source, SUM(h.delta) AS total
    FROM ProductSupplyHistory h
    WHERE h.product.id = :productId
    GROUP BY h.source
""")
    List<SupplySourceTotalProjection> sumDeltasBySource(@Param("productId") Long productId);
}

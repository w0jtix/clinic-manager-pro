package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.DTO.ProductDTO;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.repo.projection.ProductPurchaseProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepo extends JpaRepository<Product, Long> {

    /**
     * Stock status predicate shared by both findAllWithFilters variants (and by their count query),
     * so that the OK / LOW_STOCK / OUT_OF_STOCK rule lives in exactly one place.
     */
    String STOCK_STATUS_FILTER = """
      AND (:stockStatus IS NULL
           OR (:stockStatus = 'OUT_OF_STOCK' AND p.supply = 0)
           OR (:stockStatus = 'LOW_STOCK' AND p.supply > 0 AND p.supply <= p.lowStockThreshold)
           OR (:stockStatus = 'OK' AND p.supply > p.lowStockThreshold))
""";

    @EntityGraph(attributePaths = {"category", "brand"})
    Optional<Product> findOneById(Long id);

    @Query("""
    SELECT p FROM Product p
    LEFT JOIN FETCH p.category c
    LEFT JOIN FETCH p.brand b
    WHERE (COALESCE(:productIds, NULL) IS NULL OR p.id IN :productIds)
      AND (COALESCE(:categoryIds, NULL) IS NULL OR p.category.id IN :categoryIds)
      AND (COALESCE(:brandIds, NULL) IS NULL OR p.brand.id IN :brandIds)
      AND (COALESCE(:keyword, '') = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
      AND (:includeZero = TRUE OR p.supply > 0)
      AND (:isDeleted IS NULL OR p.isDeleted = :isDeleted)
""" + STOCK_STATUS_FILTER)
    List<Product> findAllWithFilters(
            @Param("productIds") List<Long> productIds,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("brandIds") List<Long> brandIds,
            @Param("keyword") String keyword,
            @Param("includeZero") Boolean includeZero,
            @Param("isDeleted") Boolean isDeleted,
            @Param("stockStatus") String stockStatus
    );

    @Query(value = """
    SELECT p FROM Product p
    LEFT JOIN FETCH p.category c
    LEFT JOIN FETCH p.brand b
    WHERE (COALESCE(:productIds, NULL) IS NULL OR p.id IN :productIds)
      AND (COALESCE(:categoryIds, NULL) IS NULL OR p.category.id IN :categoryIds)
      AND (COALESCE(:brandIds, NULL) IS NULL OR p.brand.id IN :brandIds)
      AND (COALESCE(:keyword, '') = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
      AND (:includeZero = TRUE OR p.supply > 0)
      AND (:isDeleted IS NULL OR p.isDeleted = :isDeleted)
""" + STOCK_STATUS_FILTER, countQuery = """
    SELECT COUNT(p) FROM Product p
    WHERE (COALESCE(:productIds, NULL) IS NULL OR p.id IN :productIds)
      AND (COALESCE(:categoryIds, NULL) IS NULL OR p.category.id IN :categoryIds)
      AND (COALESCE(:brandIds, NULL) IS NULL OR p.brand.id IN :brandIds)
      AND (COALESCE(:keyword, '') = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
      AND (:includeZero = TRUE OR p.supply > 0)
      AND (:isDeleted IS NULL OR p.isDeleted = :isDeleted)
""" + STOCK_STATUS_FILTER)
    Page<Product> findAllWithFilters(
            @Param("productIds") List<Long> productIds,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("brandIds") List<Long> brandIds,
            @Param("keyword") String keyword,
            @Param("includeZero") Boolean includeZero,
            @Param("isDeleted") Boolean isDeleted,
            @Param("stockStatus") String stockStatus,
            Pageable pageable
    );

    @Query("SELECT p FROM Product p WHERE p.name = :name")
    Optional<Product> findByName(
            String name
    );

    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdIncludingDeleted(@Param("id") Long id);

    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.isDeleted = false")
    Optional<Product> findByIdNotDeleted(@Param("id") Long id);

    @Query("SELECT p.isDeleted FROM Product p WHERE p.id = :productId")
    Boolean isProductSoftDeleted(@Param("productId") Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id IN :ids ORDER BY p.id")
    List<Product> findAllByIdInForUpdate(@Param("ids") Collection<Long> ids);

    long countByCategoryIdAndIsDeletedFalse(Long categoryId);

    long countByCategoryId(Long categoryId);

    /**
     * Recent purchases of a product (KSeF verified items + manual company-expense items),
     * not later than the given cut-off date, newest first. Used by bonus/margin statistics
     * so that recalculating a past period uses purchase prices as they were before the
     * payout date - not current ones.
     */
    @Query(value = """
            SELECT pp.product_id AS productId,
                   pp.purchase_date AS purchaseDate,
                   pp.unit_net_price AS unitNetPrice,
                   pp.unit_gross_price AS unitGrossPrice,
                   pp.quantity AS quantity,
                   pp.vat_rate AS vatRate
            FROM product_purchase pp
            WHERE pp.product_id = :productId
              AND pp.purchase_date <= :beforeDate
            ORDER BY pp.purchase_date DESC, pp.source_id DESC
            """, nativeQuery = true)
    List<ProductPurchaseProjection> findRecentPurchasesBeforeDate(
            @Param("productId") Long productId,
            @Param("beforeDate") LocalDate beforeDate,
            Pageable pageable);

    /**
     * All purchases of a product (KSeF verified items + manual company-expense items).
     * Used by the product supply summary to weigh the average net purchase price by quantity.
     */
    @Query(value = """
            SELECT pp.product_id AS productId,
                   pp.purchase_date AS purchaseDate,
                   pp.unit_net_price AS unitNetPrice,
                   pp.unit_gross_price AS unitGrossPrice,
                   pp.quantity AS quantity,
                   pp.vat_rate AS vatRate
            FROM product_purchase pp
            WHERE pp.product_id = :productId
            """, nativeQuery = true)
    List<ProductPurchaseProjection> findAllPurchases(@Param("productId") Long productId);
}

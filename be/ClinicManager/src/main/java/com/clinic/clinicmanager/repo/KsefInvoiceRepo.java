package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.DTO.KsefInvoiceDTO;
import com.clinic.clinicmanager.model.KsefInvoice;
import com.clinic.clinicmanager.repo.projection.KsefCategoryFacetProjection;
import com.clinic.clinicmanager.repo.projection.KsefMonthlySpendProjection;
import com.clinic.clinicmanager.repo.projection.KsefSellerSummaryProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface KsefInvoiceRepo extends JpaRepository<KsefInvoice, Long> {

    boolean existsByKsefReferenceNumber(String ksefReferenceNumber);

    long countByIssueDateBetween(LocalDate dateFrom, LocalDate dateTo);

    @Query("""
            SELECT DISTINCT i FROM KsefInvoice i
            WHERE i.correctedInvoiceId = :invoiceId
            """)
    List<KsefInvoice> findCorrectionsByInvoiceId(
            @Param("invoiceId") Long invoiceId);


    @Query("""
            SELECT DISTINCT i FROM KsefInvoice i
            LEFT JOIN FETCH i.items it
            LEFT JOIN FETCH it.product
            WHERE i.ksefReferenceNumber = :ksefReferenceNumber
            """)
    Optional<KsefInvoice> findByKsefReferenceNumberWithItems(
            @Param("ksefReferenceNumber") String ksefReferenceNumber);

    @Query("""
            SELECT DISTINCT i FROM KsefInvoice i
            LEFT JOIN FETCH i.items
            WHERE i.invoiceType LIKE 'KOR%' AND i.correctedInvoiceId IS NULL
            """)
    List<KsefInvoice> findPendingCorrectionsWithItems();

    @Query("""
            SELECT DISTINCT i FROM KsefInvoice i
            LEFT JOIN FETCH i.items
            WHERE i.correctedInvoiceId = :parentId
            """)
    List<KsefInvoice> findByCorrectedInvoiceId(@Param("parentId") Long parentId);

    @Query(value = """
            SELECT i FROM KsefInvoice i
            LEFT JOIN FETCH i.seller
            WHERE (CAST(:dateFrom AS LocalDate) IS NULL OR i.issueDate >= :dateFrom)
            AND (CAST(:dateTo AS LocalDate) IS NULL OR i.issueDate <= :dateTo)
            AND (:sellerId IS NULL OR i.seller.id = :sellerId)
            AND (:isPaid IS NULL OR i.isPaid = :isPaid)
            AND (
                  :noCategoryFilter = TRUE
               OR (:hasCategories = TRUE AND i.category IN :categories)
               OR (:wantUncategorized = TRUE AND i.category IS NULL AND i.awaitingProductVerification = FALSE)
               OR (:wantAwaiting = TRUE AND i.awaitingProductVerification = TRUE)
            )
            """,
            countQuery = """
            SELECT COUNT(i) FROM KsefInvoice i
            WHERE (CAST(:dateFrom AS LocalDate) IS NULL OR i.issueDate >= :dateFrom)
            AND (CAST(:dateTo AS LocalDate) IS NULL OR i.issueDate <= :dateTo)
            AND (:sellerId IS NULL OR i.seller.id = :sellerId)
            AND (:isPaid IS NULL OR i.isPaid = :isPaid)
            AND (
                  :noCategoryFilter = TRUE
               OR (:hasCategories = TRUE AND i.category IN :categories)
               OR (:wantUncategorized = TRUE AND i.category IS NULL AND i.awaitingProductVerification = FALSE)
               OR (:wantAwaiting = TRUE AND i.awaitingProductVerification = TRUE)
            )
            """)
    Page<KsefInvoice> findAllWithFilters(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("sellerId") Long sellerId,
            @Param("isPaid") Boolean isPaid,
            @Param("noCategoryFilter") boolean noCategoryFilter,
            @Param("hasCategories") boolean hasCategories,
            @Param("categories") Collection<String> categories,
            @Param("wantUncategorized") boolean wantUncategorized,
            @Param("wantAwaiting") boolean wantAwaiting,
            Pageable pageable);

    @Query("""
            SELECT COUNT(i) AS invoiceCount,
                   SUM(i.grossAmount) AS totalGross,
                   SUM(CASE WHEN i.isPaid = FALSE
                             AND (i.invoiceType IS NULL OR i.invoiceType NOT LIKE 'KOR%')
                            THEN 1 ELSE 0 END) AS unpaidCount,
                   SUM(CASE WHEN i.awaitingProductVerification = TRUE
                            THEN 1 ELSE 0 END) AS awaitingVerificationCount,
                   SUM(CASE WHEN i.category IS NULL AND i.awaitingProductVerification = FALSE
                            THEN 1 ELSE 0 END) AS uncategorizedCount
            FROM KsefInvoice i
            WHERE i.seller.id = :sellerId
            """)
    KsefSellerSummaryProjection summarizeBySellerId(@Param("sellerId") Long sellerId);

    @Query("""
            SELECT i.category AS category, COUNT(i) AS cnt
            FROM KsefInvoice i
            WHERE i.seller.id = :sellerId AND i.category IS NOT NULL
            GROUP BY i.category
            ORDER BY COUNT(i) DESC
            """)
    List<KsefCategoryFacetProjection> countByCategoryForSeller(@Param("sellerId") Long sellerId);

    @Query("""
            SELECT EXTRACT(MONTH FROM i.issueDate) AS month,
                   SUM(i.grossAmount) AS gross,
                   COUNT(i) AS cnt
            FROM KsefInvoice i
            WHERE i.seller.id = :sellerId
              AND i.issueDate >= :dateFrom
              AND i.issueDate <= :dateTo
            GROUP BY EXTRACT(MONTH FROM i.issueDate)
            """)
    List<KsefMonthlySpendProjection> sumMonthlySpendBySellerId(
            @Param("sellerId") Long sellerId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);

    @Query("SELECT MIN(i.issueDate) FROM KsefInvoice i WHERE i.seller.id = :sellerId")
    LocalDate findFirstIssueDateBySellerId(@Param("sellerId") Long sellerId);

    @Query("SELECT MAX(i.issueDate) FROM KsefInvoice i WHERE i.seller.id = :sellerId")
    LocalDate findLastIssueDateBySellerId(@Param("sellerId") Long sellerId);
}

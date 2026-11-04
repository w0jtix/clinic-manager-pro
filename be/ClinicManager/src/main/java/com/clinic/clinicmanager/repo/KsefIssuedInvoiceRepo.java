package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.KsefIssuedInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface KsefIssuedInvoiceRepo extends JpaRepository<KsefIssuedInvoice, Long> {

    Optional<KsefIssuedInvoice> findByKsefReferenceNumber(String ksefReferenceNumber);

    boolean existsByKsefReferenceNumber(String ksefReferenceNumber);

    @Query("""
            SELECT DISTINCT i FROM KsefIssuedInvoice i
            LEFT JOIN FETCH i.items
            WHERE i.ksefReferenceNumber = :ksefReferenceNumber
            """)
    Optional<KsefIssuedInvoice> findByKsefReferenceNumberWithItems(
            @Param("ksefReferenceNumber") String ksefReferenceNumber);

    @Query("""
            SELECT DISTINCT i FROM KsefIssuedInvoice i
            LEFT JOIN FETCH i.items
            WHERE i.id = :id
            """)
    Optional<KsefIssuedInvoice> findByIdWithItems(@Param("id") Long id);

    @Query(value = """
            SELECT i FROM KsefIssuedInvoice i
            WHERE (CAST(:dateFrom AS LocalDate) IS NULL OR i.issueDate >= :dateFrom)
            AND (CAST(:dateTo AS LocalDate) IS NULL OR i.issueDate <= :dateTo)
            AND (COALESCE(:keyword, '') = ''
                 OR LOWER(i.buyerName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY i.issueDate DESC, i.id DESC
            """,
            countQuery = """
            SELECT COUNT(i) FROM KsefIssuedInvoice i
            WHERE (CAST(:dateFrom AS LocalDate) IS NULL OR i.issueDate >= :dateFrom)
            AND (CAST(:dateTo AS LocalDate) IS NULL OR i.issueDate <= :dateTo)
            AND (COALESCE(:keyword, '') = ''
                 OR LOWER(i.buyerName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<KsefIssuedInvoice> findAllWithFilters(
            @Param("keyword") String keyword,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            Pageable pageable);

    @Query("""
            SELECT COUNT(i) FROM KsefIssuedInvoice i
            WHERE (CAST(:dateFrom AS LocalDate) IS NULL OR i.issueDate >= :dateFrom)
            AND (CAST(:dateTo AS LocalDate) IS NULL OR i.issueDate <= :dateTo)
            AND (COALESCE(:keyword, '') = ''
                 OR LOWER(i.buyerName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                 OR LOWER(i.invoiceNumber) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    long countWithFilters(
            @Param("keyword") String keyword,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);

    @Query("""
            SELECT i.invoiceNumber FROM KsefIssuedInvoice i
            WHERE i.issueDate >= :dateFrom AND i.issueDate <= :dateTo
            """)
    List<String> findInvoiceNumbersByIssueDateBetween(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);
}

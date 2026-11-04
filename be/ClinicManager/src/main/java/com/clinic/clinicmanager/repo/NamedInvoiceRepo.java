package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.NamedInvoice;
import com.clinic.clinicmanager.repo.projection.VisitInvoiceIdProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NamedInvoiceRepo extends JpaRepository<NamedInvoice, Long> {

    @Query("""
            SELECT COALESCE(MAX(n.sequenceInMonth), 0) FROM NamedInvoice n
            WHERE n.year = :year AND n.month = :month
            """)
    int findMaxSequenceInMonth(@Param("year") int year, @Param("month") int month);

    boolean existsByYearAndMonthAndSequenceInMonthAndIdNot(
            Integer year, Integer month, Integer sequenceInMonth, Long id);

    @Query("""
            SELECT DISTINCT n FROM NamedInvoice n
            LEFT JOIN FETCH n.items
            WHERE n.id = :id
            """)
    Optional<NamedInvoice> findByIdWithItems(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE NamedInvoice n SET n.sentTo = :sentTo, n.emailSentAt = :sentAt WHERE n.id = :id")
    void markSent(@Param("id") Long id, @Param("sentTo") String sentTo, @Param("sentAt") LocalDateTime sentAt);

    @Query(value = """
            SELECT n FROM NamedInvoice n
            WHERE n.client.id = :clientId
            ORDER BY n.issueDate DESC, n.id DESC
            """,
            countQuery = """
            SELECT COUNT(n) FROM NamedInvoice n
            WHERE n.client.id = :clientId
            """)
    Page<NamedInvoice> findAllByClientId(@Param("clientId") Long clientId, Pageable pageable);

    @Query("""
            SELECT n FROM NamedInvoice n
            WHERE n.year = :year AND n.month = :month
            ORDER BY n.sequenceInMonth ASC
            """)
    List<NamedInvoice> findAllByYearAndMonth(@Param("year") int year, @Param("month") int month);

    Optional<NamedInvoice> findFirstBySourceVisitIdOrderByIdDesc(Long sourceVisitId);

    boolean existsBySourceVisitId(Long sourceVisitId);

    @Query("""
            SELECT n.sourceVisitId AS visitId, n.id AS invoiceId FROM NamedInvoice n
            WHERE n.sourceVisitId IN :visitIds
            """)
    List<VisitInvoiceIdProjection> findInvoiceIdsByVisitIds(@Param("visitIds") List<Long> visitIds);
}

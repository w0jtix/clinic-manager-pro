package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.KsefInvoiceItem;
import com.clinic.clinicmanager.service.ProductReferenceChecker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface KsefInvoiceItemRepo extends JpaRepository<KsefInvoiceItem, Long>, ProductReferenceChecker {

    List<KsefInvoiceItem> findByInvoiceId(Long invoiceId);

    List<KsefInvoiceItem> findByInvoiceIdOrderByIdAsc(Long invoiceId);

    boolean existsByProductId(Long productId);

    @Query("SELECT DISTINCT it.invoice.id FROM KsefInvoiceItem it WHERE it.lineNumber IS NULL")
    List<Long> findInvoiceIdsWithMissingLineNumber();

    @Transactional
    @Modifying
    @Query("""
            UPDATE KsefInvoiceItem it
            SET it.lineNumber = :lineNumber, it.lineUuid = :lineUuid
            WHERE it.id = :id AND it.lineNumber IS NULL
            """)
    int updateLineIfMissing(@Param("id") Long id,
                            @Param("lineNumber") Integer lineNumber,
                            @Param("lineUuid") String lineUuid);
}

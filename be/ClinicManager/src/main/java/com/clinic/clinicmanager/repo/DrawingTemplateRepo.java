package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.DrawingTemplate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DrawingTemplateRepo extends JpaRepository<DrawingTemplate, Long> {

    @Query(value = "SELECT COUNT(*) FROM (SELECT pg_advisory_xact_lock(:key)) AS lock_holder", nativeQuery = true)
    long acquireTransactionLock(@Param("key") long key);

    List<DrawingTemplate> findByArchivedFalseOrderByDisplayOrderAscIdAsc();

    List<DrawingTemplate> findByArchivedTrueOrderByArchivedAtDescIdDesc();

    long countByArchivedFalse();

    boolean existsByArchivedFalseAndNameIgnoreCase(String name);

    boolean existsByArchivedFalseAndNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsByArchivedTrueAndNameIgnoreCase(String name);

    boolean existsByArchivedTrueAndNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("SELECT COALESCE(MAX(t.displayOrder), 0) FROM DrawingTemplate t WHERE t.archived = false")
    int findMaxActiveDisplayOrder();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM DrawingTemplate t WHERE t.id = :id")
    Optional<DrawingTemplate> lockById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT t FROM DrawingTemplate t WHERE t.id = :id")
    Optional<DrawingTemplate> lockSharedById(@Param("id") Long id);
}

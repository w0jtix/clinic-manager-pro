package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.DrawingMarker;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DrawingMarkerRepo extends JpaRepository<DrawingMarker, Long> {

    @Query(value = "SELECT COUNT(*) FROM (SELECT pg_advisory_xact_lock(:key)) AS lock_holder", nativeQuery = true)
    long acquireTransactionLock(@Param("key") long key);

    List<DrawingMarker> findAllByOrderByDisplayOrderAscIdAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("SELECT COALESCE(MAX(m.displayOrder), 0) FROM DrawingMarker m")
    int findMaxDisplayOrder();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM DrawingMarker m WHERE m.id = :id")
    Optional<DrawingMarker> lockById(@Param("id") Long id);
}

package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.TherapyEntry;
import com.clinic.clinicmanager.repo.projection.TherapyEntryCardDateProjection;
import com.clinic.clinicmanager.repo.projection.TherapyEntryDateProjection;
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
public interface TherapyEntryRepo extends JpaRepository<TherapyEntry, Long> {

    Optional<TherapyEntry> findByIdAndCardId(Long id, Long cardId);

    @Query("""
            SELECT e FROM TherapyEntry e
            WHERE e.card.id = :cardId
              AND e.entryDate = :entryDate
              AND e.invalidatedAt IS NULL
            """)
    Optional<TherapyEntry> findValidByCardIdAndEntryDate(
            @Param("cardId") Long cardId,
            @Param("entryDate") LocalDate entryDate
    );

    @Query("""
            SELECT DISTINCT e.entryDate FROM TherapyEntry e
            WHERE e.card.id = :cardId
              AND e.entryDate < :beforeDate
            ORDER BY e.entryDate DESC
            """)
    List<LocalDate> findEntryDatesBefore(
            @Param("cardId") Long cardId,
            @Param("beforeDate") LocalDate beforeDate,
            Pageable pageable
    );

    @Query("""
            SELECT e FROM TherapyEntry e
            WHERE e.card.id = :cardId
              AND e.entryDate IN :dates
            ORDER BY e.entryDate DESC, e.createdAt ASC, e.id ASC
            """)
    List<TherapyEntry> findByCardIdAndEntryDateIn(
            @Param("cardId") Long cardId,
            @Param("dates") Collection<LocalDate> dates
    );

    @Query("""
            SELECT e.id AS id, e.entryDate AS entryDate FROM TherapyEntry e
            WHERE e.card.client.id = :clientId
              AND e.id IN :ids
            """)
    List<TherapyEntryDateProjection> findEntryDatesByClientIdAndIdIn(
            @Param("clientId") Long clientId,
            @Param("ids") Collection<Long> ids
    );

    @Query("""
            SELECT DISTINCT e.card.id AS cardId, e.entryDate AS entryDate FROM TherapyEntry e
            WHERE e.card.id IN :cardIds
              AND e.entryDate BETWEEN :dateFrom AND :dateTo
              AND e.invalidatedAt IS NULL
            """)
    List<TherapyEntryCardDateProjection> findValidEntryDatesByCardIdInAndDateBetween(
            @Param("cardIds") Collection<Long> cardIds,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo
    );

    List<TherapyEntry> findByCardClientIdAndIdIn(Long clientId, Collection<Long> ids);

    boolean existsByCardClientId(Long clientId);
}

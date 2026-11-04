package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.TherapyEntryRevision;
import com.clinic.clinicmanager.repo.projection.TherapyEntryRevisionRefProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface TherapyEntryRevisionRepo extends JpaRepository<TherapyEntryRevision, Long> {

    List<TherapyEntryRevision> findByEntryIdOrderByChangedAtDescIdDesc(Long entryId);

    @Query("""
            SELECT r FROM TherapyEntryRevision r
            WHERE r.entry.card.client.id = :clientId
              AND r.entry.id IN :entryIds
              AND NOT EXISTS (
                  SELECT o FROM TherapyEntryRevision o
                  WHERE o.entry = r.entry
                    AND (o.changedAt < r.changedAt OR (o.changedAt = r.changedAt AND o.id < r.id))
              )
            """)
    List<TherapyEntryRevision> findOldestByClientIdAndEntryIdIn(
            @Param("clientId") Long clientId,
            @Param("entryIds") Collection<Long> entryIds
    );

    @Query("""
            SELECT r.id AS id, r.entry.id AS entryId FROM TherapyEntryRevision r
            WHERE r.entry.card.client.id = :clientId
              AND r.id IN :ids
            """)
    List<TherapyEntryRevisionRefProjection> findRefsByClientIdAndIdIn(
            @Param("clientId") Long clientId,
            @Param("ids") Collection<Long> ids
    );

    @Query("""
            SELECT r.id AS id, r.entry.id AS entryId FROM TherapyEntryRevision r
            WHERE r.entry.card.client.id = :clientId
              AND r.entry.id IN :entryIds
            ORDER BY r.changedAt ASC, r.id ASC
            """)
    List<TherapyEntryRevisionRefProjection> findOrderedRefsByClientIdAndEntryIdIn(
            @Param("clientId") Long clientId,
            @Param("entryIds") Collection<Long> entryIds
    );

    List<TherapyEntryRevision> findByEntryCardClientIdAndIdIn(Long clientId, Collection<Long> ids);
}

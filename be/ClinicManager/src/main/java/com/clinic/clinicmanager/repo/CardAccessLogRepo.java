package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.CardAccessLog;
import com.clinic.clinicmanager.model.constants.CardAccessAction;
import com.clinic.clinicmanager.repo.projection.CardAccessLogResourceProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CardAccessLogRepo extends JpaRepository<CardAccessLog, Long> {

    @Query(value = """
            SELECT l FROM CardAccessLog l
            WHERE l.clientId = :clientId
              AND l.action NOT IN :excludedActions
            ORDER BY l.at DESC, l.id DESC
            """,
            countQuery = """
            SELECT COUNT(l) FROM CardAccessLog l
            WHERE l.clientId = :clientId
              AND l.action NOT IN :excludedActions
            """)
    Page<CardAccessLog> findHistory(
            @Param("clientId") Long clientId,
            @Param("excludedActions") Collection<CardAccessAction> excludedActions,
            Pageable pageable
    );

    @Query("""
            SELECT l.id AS id, l.resourceId AS resourceId FROM CardAccessLog l
            WHERE l.clientId = :clientId
              AND l.action = :action
              AND l.resourceType = :resourceType
              AND l.resourceId IN :resourceIds
            ORDER BY l.at ASC, l.id ASC
            """)
    List<CardAccessLogResourceProjection> findOrderedResourceRefs(
            @Param("clientId") Long clientId,
            @Param("action") CardAccessAction action,
            @Param("resourceType") String resourceType,
            @Param("resourceIds") Collection<Long> resourceIds
    );
}

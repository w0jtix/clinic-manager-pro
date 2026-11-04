package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.ClientCard;
import com.clinic.clinicmanager.repo.projection.ClientCardCreatedAtProjection;
import com.clinic.clinicmanager.repo.projection.QuestionnaireStateProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientCardRepo extends JpaRepository<ClientCard, Long> {

    Optional<ClientCard> findByClientId(Long clientId);

    boolean existsByClientId(Long clientId);

    @Query("""
            SELECT c.id AS id, c.client.id AS clientId, c.createdAt AS createdAt FROM ClientCard c
            WHERE c.client.id IN :clientIds
            """)
    List<ClientCardCreatedAtProjection> findCreatedAtByClientIdIn(@Param("clientIds") Collection<Long> clientIds);

    @Modifying
    @Query(
            value = """
    INSERT INTO client_card (client_id, version, created_at, created_by_user_id)
    VALUES (:clientId, 0, :createdAt, :createdByUserId)
    ON CONFLICT (client_id) DO NOTHING
    """,
            nativeQuery = true
    )
    int insertIfAbsent(
            @Param("clientId") Long clientId,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("createdByUserId") Long createdByUserId
    );

    @Query(
            value = """
    SELECT questionnaire_version AS version, questionnaire_updated_at AS updatedAt
    FROM client_card
    WHERE id = :cardId
    FOR UPDATE
    """,
            nativeQuery = true
    )
    QuestionnaireStateProjection lockQuestionnaireState(@Param("cardId") Long cardId);

    @Modifying
    @Query(
            value = """
    UPDATE client_card
    SET questionnaire_version = questionnaire_version + 1,
        questionnaire_updated_at = :updatedAt,
        questionnaire_updated_by_user_id = :updatedByUserId
    WHERE id = :cardId
    """,
            nativeQuery = true
    )
    int bumpQuestionnaireVersion(
            @Param("cardId") Long cardId,
            @Param("updatedAt") LocalDateTime updatedAt,
            @Param("updatedByUserId") Long updatedByUserId
    );

    @Query(
            value = """
    SELECT id
    FROM client_card
    WHERE id = :cardId
    FOR UPDATE
    """,
            nativeQuery = true
    )
    Long lockCard(@Param("cardId") Long cardId);
}

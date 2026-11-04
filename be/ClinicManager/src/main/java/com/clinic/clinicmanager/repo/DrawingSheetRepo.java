package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.DrawingSheet;
import com.clinic.clinicmanager.model.constants.DrawingSheetStatus;
import com.clinic.clinicmanager.repo.projection.DrawingTemplateUsageProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DrawingSheetRepo extends JpaRepository<DrawingSheet, Long> {

    List<DrawingSheet> findByCardIdOrderBySheetNumberDesc(Long cardId);

    boolean existsByTemplateId(Long templateId);

    boolean existsByTemplateImageId(Long templateImageId);

    Optional<DrawingSheet> findByIdAndCardClientId(Long id, Long clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM DrawingSheet s WHERE s.id = :sheetId AND s.card.id = :cardId")
    Optional<DrawingSheet> lockByIdAndCardId(@Param("sheetId") Long sheetId, @Param("cardId") Long cardId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT s FROM DrawingSheet s
    WHERE s.card.id = :cardId AND s.templateId = :templateId AND s.status = :status
    """)
    Optional<DrawingSheet> lockByCardIdAndTemplateIdAndStatus(@Param("cardId") Long cardId,
                                                              @Param("templateId") Long templateId,
                                                              @Param("status") DrawingSheetStatus status);

    @Query("""
    SELECT COALESCE(MAX(s.sheetNumber), 0) FROM DrawingSheet s
    WHERE s.card.id = :cardId AND s.templateId = :templateId
    """)
    int findMaxSheetNumber(@Param("cardId") Long cardId, @Param("templateId") Long templateId);

    @Query("""
    SELECT s.templateId AS templateId, COUNT(DISTINCT s.card.id) AS clientCount
    FROM DrawingSheet s
    GROUP BY s.templateId
    """)
    List<DrawingTemplateUsageProjection> countClientsPerTemplate();

    @Query("SELECT COUNT(DISTINCT s.card.id) FROM DrawingSheet s WHERE s.templateId = :templateId")
    long countClientsByTemplateId(@Param("templateId") Long templateId);

    @Query(
            value = """
    SELECT id
    FROM drawing_sheet
    WHERE template_id = :templateId AND status = 'OPEN'
    FOR UPDATE
    """,
            nativeQuery = true
    )
    List<Long> lockOpenSheetIdsByTemplateId(@Param("templateId") Long templateId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = """
    UPDATE drawing_sheet s
    SET template_image_id = :templateImageId, width = :width, height = :height
    WHERE s.template_id = :templateId
      AND s.status = 'OPEN'
      AND NOT EXISTS (SELECT 1 FROM drawing_layer l WHERE l.sheet_id = s.id)
    """,
            nativeQuery = true
    )
    int rebaseBlankOpenSheets(
            @Param("templateId") Long templateId,
            @Param("templateImageId") Long templateImageId,
            @Param("width") int width,
            @Param("height") int height
    );
}

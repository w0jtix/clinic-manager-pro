package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.DrawingTemplateImage;
import com.clinic.clinicmanager.repo.projection.DrawingTemplateImageInfoProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DrawingTemplateImageRepo extends JpaRepository<DrawingTemplateImage, Long> {

    @Query("""
    SELECT i.id AS id, i.templateId AS templateId, i.width AS width, i.height AS height, i.sizeBytes AS sizeBytes
    FROM DrawingTemplateImage i
    WHERE i.id = :id
    """)
    Optional<DrawingTemplateImageInfoProjection> findInfoById(@Param("id") Long id);

    @Query("""
    SELECT i.id AS id, i.templateId AS templateId, i.width AS width, i.height AS height, i.sizeBytes AS sizeBytes
    FROM DrawingTemplateImage i
    WHERE i.id IN :ids
    """)
    List<DrawingTemplateImageInfoProjection> findInfoByIdIn(@Param("ids") Collection<Long> ids);

    @Query("SELECT i.image FROM DrawingTemplateImage i WHERE i.id = :id")
    Optional<byte[]> findImageById(@Param("id") Long id);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM DrawingTemplateImage i WHERE i.id = :id")
    int deleteImageById(@Param("id") Long id);
}

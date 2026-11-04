package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.DrawingLayer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DrawingLayerRepo extends JpaRepository<DrawingLayer, Long> {

    List<DrawingLayer> findBySheetIdInOrderByCreatedAtAscIdAsc(Collection<Long> sheetIds);

    Optional<DrawingLayer> findBySheetIdAndAuthorUserId(Long sheetId, Long authorUserId);

    Optional<DrawingLayer> findByIdAndSheetIdAndSheetCardClientId(Long id, Long sheetId, Long clientId);

    boolean existsBySheetId(Long sheetId);

    boolean existsBySheetCardClientId(Long clientId);
}

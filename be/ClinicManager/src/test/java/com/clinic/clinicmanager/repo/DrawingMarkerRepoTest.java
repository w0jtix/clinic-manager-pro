package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.DrawingMarker;
import com.clinic.clinicmanager.model.constants.DrawingMarkerShape;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class DrawingMarkerRepoTest extends AbstractPostgresTest {

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 10, 1, 9, 0);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private DrawingMarkerRepo markerRepo;

    private DrawingMarker marker(String name, int displayOrder) {
        return markerRepo.saveAndFlush(DrawingMarker.builder()
                .name(name).shape(DrawingMarkerShape.TRIANGLE).color("#ff0000").filled(true)
                .displayOrder(displayOrder)
                .createdAt(CREATED_AT).createdByUserId(1L).updatedAt(CREATED_AT)
                .build());
    }

    @Test
    void nameIsUniqueIgnoringCase() {
        marker("Modzel", 1);

        assertThatThrownBy(() -> marker("MODZEL", 2)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nameExistenceQueries_ignoreCase_andExcludeOwnId() {
        DrawingMarker modzel = marker("Modzel", 1);
        DrawingMarker odcisk = marker("Odcisk", 2);

        assertThat(markerRepo.existsByNameIgnoreCase("modzel")).isTrue();
        assertThat(markerRepo.existsByNameIgnoreCase("Brodawka")).isFalse();
        assertThat(markerRepo.existsByNameIgnoreCaseAndIdNot("MODZEL", modzel.getId())).isFalse();
        assertThat(markerRepo.existsByNameIgnoreCaseAndIdNot("MODZEL", odcisk.getId())).isTrue();
    }

    @Test
    void list_isOrderedByDisplayOrderThenId_andMaxOrderIsReported() {
        assertThat(markerRepo.findMaxDisplayOrder()).isZero();
        DrawingMarker third = marker("C", 3);
        DrawingMarker firstA = marker("A", 1);
        DrawingMarker firstB = marker("B", 1);

        assertThat(markerRepo.findAllByOrderByDisplayOrderAscIdAsc())
                .extracting(DrawingMarker::getId)
                .containsExactly(firstA.getId(), firstB.getId(), third.getId());
        assertThat(markerRepo.findMaxDisplayOrder()).isEqualTo(3);
    }

    @Test
    void shapeIsStoredAsString_andAdvisoryLockWorks() {
        DrawingMarker marker = marker("Modzel", 1);
        em.clear();

        Object shape = em.getEntityManager()
                .createNativeQuery("SELECT shape FROM drawing_marker WHERE id = ?1")
                .setParameter(1, marker.getId())
                .getSingleResult();

        assertThat(shape).isEqualTo("TRIANGLE");
        assertThat(markerRepo.acquireTransactionLock(42L)).isEqualTo(1L);
        assertThat(markerRepo.lockById(marker.getId())).get().extracting(DrawingMarker::getName).isEqualTo("Modzel");
    }
}

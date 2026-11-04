package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.CardSettings;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CardSettingsRepoTest extends AbstractPostgresTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CardSettingsRepo cardSettingsRepo;

    private long countRows() {
        return ((Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM card_settings")
                .getSingleResult()).longValue();
    }

    @Test
    void migration_insertsSingleRowWithId1_englishDisabled() {
        assertThat(countRows()).isEqualTo(1);

        CardSettings settings = cardSettingsRepo.findById(1L).orElseThrow();
        assertThat(settings.getQuestionnaireEnglishEnabled()).isFalse();
        assertThat(settings.getUpdatedAt()).isNotNull();
    }

    @Test
    void getSettings_returnsRowWithId1() {
        assertThat(cardSettingsRepo.getSettings().getId()).isEqualTo(1L);
    }

    @Test
    void flagUpdate_isPersisted_withoutCreatingNewRow() {
        CardSettings settings = cardSettingsRepo.getSettings();
        settings.setQuestionnaireEnglishEnabled(true);
        settings.setUpdatedAt(LocalDateTime.now());
        cardSettingsRepo.saveAndFlush(settings);
        em.clear();

        assertThat(cardSettingsRepo.getSettings().getQuestionnaireEnglishEnabled()).isTrue();
        assertThat(countRows()).isEqualTo(1);
    }

    @Test
    void missingRow_isRecreatedWithId1_onSave() {
        cardSettingsRepo.deleteAllInBatch();
        em.clear();

        CardSettings defaults = cardSettingsRepo.getSettings();
        assertThat(defaults.getId()).isEqualTo(1L);
        assertThat(defaults.getQuestionnaireEnglishEnabled()).isFalse();

        defaults.setUpdatedAt(LocalDateTime.now());
        cardSettingsRepo.saveAndFlush(defaults);
        em.clear();

        assertThat(cardSettingsRepo.findById(1L)).isPresent();
        assertThat(countRows()).isEqualTo(1);
    }
}

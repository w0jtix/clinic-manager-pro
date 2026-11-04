package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.support.AbstractPostgresTest;

import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.Review;
import com.clinic.clinicmanager.model.constants.ReviewSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ReviewRepoTest extends AbstractPostgresTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ReviewRepo reviewRepo;

    private Client client;

    @BeforeEach
    void setUp() {
        client = em.persistAndFlush(Client.builder().firstName("Jan").lastName("Kowalski").build());
    }

    private void review(Client reviewClient, ReviewSource source, boolean isUsed) {
        em.persistAndFlush(Review.builder()
                .client(reviewClient)
                .source(source)
                .isUsed(isUsed)
                .issueDate(LocalDate.of(2025, 3, 1))
                .build());
    }

    @Test
    void existsByClientIdAndSourceAndIsUsedFalse_isTrue_whenOnlyOneOfManyReviewsIsUnused() {
        review(client, ReviewSource.GOOGLE, true);
        review(client, ReviewSource.GOOGLE, true);
        review(client, ReviewSource.GOOGLE, false);

        assertThat(reviewRepo.existsByClientIdAndSource(client.getId(), ReviewSource.GOOGLE)).isTrue();
        assertThat(reviewRepo.existsByClientIdAndSourceAndIsUsedFalse(client.getId(), ReviewSource.GOOGLE)).isTrue();
    }

    @Test
    void existsByClientIdAndSourceAndIsUsedFalse_isFalse_whenEveryReviewIsUsed() {
        review(client, ReviewSource.GOOGLE, true);
        review(client, ReviewSource.GOOGLE, true);

        assertThat(reviewRepo.existsByClientIdAndSource(client.getId(), ReviewSource.GOOGLE)).isTrue();
        assertThat(reviewRepo.existsByClientIdAndSourceAndIsUsedFalse(client.getId(), ReviewSource.GOOGLE)).isFalse();
    }

    @Test
    void existsByClientIdAndSource_separatesSources() {
        review(client, ReviewSource.GOOGLE, true);

        assertThat(reviewRepo.existsByClientIdAndSource(client.getId(), ReviewSource.BOOKSY)).isFalse();
        assertThat(reviewRepo.existsByClientIdAndSourceAndIsUsedFalse(client.getId(), ReviewSource.BOOKSY)).isFalse();
    }

    @Test
    void existsByClientIdAndSource_excludesOtherClients() {
        Client other = em.persistAndFlush(Client.builder().firstName("Anna").lastName("Nowak").build());
        review(other, ReviewSource.GOOGLE, false);

        assertThat(reviewRepo.existsByClientIdAndSource(client.getId(), ReviewSource.GOOGLE)).isFalse();
        assertThat(reviewRepo.existsByClientIdAndSourceAndIsUsedFalse(client.getId(), ReviewSource.GOOGLE)).isFalse();
    }
}

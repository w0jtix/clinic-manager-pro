package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.QuestionnaireAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface QuestionnaireAnswerRepo extends JpaRepository<QuestionnaireAnswer, Long> {

    List<QuestionnaireAnswer> findByCardId(Long cardId);

    boolean existsByCardClientId(Long clientId);

    @Query("SELECT DISTINCT a.field.id FROM QuestionnaireAnswer a")
    Set<Long> findFieldIdsWithAnswers();
}

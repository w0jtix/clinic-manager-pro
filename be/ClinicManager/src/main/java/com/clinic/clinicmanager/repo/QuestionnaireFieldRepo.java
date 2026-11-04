package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.QuestionnaireField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestionnaireFieldRepo extends JpaRepository<QuestionnaireField, Long> {

    List<QuestionnaireField> findAllByOrderByArchivedAscDisplayOrderAscIdAsc();
}

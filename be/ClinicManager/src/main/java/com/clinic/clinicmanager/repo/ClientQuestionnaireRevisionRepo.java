package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.ClientQuestionnaireRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ClientQuestionnaireRevisionRepo extends JpaRepository<ClientQuestionnaireRevision, Long> {

    List<ClientQuestionnaireRevision> findByClientIdAndIdIn(Long clientId, Collection<Long> ids);
}

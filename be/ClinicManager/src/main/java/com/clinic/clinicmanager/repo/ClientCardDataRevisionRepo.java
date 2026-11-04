package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.ClientCardDataRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ClientCardDataRevisionRepo extends JpaRepository<ClientCardDataRevision, Long> {

    List<ClientCardDataRevision> findByClientIdAndIdIn(Long clientId, Collection<Long> ids);
}

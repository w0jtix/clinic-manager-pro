package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.KsefSyncStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KsefSyncStatusRepo extends JpaRepository<KsefSyncStatus, Long> {
}

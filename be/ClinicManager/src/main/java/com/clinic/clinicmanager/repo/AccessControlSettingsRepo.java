package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.AccessControlSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccessControlSettingsRepo extends JpaRepository<AccessControlSettings, Long> {
}

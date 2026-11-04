package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.CardSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CardSettingsRepo extends JpaRepository<CardSettings, Long> {

    long SETTINGS_ID = 1L;

    default CardSettings getSettings() {
        return findById(SETTINGS_ID).orElseGet(() -> CardSettings.builder().id(SETTINGS_ID).build());
    }
}

package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.KsefSyncPeriod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KsefSyncPeriodRepo extends JpaRepository<KsefSyncPeriod, Long> {

    Optional<KsefSyncPeriod> findByYearAndMonth(int year, int month);
}

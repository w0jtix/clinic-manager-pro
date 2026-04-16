package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.model.BonusParamsSnapshot;

import java.util.Optional;

public interface BonusParamsSnapshotService {

    void createMonthlySnapshots();

    Optional<BonusParamsSnapshot> getSnapshot(Long employeeId, int year, int month);
}

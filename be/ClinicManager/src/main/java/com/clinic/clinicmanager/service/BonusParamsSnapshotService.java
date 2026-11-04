package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.BonusChangePointDTO;
import com.clinic.clinicmanager.model.BonusParamsSnapshot;

import java.util.List;
import java.util.Optional;

public interface BonusParamsSnapshotService {

    void createMonthlySnapshots();

    Optional<BonusParamsSnapshot> getSnapshot(Long employeeId, int year, int month);

    List<BonusChangePointDTO> getBonusChangeHistory(Long employeeId);
}

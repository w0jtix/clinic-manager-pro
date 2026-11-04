package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.BonusChangePointDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.AppSettings;
import com.clinic.clinicmanager.model.BonusParamsSnapshot;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.StatSettings;
import com.clinic.clinicmanager.repo.AppSettingsRepo;
import com.clinic.clinicmanager.repo.BonusParamsSnapshotRepo;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.repo.StatSettingsRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.BonusParamsSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BonusParamsSnapshotServiceImpl implements BonusParamsSnapshotService {

    private final BonusParamsSnapshotRepo bonusParamsSnapshotRepo;
    private final EmployeeRepo employeeRepo;
    private final AppSettingsRepo appSettingsRepo;
    private final StatSettingsRepo statSettingsRepo;
    private final AuditLogService auditLogService;

    @Override
    @Scheduled(cron = "0 59 23 28-31 * ?")
    @Transactional
    public void createMonthlySnapshots() {
        LocalDate today = LocalDate.now();
        if (today.getDayOfMonth() != today.lengthOfMonth()) {
            return;
        }

        int year = today.getYear();
        int month = today.getMonthValue();

        AppSettings appSettings = appSettingsRepo.getSettings();
        StatSettings statSettings = statSettingsRepo.getSettings();

        double boostNetRate = appSettings.getBoostNetRate() != null ? appSettings.getBoostNetRate() : 0.0;
        BigDecimal bonusThreshold = BigDecimal.valueOf(statSettings.getBonusThreshold());

        List<Employee> activeEmployees = employeeRepo.findAllActive();
        List<BonusParamsSnapshot> toSave = new ArrayList<>();

        for (Employee employee : activeEmployees) {
            Optional<BonusParamsSnapshot> existing = bonusParamsSnapshotRepo
                    .findByEmployeeIdAndYearAndMonth(employee.getId(), year, month);

            if (existing.isPresent()) {
                continue;
            }

            toSave.add(BonusParamsSnapshot.builder()
                    .employee(employee)
                    .year(year)
                    .month(month)
                    .bonusPercent(employee.getBonusPercent())
                    .saleBonusPercent(employee.getSaleBonusPercent())
                    .boostNetRate(boostNetRate)
                    .bonusThreshold(bonusThreshold)
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        if (!toSave.isEmpty()) {
            List<BonusParamsSnapshot> saved = bonusParamsSnapshotRepo.saveAll(toSave);
            for (BonusParamsSnapshot snapshot : saved) {
                auditLogService.logCreate("BonusParamsSnapshot", snapshot.getId(),
                        snapshot.getEmployee().getName() + " " + snapshot.getYear() + "/" + snapshot.getMonth(), snapshot);
            }
        }
    }

    @Override
    public Optional<BonusParamsSnapshot> getSnapshot(Long employeeId, int year, int month) {
        return bonusParamsSnapshotRepo.findByEmployeeIdAndYearAndMonth(employeeId, year, month);
    }

    @Override
    public List<BonusChangePointDTO> getBonusChangeHistory(Long employeeId) {
        Employee employee = employeeRepo.findOneById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));

        List<BonusParamsSnapshot> snapshots = bonusParamsSnapshotRepo
                .findByEmployeeIdOrderByYearAscMonthAsc(employeeId);

        if (snapshots.isEmpty()) {
            LocalDate now = LocalDate.now();
            return new ArrayList<>(List.of(BonusChangePointDTO.builder()
                    .year(now.getYear())
                    .month(now.getMonthValue())
                    .bonusPercent(employee.getBonusPercent())
                    .saleBonusPercent(employee.getSaleBonusPercent())
                    .build()));
        }

        List<BonusChangePointDTO> changePoints = new ArrayList<>();
        Double prevBonus = null;
        Double prevSaleBonus = null;
        boolean isFirst = true;

        for (BonusParamsSnapshot snapshot : snapshots) {
            Double bonus = snapshot.getBonusPercent();
            Double saleBonus = snapshot.getSaleBonusPercent();

            if (isFirst) {
                changePoints.add(BonusChangePointDTO.builder()
                        .year(snapshot.getYear())
                        .month(snapshot.getMonth())
                        .bonusPercent(bonus)
                        .saleBonusPercent(saleBonus)
                        .build());
                isFirst = false;
            } else {
                boolean bonusChanged = !Objects.equals(bonus, prevBonus);
                boolean saleBonusChanged = !Objects.equals(saleBonus, prevSaleBonus);

                if (bonusChanged || saleBonusChanged) {
                    changePoints.add(BonusChangePointDTO.builder()
                            .year(snapshot.getYear())
                            .month(snapshot.getMonth())
                            .bonusPercent(bonusChanged ? bonus : null)
                            .bonusPercentChange(bonusChanged ? delta(bonus, prevBonus) : null)
                            .saleBonusPercent(saleBonusChanged ? saleBonus : null)
                            .saleBonusPercentChange(saleBonusChanged ? delta(saleBonus, prevSaleBonus) : null)
                            .build());
                }
            }

            prevBonus = bonus;
            prevSaleBonus = saleBonus;
        }

        return changePoints;
    }

    private Double delta(Double current, Double previous) {
        if (current == null || previous == null) {
            return null;
        }
        return Math.round((current - previous) * 100.0) / 100.0;
    }
}

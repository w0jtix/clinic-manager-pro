package com.clinic.clinicmanager.service;

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
import com.clinic.clinicmanager.service.impl.BonusParamsSnapshotServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BonusParamsSnapshotServiceImplTest {

    @Mock BonusParamsSnapshotRepo bonusParamsSnapshotRepo;
    @Mock EmployeeRepo employeeRepo;
    @Mock AppSettingsRepo appSettingsRepo;
    @Mock StatSettingsRepo statSettingsRepo;
    @Mock AuditLogService auditLogService;

    @InjectMocks
    BonusParamsSnapshotServiceImpl snapshotService;

    private Employee employee(Long id) {
        return Employee.builder().id(id).name("Anna").lastName("Kowalska")
                .bonusPercent(10.0).saleBonusPercent(5.0).build();
    }

    private AppSettings appSettings(Integer boostNetRate) {
        return AppSettings.builder().id(1L).boostNetRate(boostNetRate).build();
    }

    private StatSettings statSettings() {
        return StatSettings.builder().id(1L).bonusThreshold(3000).build();
    }

    @Test
    void createMonthlySnapshots_shouldDoNothing_whenNotLastDayOfMonth() {
        try (MockedStatic<LocalDate> mocked = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            LocalDate notLastDay = LocalDate.of(2025, 4, 14);
            mocked.when(LocalDate::now).thenReturn(notLastDay);

            snapshotService.createMonthlySnapshots();

            verifyNoInteractions(employeeRepo, appSettingsRepo, statSettingsRepo, bonusParamsSnapshotRepo);
        }
    }

    @Test
    void createMonthlySnapshots_shouldSaveSnapshot_whenLastDayAndNoExisting() {
        try (MockedStatic<LocalDate> mocked = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            LocalDate lastDay = LocalDate.of(2025, 4, 30);
            mocked.when(LocalDate::now).thenReturn(lastDay);

            Employee emp = employee(1L);
            when(appSettingsRepo.getSettings()).thenReturn(appSettings(45));
            when(statSettingsRepo.getSettings()).thenReturn(statSettings());
            when(employeeRepo.findAllActive()).thenReturn(List.of(emp));
            when(bonusParamsSnapshotRepo.findByEmployeeIdAndYearAndMonth(1L, 2025, 4))
                    .thenReturn(Optional.empty());

            BonusParamsSnapshot saved = BonusParamsSnapshot.builder().id(10L).employee(emp)
                    .year(2025).month(4).build();
            when(bonusParamsSnapshotRepo.saveAll(any())).thenReturn(List.of(saved));

            snapshotService.createMonthlySnapshots();

            ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
            verify(bonusParamsSnapshotRepo).saveAll(captor.capture());
            assertEquals(1, captor.getValue().size());
            verify(auditLogService).logCreate(eq("BonusParamsSnapshot"), eq(10L), anyString(), any());
        }
    }

    @Test
    void createMonthlySnapshots_shouldSkipEmployee_whenSnapshotAlreadyExists() {
        try (MockedStatic<LocalDate> mocked = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            LocalDate lastDay = LocalDate.of(2025, 4, 30);
            mocked.when(LocalDate::now).thenReturn(lastDay);

            Employee emp = employee(1L);
            when(appSettingsRepo.getSettings()).thenReturn(appSettings(45));
            when(statSettingsRepo.getSettings()).thenReturn(statSettings());
            when(employeeRepo.findAllActive()).thenReturn(List.of(emp));
            when(bonusParamsSnapshotRepo.findByEmployeeIdAndYearAndMonth(1L, 2025, 4))
                    .thenReturn(Optional.of(BonusParamsSnapshot.builder().id(5L).build()));

            snapshotService.createMonthlySnapshots();

            verify(bonusParamsSnapshotRepo, never()).saveAll(any());
            verifyNoInteractions(auditLogService);
        }
    }

    @Test
    void createMonthlySnapshots_shouldNotSaveOrAudit_whenAllEmployeesAlreadyHaveSnapshots() {
        try (MockedStatic<LocalDate> mocked = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            LocalDate lastDay = LocalDate.of(2025, 1, 31);
            mocked.when(LocalDate::now).thenReturn(lastDay);

            Employee emp1 = employee(1L);
            Employee emp2 = employee(2L);
            when(appSettingsRepo.getSettings()).thenReturn(appSettings(45));
            when(statSettingsRepo.getSettings()).thenReturn(statSettings());
            when(employeeRepo.findAllActive()).thenReturn(List.of(emp1, emp2));
            when(bonusParamsSnapshotRepo.findByEmployeeIdAndYearAndMonth(anyLong(), eq(2025), eq(1)))
                    .thenReturn(Optional.of(BonusParamsSnapshot.builder().id(99L).build()));

            snapshotService.createMonthlySnapshots();

            verify(bonusParamsSnapshotRepo, never()).saveAll(any());
            verifyNoInteractions(auditLogService);
        }
    }

    @Test
    void createMonthlySnapshots_shouldUseZeroBoostNetRate_whenAppSettingsBoostNetRateIsNull() {
        try (MockedStatic<LocalDate> mocked = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            LocalDate lastDay = LocalDate.of(2025, 4, 30);
            mocked.when(LocalDate::now).thenReturn(lastDay);

            Employee emp = employee(1L);
            when(appSettingsRepo.getSettings()).thenReturn(appSettings(null));
            when(statSettingsRepo.getSettings()).thenReturn(statSettings());
            when(employeeRepo.findAllActive()).thenReturn(List.of(emp));
            when(bonusParamsSnapshotRepo.findByEmployeeIdAndYearAndMonth(1L, 2025, 4))
                    .thenReturn(Optional.empty());

            BonusParamsSnapshot saved = BonusParamsSnapshot.builder().id(10L).employee(emp)
                    .year(2025).month(4).build();
            when(bonusParamsSnapshotRepo.saveAll(any())).thenReturn(List.of(saved));

            snapshotService.createMonthlySnapshots();

            ArgumentCaptor<List> captor = ArgumentCaptor.forClass(List.class);
            verify(bonusParamsSnapshotRepo).saveAll(captor.capture());
            BonusParamsSnapshot snapshot = (BonusParamsSnapshot) captor.getValue().getFirst();
            assertEquals(0.0, snapshot.getBoostNetRate());
        }
    }

    @Test
    void createMonthlySnapshots_shouldNotSaveOrAudit_whenNoActiveEmployees() {
        try (MockedStatic<LocalDate> mocked = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            LocalDate lastDay = LocalDate.of(2025, 4, 30);
            mocked.when(LocalDate::now).thenReturn(lastDay);

            when(appSettingsRepo.getSettings()).thenReturn(appSettings(45));
            when(statSettingsRepo.getSettings()).thenReturn(statSettings());
            when(employeeRepo.findAllActive()).thenReturn(List.of());

            snapshotService.createMonthlySnapshots();

            verify(bonusParamsSnapshotRepo, never()).saveAll(any());
            verifyNoInteractions(auditLogService);
        }
    }

    private BonusParamsSnapshot snapshot(int year, int month, Double bonusPercent, Double saleBonusPercent) {
        return BonusParamsSnapshot.builder()
                .year(year).month(month)
                .bonusPercent(bonusPercent).saleBonusPercent(saleBonusPercent)
                .build();
    }

    @Test
    void getBonusChangeHistory_shouldThrow_whenEmployeeNotFound() {
        when(employeeRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> snapshotService.getBonusChangeHistory(99L));
        verify(bonusParamsSnapshotRepo, never()).findByEmployeeIdOrderByYearAscMonthAsc(anyLong());
    }

    @Test
    void getBonusChangeHistory_shouldSeedBaselineFromLiveValues_whenNoSnapshots() {
        try (MockedStatic<LocalDate> mocked = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            LocalDate now = LocalDate.of(2026, 8, 3);
            mocked.when(LocalDate::now).thenReturn(now);
            when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee(1L)));
            when(bonusParamsSnapshotRepo.findByEmployeeIdOrderByYearAscMonthAsc(1L)).thenReturn(List.of());

            List<BonusChangePointDTO> result = snapshotService.getBonusChangeHistory(1L);

            assertEquals(1, result.size());
            BonusChangePointDTO baseline = result.getFirst();
            assertEquals(2026, baseline.getYear());
            assertEquals(8, baseline.getMonth());
            assertEquals(10.0, baseline.getBonusPercent());
            assertEquals(5.0, baseline.getSaleBonusPercent());
            assertNull(baseline.getBonusPercentChange());
            assertNull(baseline.getSaleBonusPercentChange());
        }
    }

    @Test
    void getBonusChangeHistory_shouldReturnBaselineWithBothValues_forSingleSnapshot() {
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee(1L)));
        when(bonusParamsSnapshotRepo.findByEmployeeIdOrderByYearAscMonthAsc(1L))
                .thenReturn(List.of(snapshot(2026, 3, 10.0, 5.0)));

        List<BonusChangePointDTO> result = snapshotService.getBonusChangeHistory(1L);

        assertEquals(1, result.size());
        BonusChangePointDTO baseline = result.getFirst();
        assertEquals(2026, baseline.getYear());
        assertEquals(3, baseline.getMonth());
        assertEquals(10.0, baseline.getBonusPercent());
        assertEquals(5.0, baseline.getSaleBonusPercent());
        assertNull(baseline.getBonusPercentChange());
        assertNull(baseline.getSaleBonusPercentChange());
    }

    @Test
    void getBonusChangeHistory_shouldEmitOnlyChangedBonus_whenBonusChangesAndSaleStable() {
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee(1L)));
        when(bonusParamsSnapshotRepo.findByEmployeeIdOrderByYearAscMonthAsc(1L))
                .thenReturn(List.of(
                        snapshot(2026, 3, 10.0, 5.0),
                        snapshot(2026, 4, 10.0, 5.0),
                        snapshot(2026, 5, 12.0, 5.0)));

        List<BonusChangePointDTO> result = snapshotService.getBonusChangeHistory(1L);

        assertEquals(2, result.size());
        BonusChangePointDTO change = result.get(1);
        assertEquals(2026, change.getYear());
        assertEquals(5, change.getMonth());
        assertEquals(12.0, change.getBonusPercent());
        assertEquals(2.0, change.getBonusPercentChange());
        assertNull(change.getSaleBonusPercent());
        assertNull(change.getSaleBonusPercentChange());
    }

    @Test
    void getBonusChangeHistory_shouldEmitOnlyChangedSaleBonus_whenSaleChangesAndBonusStable() {
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee(1L)));
        when(bonusParamsSnapshotRepo.findByEmployeeIdOrderByYearAscMonthAsc(1L))
                .thenReturn(List.of(
                        snapshot(2026, 3, 10.0, 5.0),
                        snapshot(2026, 4, 10.0, 7.0)));

        List<BonusChangePointDTO> result = snapshotService.getBonusChangeHistory(1L);

        assertEquals(2, result.size());
        BonusChangePointDTO change = result.get(1);
        assertEquals(4, change.getMonth());
        assertNull(change.getBonusPercent());
        assertNull(change.getBonusPercentChange());
        assertEquals(7.0, change.getSaleBonusPercent());
        assertEquals(2.0, change.getSaleBonusPercentChange());
    }

    @Test
    void getBonusChangeHistory_shouldEmitBothValues_whenBothChangeSameMonth() {
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee(1L)));
        when(bonusParamsSnapshotRepo.findByEmployeeIdOrderByYearAscMonthAsc(1L))
                .thenReturn(List.of(
                        snapshot(2026, 3, 10.0, 5.0),
                        snapshot(2026, 4, 12.0, 7.0)));

        List<BonusChangePointDTO> result = snapshotService.getBonusChangeHistory(1L);

        assertEquals(2, result.size());
        BonusChangePointDTO change = result.get(1);
        assertEquals(12.0, change.getBonusPercent());
        assertEquals(2.0, change.getBonusPercentChange());
        assertEquals(7.0, change.getSaleBonusPercent());
        assertEquals(2.0, change.getSaleBonusPercentChange());
    }

    @Test
    void getBonusChangeHistory_shouldReturnOnlyBaseline_whenNoParamsEverChange() {
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee(1L)));
        when(bonusParamsSnapshotRepo.findByEmployeeIdOrderByYearAscMonthAsc(1L))
                .thenReturn(List.of(
                        snapshot(2026, 3, 10.0, 5.0),
                        snapshot(2026, 4, 10.0, 5.0),
                        snapshot(2026, 5, 10.0, 5.0)));

        List<BonusChangePointDTO> result = snapshotService.getBonusChangeHistory(1L);

        assertEquals(1, result.size());
        assertEquals(3, result.getFirst().getMonth());
    }

    @Test
    void getSnapshot_shouldDelegateToRepo() {
        BonusParamsSnapshot snapshot = BonusParamsSnapshot.builder().id(1L).build();
        when(bonusParamsSnapshotRepo.findByEmployeeIdAndYearAndMonth(1L, 2025, 4))
                .thenReturn(Optional.of(snapshot));

        Optional<BonusParamsSnapshot> result = snapshotService.getSnapshot(1L, 2025, 4);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
    }

    @Test
    void getSnapshot_shouldReturnEmpty_whenNotFound() {
        when(bonusParamsSnapshotRepo.findByEmployeeIdAndYearAndMonth(99L, 2025, 4))
                .thenReturn(Optional.empty());

        Optional<BonusParamsSnapshot> result = snapshotService.getSnapshot(99L, 2025, 4);

        assertTrue(result.isEmpty());
    }
}

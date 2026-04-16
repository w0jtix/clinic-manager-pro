package com.clinic.clinicmanager.service;

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

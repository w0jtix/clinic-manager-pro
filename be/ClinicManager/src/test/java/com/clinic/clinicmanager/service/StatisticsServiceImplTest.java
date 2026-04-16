package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.EmployeeBonusDTO;
import com.clinic.clinicmanager.DTO.EmployeeRevenueDTO;
import com.clinic.clinicmanager.DTO.EmployeeStatsDTO;
import com.clinic.clinicmanager.DTO.request.EmployeeBonusFilterDTO;
import com.clinic.clinicmanager.DTO.request.EmployeeRevenueFilterDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.AppSettings;
import com.clinic.clinicmanager.model.BonusParamsSnapshot;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.Payment;
import com.clinic.clinicmanager.model.StatSettings;
import com.clinic.clinicmanager.model.Visit;
import com.clinic.clinicmanager.model.constants.ChartMode;
import com.clinic.clinicmanager.model.constants.EmploymentType;
import com.clinic.clinicmanager.model.constants.PaymentMethod;
import com.clinic.clinicmanager.repo.AppSettingsRepo;
import com.clinic.clinicmanager.repo.BonusParamsSnapshotRepo;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.repo.OrderProductRepo;
import com.clinic.clinicmanager.repo.StatSettingsRepo;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.repo.VisitRepo;
import com.clinic.clinicmanager.service.impl.StatisticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceImplTest {

    @Mock VisitRepo visitRepo;
    @Mock EmployeeRepo employeeRepo;
    @Mock OrderProductRepo orderProductRepo;
    @Mock UserRepo userRepo;
    @Mock StatSettingsRepo statSettingsRepo;
    @Mock AppSettingsRepo appSettingsRepo;
    @Mock BonusParamsSnapshotRepo bonusParamsSnapshotRepo;

    @InjectMocks
    StatisticsServiceImpl statisticsService;

    private Employee employee;
    private StatSettings statSettings;
    private AppSettings appSettings;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
                .id(1L).name("Anna").lastName("Kowalska")
                .isDeleted(false).employmentType(EmploymentType.FULL)
                .bonusPercent(10.0).saleBonusPercent(5.0).build();

        statSettings = StatSettings.builder()
                .id(1L).bonusThreshold(1000).servicesRevenueGoal(3000)
                .productsRevenueGoal(500).saleBonusPayoutMonths(Set.of(1, 4, 7, 10)).build();

        appSettings = AppSettings.builder()
                .id(1L).boostNetRate(45).build();
    }

    private EmployeeRevenueFilterDTO filter(ChartMode mode, int year, Integer month) {
        EmployeeRevenueFilterDTO f = new EmployeeRevenueFilterDTO();
        f.setMode(mode);
        f.setYear(year);
        f.setMonth(month);
        return f;
    }

    private EmployeeBonusFilterDTO bonusFilter(long employeeId, int year, int month) {
        EmployeeBonusFilterDTO f = new EmployeeBonusFilterDTO();
        f.setEmployeeId(employeeId);
        f.setYear(year);
        f.setMonth(month);
        return f;
    }

    @Test
    void getEmployeeRevenue_shouldReturn12DataPointsPerEmployee_whenModeIsMonthly() {
        when(visitRepo.findMonthlyRevenueByYear(2025)).thenReturn(List.of());
        when(employeeRepo.findAllActive()).thenReturn(List.of(employee));

        EmployeeRevenueDTO result = statisticsService.getEmployeeRevenue(filter(ChartMode.MONTHLY, 2025, null));

        assertEquals(1, result.getSeries().size());
        assertEquals(12, result.getSeries().getFirst().getData().size());
    }

    @Test
    void getEmployeeRevenue_shouldFillZerosForMissingMonths_whenModeIsMonthly() {
        when(visitRepo.findMonthlyRevenueByYear(2025)).thenReturn(List.of());
        when(employeeRepo.findAllActive()).thenReturn(List.of(employee));

        EmployeeRevenueDTO result = statisticsService.getEmployeeRevenue(filter(ChartMode.MONTHLY, 2025, null));

        result.getSeries().getFirst().getData()
                .forEach(val -> assertEquals(BigDecimal.ZERO, val));
    }

    @Test
    void getEmployeeRevenue_shouldReturnCorrectDayCount_whenModeIsDaily() {
        when(visitRepo.findDailyRevenueByYearAndMonth(2025, 4)).thenReturn(List.of());
        when(employeeRepo.findAllActive()).thenReturn(List.of(employee));

        EmployeeRevenueDTO result = statisticsService.getEmployeeRevenue(filter(ChartMode.DAILY, 2025, 4));

        assertEquals(30, result.getSeries().getFirst().getData().size());
    }

    @Test
    void getEmployeeRevenue_shouldReturnEmptySeries_whenNoActiveEmployees() {
        when(visitRepo.findMonthlyRevenueByYear(2025)).thenReturn(List.of());
        when(employeeRepo.findAllActive()).thenReturn(List.of());

        EmployeeRevenueDTO result = statisticsService.getEmployeeRevenue(filter(ChartMode.MONTHLY, 2025, null));

        assertTrue(result.getSeries().isEmpty());
    }

    @Test
    void getEmployeeStats_shouldUseYearlyDates_whenModeIsMonthly() {
        when(employeeRepo.findAllActive()).thenReturn(List.of(employee));
        when(statSettingsRepo.getSettings()).thenReturn(statSettings);
        when(userRepo.findAvatarByEmployeeId(any())).thenReturn(null);
        when(visitRepo.sumServicesRevenue(any(), any(), any())).thenReturn(null);
        when(visitRepo.sumAbsenceFeeRedemptions(any(), any(), any())).thenReturn(null);
        when(visitRepo.sumVoucherPayments(any(), any(), any())).thenReturn(null);
        when(visitRepo.sumVouchersSoldValue(any(), any(), any())).thenReturn(null);
        when(visitRepo.sumProductsRevenue(any(), any(), any())).thenReturn(null);
        when(visitRepo.sumTotalRevenue(any(), any(), any())).thenReturn(null);
        when(visitRepo.sumHoursWithClients(any(), any(), any())).thenReturn(null);
        when(visitRepo.countServicesDone(any(), any(), any())).thenReturn(null);
        when(visitRepo.countProductsSold(any(), any(), any())).thenReturn(null);
        when(visitRepo.countVouchersSold(any(), any(), any())).thenReturn(null);
        when(visitRepo.countNewClients(any(), any(), any())).thenReturn(null);
        when(visitRepo.countNewBoostClients(any(), any(), any())).thenReturn(null);
        when(visitRepo.countClientsWithSecondVisit(any())).thenReturn(null);
        when(visitRepo.countTotalClients(any())).thenReturn(null);
        when(visitRepo.countBoostClientsWithSecondVisit(any())).thenReturn(null);
        when(visitRepo.countTotalBoostClients(any())).thenReturn(null);
        when(visitRepo.findTopSellingServiceName(any(), any(), any())).thenReturn(null);
        when(visitRepo.findTopSellingProductName(any(), any(), any())).thenReturn(null);

        List<EmployeeStatsDTO> result = statisticsService.getEmployeeStats(filter(ChartMode.MONTHLY, 2025, null));

        assertEquals(1, result.size());
        verify(visitRepo).sumServicesRevenue(eq(1L),
                eq(java.time.LocalDate.of(2025, 1, 1)),
                eq(java.time.LocalDate.of(2025, 12, 31)));
    }

    @Test
    void getEmployeeStats_shouldUseMonthlyDates_whenModeIsDaily() {
        when(employeeRepo.findAllActive()).thenReturn(List.of(employee));
        when(statSettingsRepo.getSettings()).thenReturn(statSettings);
        when(userRepo.findAvatarByEmployeeId(any())).thenReturn(null);
        lenient().when(visitRepo.sumServicesRevenue(any(), any(), any())).thenReturn(null);
        lenient().when(visitRepo.sumAbsenceFeeRedemptions(any(), any(), any())).thenReturn(null);
        lenient().when(visitRepo.sumVoucherPayments(any(), any(), any())).thenReturn(null);
        lenient().when(visitRepo.sumVouchersSoldValue(any(), any(), any())).thenReturn(null);
        lenient().when(visitRepo.sumProductsRevenue(any(), any(), any())).thenReturn(null);
        lenient().when(visitRepo.sumTotalRevenue(any(), any(), any())).thenReturn(null);
        lenient().when(visitRepo.sumHoursWithClients(any(), any(), any())).thenReturn(null);
        lenient().when(visitRepo.countServicesDone(any(), any(), any())).thenReturn(null);
        lenient().when(visitRepo.countProductsSold(any(), any(), any())).thenReturn(null);
        lenient().when(visitRepo.countVouchersSold(any(), any(), any())).thenReturn(null);
        lenient().when(visitRepo.countNewClients(any(), any(), any())).thenReturn(null);
        lenient().when(visitRepo.countNewBoostClients(any(), any(), any())).thenReturn(null);
        lenient().when(visitRepo.countClientsWithSecondVisit(any())).thenReturn(null);
        lenient().when(visitRepo.countTotalClients(any())).thenReturn(null);
        lenient().when(visitRepo.countBoostClientsWithSecondVisit(any())).thenReturn(null);
        lenient().when(visitRepo.countTotalBoostClients(any())).thenReturn(null);
        lenient().when(visitRepo.findTopSellingServiceName(any(), any(), any())).thenReturn(null);
        lenient().when(visitRepo.findTopSellingProductName(any(), any(), any())).thenReturn(null);

        statisticsService.getEmployeeStats(filter(ChartMode.DAILY, 2025, 4));

        verify(visitRepo).sumServicesRevenue(eq(1L),
                eq(java.time.LocalDate.of(2025, 4, 1)),
                eq(java.time.LocalDate.of(2025, 4, 30)));
    }

    @Test
    void getEmployeeBonus_shouldThrowResourceNotFoundException_whenEmployeeNotFound() {
        when(employeeRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> statisticsService.getEmployeeBonus(bonusFilter(99L, 2025, 4)));
    }

    @Test
    void getEmployeeBonus_shouldUseBonusParamsFromSnapshot_whenSnapshotPresent() {
        BonusParamsSnapshot snapshot = BonusParamsSnapshot.builder()
                .id(1L).employee(employee).year(2025).month(4)
                .boostNetRate(30.0).bonusThreshold(800.0)
                .bonusPercent(15.0).saleBonusPercent(8.0).build();

        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(statSettingsRepo.getSettings()).thenReturn(statSettings);
        when(appSettingsRepo.getSettings()).thenReturn(appSettings);
        when(bonusParamsSnapshotRepo.findByEmployeeIdAndYearAndMonth(1L, 2025, 4)).thenReturn(Optional.of(snapshot));
        when(visitRepo.findVisitsForBonusWithPayments(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.findVisitsForBonusWithSaleItems(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.findBoostVisitsWithItems(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.sumProductsRevenue(any(), any(), any())).thenReturn(null);

        EmployeeBonusDTO result = statisticsService.getEmployeeBonus(bonusFilter(1L, 2025, 4));

        assertEquals(15.0, result.getBonusPercent());
        assertEquals(800.0, result.getBonusThreshold());
    }

    @Test
    void getEmployeeBonus_shouldUseCurrentParamsFromEmployee_whenNoSnapshot() {
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(statSettingsRepo.getSettings()).thenReturn(statSettings);
        when(appSettingsRepo.getSettings()).thenReturn(appSettings);
        when(bonusParamsSnapshotRepo.findByEmployeeIdAndYearAndMonth(1L, 2025, 4)).thenReturn(Optional.empty());
        when(visitRepo.findVisitsForBonusWithPayments(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.findVisitsForBonusWithSaleItems(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.findBoostVisitsWithItems(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.sumProductsRevenue(any(), any(), any())).thenReturn(null);

        EmployeeBonusDTO result = statisticsService.getEmployeeBonus(bonusFilter(1L, 2025, 4));

        assertEquals(10.0, result.getBonusPercent());
        assertEquals(1000.0, result.getBonusThreshold());
    }

    @Test
    void getEmployeeBonus_shouldReturnZeroBonusAmount_whenRevenueDoesNotExceedThreshold() {
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(statSettingsRepo.getSettings()).thenReturn(statSettings);
        when(appSettingsRepo.getSettings()).thenReturn(appSettings);
        when(bonusParamsSnapshotRepo.findByEmployeeIdAndYearAndMonth(1L, 2025, 4)).thenReturn(Optional.empty());
        when(visitRepo.findVisitsForBonusWithPayments(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.findVisitsForBonusWithSaleItems(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.findBoostVisitsWithItems(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.sumProductsRevenue(any(), any(), any())).thenReturn(null);

        EmployeeBonusDTO result = statisticsService.getEmployeeBonus(bonusFilter(1L, 2025, 4));

        assertEquals(0.0, result.getBonusAmount());
    }

    @Test
    void getEmployeeBonus_shouldCalculateBonusCorrectly_whenRevenueExceedsThreshold() {
        Client client = Client.builder().id(1L).firstName("Jan").lastName("Nowak").isDeleted(false).build();
        Payment payment = Payment.builder().id(1L).method(PaymentMethod.CARD).amount(1500.0).build();
        Visit visit = Visit.builder().id(1L).client(client).date(java.time.LocalDate.of(2025, 4, 10)).build();
        visit.setPayments(List.of(payment));

        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(statSettingsRepo.getSettings()).thenReturn(statSettings);
        when(appSettingsRepo.getSettings()).thenReturn(appSettings);
        when(bonusParamsSnapshotRepo.findByEmployeeIdAndYearAndMonth(1L, 2025, 4)).thenReturn(Optional.empty());
        when(visitRepo.findVisitsForBonusWithPayments(any(), any(), any())).thenReturn(List.of(visit));
        when(visitRepo.findVisitsForBonusWithSaleItems(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.findBoostVisitsWithItems(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.sumProductsRevenue(any(), any(), any())).thenReturn(null);

        EmployeeBonusDTO result = statisticsService.getEmployeeBonus(bonusFilter(1L, 2025, 4));

        // adjustedRevenue = 1500 - 0 (products) - 0 (vouchers) = 1500
        // bonusAmount = (1500 - 1000) * 10% = 50.0
        assertEquals(50.0, result.getBonusAmount());
        assertEquals(1500.0, result.getMonthlyServicesRevenue());
    }

    @Test
    void getEmployeeBonus_shouldFetchOnlyCurrentPeriod_whenQuarterPositionIsOne() {
        // month=2: neither 2 nor nextMonth=3 is in payoutMonths {1,4,7,10} → position 1
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(statSettingsRepo.getSettings()).thenReturn(statSettings);
        when(appSettingsRepo.getSettings()).thenReturn(appSettings);
        when(bonusParamsSnapshotRepo.findByEmployeeIdAndYearAndMonth(any(), any(), any())).thenReturn(Optional.empty());
        when(visitRepo.findVisitsForBonusWithPayments(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.findVisitsForBonusWithSaleItems(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.findBoostVisitsWithItems(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.sumProductsRevenue(any(), any(), any())).thenReturn(null);

        statisticsService.getEmployeeBonus(bonusFilter(1L, 2025, 2));

        verify(visitRepo, times(1)).findVisitsForBonusWithSaleItems(any(), any(), any());
    }

    @Test
    void getEmployeeBonus_shouldFetchCurrentAndPrevPeriod_whenQuarterPositionIsTwo() {
        // month=3: nextMonth=4 is in payoutMonths {1,4,7,10} → position 2
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(statSettingsRepo.getSettings()).thenReturn(statSettings);
        when(appSettingsRepo.getSettings()).thenReturn(appSettings);
        when(bonusParamsSnapshotRepo.findByEmployeeIdAndYearAndMonth(any(), any(), any())).thenReturn(Optional.empty());
        when(visitRepo.findVisitsForBonusWithPayments(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.findVisitsForBonusWithSaleItems(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.findBoostVisitsWithItems(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.sumProductsRevenue(any(), any(), any())).thenReturn(null);

        statisticsService.getEmployeeBonus(bonusFilter(1L, 2025, 3));

        verify(visitRepo, times(2)).findVisitsForBonusWithSaleItems(any(), any(), any());
    }

    @Test
    void getEmployeeBonus_shouldFetchAllThreePeriods_whenQuarterPositionIsThree() {
        // month=4: 4 is in payoutMonths {1,4,7,10} → position 3
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(statSettingsRepo.getSettings()).thenReturn(statSettings);
        when(appSettingsRepo.getSettings()).thenReturn(appSettings);
        when(bonusParamsSnapshotRepo.findByEmployeeIdAndYearAndMonth(any(), any(), any())).thenReturn(Optional.empty());
        when(visitRepo.findVisitsForBonusWithPayments(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.findVisitsForBonusWithSaleItems(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.findBoostVisitsWithItems(any(), any(), any())).thenReturn(List.of());
        when(visitRepo.sumProductsRevenue(any(), any(), any())).thenReturn(null);

        statisticsService.getEmployeeBonus(bonusFilter(1L, 2025, 4));

        verify(visitRepo, times(3)).findVisitsForBonusWithSaleItems(any(), any(), any());
    }
}

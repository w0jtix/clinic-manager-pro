package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.CompanyFinancialSummaryDTO;
import com.clinic.clinicmanager.DTO.CompanyRevenueDTO;
import com.clinic.clinicmanager.DTO.CompanyStatsDTO;
import com.clinic.clinicmanager.DTO.request.EmployeeRevenueFilterDTO;
import com.clinic.clinicmanager.model.constants.ChartMode;
import com.clinic.clinicmanager.repo.ClientDebtRepo;
import com.clinic.clinicmanager.repo.CompanyExpenseRepo;
import com.clinic.clinicmanager.repo.VisitRepo;
import com.clinic.clinicmanager.repo.projection.CompanyExpenseProjection;
import com.clinic.clinicmanager.repo.projection.CompanyRevenueProjection;
import com.clinic.clinicmanager.service.impl.CompanyStatsServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyStatsServiceImplTest {

    @Mock VisitRepo visitRepo;
    @Mock CompanyExpenseRepo companyExpenseRepo;
    @Mock ClientDebtRepo clientDebtRepo;

    @InjectMocks
    CompanyStatsServiceImpl statsService;

    private EmployeeRevenueFilterDTO filter(ChartMode mode, int year, Integer month) {
        EmployeeRevenueFilterDTO f = new EmployeeRevenueFilterDTO();
        f.setMode(mode);
        f.setYear(year);
        f.setMonth(month);
        return f;
    }

    private void stubAllRevenueRepoMethods(Double totalRevenue, Double voucherPayments) {
        when(visitRepo.sumCompanyTotalRevenue(any(), any())).thenReturn(totalRevenue);
        when(visitRepo.sumCompanyVoucherPayments(any(), any())).thenReturn(voucherPayments);
        when(visitRepo.sumOffTheBookRevenue(any(), any())).thenReturn(null);
        when(companyExpenseRepo.sumTotalExpenses(any(), any())).thenReturn(null);
    }

    private void stubAllStatsRepoMethods() {
        when(visitRepo.sumCompanyServicesRevenue(any(), any())).thenReturn(null);
        when(visitRepo.sumCompanyVoucherPayments(any(), any())).thenReturn(null);
        when(visitRepo.sumCompanyVouchersSoldValue(any(), any())).thenReturn(null);
        when(visitRepo.sumCompanyAllDebtRedemptions(any(), any())).thenReturn(null);
        when(visitRepo.sumCompanyPartialVisitsServicesValue(any(), any())).thenReturn(null);
        when(visitRepo.sumCompanyPartialVisitsPayments(any(), any())).thenReturn(null);
        when(visitRepo.sumCompanyProductsRevenue(any(), any())).thenReturn(null);
        when(visitRepo.sumCompanyTotalRevenue(any(), any())).thenReturn(null);
        when(companyExpenseRepo.sumTotalExpenses(any(), any())).thenReturn(null);
        when(companyExpenseRepo.findExpensesByCategory(any(), any())).thenReturn(List.of());
    }

    @Test
    void getFinancialSummary_shouldUseYearlyDates_whenModeIsMonthly() {
        stubAllRevenueRepoMethods(1000.0, 0.0);

        statsService.getFinancialSummary(filter(ChartMode.MONTHLY, 2025, null));

        verify(visitRepo).sumCompanyTotalRevenue(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        verify(visitRepo).sumCompanyTotalRevenue(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
    }

    @Test
    void getFinancialSummary_shouldReturnNullLastYearFields_whenModeIsMonthly() {
        stubAllRevenueRepoMethods(1000.0, 100.0);

        CompanyFinancialSummaryDTO result = statsService.getFinancialSummary(filter(ChartMode.MONTHLY, 2025, null));

        assertNull(result.getLastYearRevenue());
        assertNull(result.getLastYearExpenses());
        assertNull(result.getLastYearIncome());
        assertNull(result.getRevenueChangeVsLastYear());
    }

    @Test
    void getFinancialSummary_shouldPopulateLastYearFields_whenModeIsDaily() {
        stubAllRevenueRepoMethods(1000.0, 0.0);

        CompanyFinancialSummaryDTO result = statsService.getFinancialSummary(filter(ChartMode.DAILY, 2025, 4));

        assertNotNull(result.getLastYearRevenue());
        assertNotNull(result.getLastYearExpenses());
        assertNotNull(result.getLastYearIncome());
        verify(visitRepo).sumCompanyTotalRevenue(LocalDate.of(2024, 4, 1), LocalDate.of(2024, 4, 30));
    }

    @Test
    void getFinancialSummary_shouldCalculateAdjustedRevenue_subtractingVoucherPayments() {
        stubAllRevenueRepoMethods(1000.0, 200.0);

        CompanyFinancialSummaryDTO result = statsService.getFinancialSummary(filter(ChartMode.MONTHLY, 2025, null));

        assertEquals(800.0, result.getCurrentRevenue());
    }

    @Test
    void getFinancialSummary_shouldHandleNullRepoValues_asZero() {
        stubAllRevenueRepoMethods(null, null);

        assertDoesNotThrow(() -> statsService.getFinancialSummary(filter(ChartMode.MONTHLY, 2025, null)));
    }

    @Test
    void getFinancialSummary_shouldReturn100_whenPreviousIsZeroAndCurrentPositive() {
        when(visitRepo.sumCompanyTotalRevenue(any(), any())).thenAnswer(inv -> {
            LocalDate from = inv.getArgument(0);
            return from.getYear() == 2025 ? 500.0 : 0.0;
        });
        when(visitRepo.sumCompanyVoucherPayments(any(), any())).thenReturn(null);
        when(visitRepo.sumOffTheBookRevenue(any(), any())).thenReturn(null);
        when(companyExpenseRepo.sumTotalExpenses(any(), any())).thenReturn(null);

        CompanyFinancialSummaryDTO result = statsService.getFinancialSummary(filter(ChartMode.MONTHLY, 2025, null));

        assertEquals(100.0, result.getRevenueChangeVsPrevPeriod());
    }

    @Test
    void getFinancialSummary_shouldReturnMinus100_whenPreviousIsZeroAndCurrentNegative() {
        when(visitRepo.sumCompanyTotalRevenue(any(), any())).thenAnswer(inv -> {
            LocalDate from = inv.getArgument(0);
            return from.getYear() == 2025 ? -100.0 : 0.0;
        });
        when(visitRepo.sumCompanyVoucherPayments(any(), any())).thenReturn(null);
        when(visitRepo.sumOffTheBookRevenue(any(), any())).thenReturn(null);
        when(companyExpenseRepo.sumTotalExpenses(any(), any())).thenReturn(null);

        CompanyFinancialSummaryDTO result = statsService.getFinancialSummary(filter(ChartMode.MONTHLY, 2025, null));

        assertEquals(-100.0, result.getRevenueChangeVsPrevPeriod());
    }

    @Test
    void getFinancialSummary_shouldReturnZeroChange_whenBothPeriodsAreZero() {
        stubAllRevenueRepoMethods(null, null);

        CompanyFinancialSummaryDTO result = statsService.getFinancialSummary(filter(ChartMode.MONTHLY, 2025, null));

        assertEquals(0.0, result.getRevenueChangeVsPrevPeriod());
    }

    @Test
    void getCompanyStats_shouldCalculateSharesCorrectly_whenRevenuePositive() {
        when(visitRepo.sumCompanyTotalRevenue(any(), any())).thenReturn(1000.0);
        when(visitRepo.sumCompanyVoucherPayments(any(), any())).thenReturn(0.0);
        when(visitRepo.sumCompanyServicesRevenue(any(), any())).thenReturn(800.0);
        when(visitRepo.sumCompanyVouchersSoldValue(any(), any())).thenReturn(0.0);
        when(visitRepo.sumCompanyAllDebtRedemptions(any(), any())).thenReturn(0.0);
        when(visitRepo.sumCompanyPartialVisitsServicesValue(any(), any())).thenReturn(0.0);
        when(visitRepo.sumCompanyPartialVisitsPayments(any(), any())).thenReturn(0.0);
        when(visitRepo.sumCompanyProductsRevenue(any(), any())).thenReturn(200.0);
        when(companyExpenseRepo.sumTotalExpenses(any(), any())).thenReturn(0.0);
        when(companyExpenseRepo.findExpensesByCategory(any(), any())).thenReturn(List.of());

        CompanyStatsDTO result = statsService.getCompanyStats(filter(ChartMode.MONTHLY, 2025, null));

        assertTrue(result.getServicesRevenueShare() > 0);
        assertTrue(result.getProductsRevenueShare() > 0);
    }

    @Test
    void getCompanyStats_shouldReturnZeroShares_whenTotalRevenueIsZero() {
        stubAllStatsRepoMethods();

        CompanyStatsDTO result = statsService.getCompanyStats(filter(ChartMode.MONTHLY, 2025, null));

        assertEquals(0.0, result.getServicesRevenueShare());
        assertEquals(0.0, result.getProductsRevenueShare());
    }

    @Test
    void getCompanyRevenueChart_shouldReturn12DataPoints_whenModeIsMonthly() {
        when(visitRepo.findCompanyMonthlyRevenueByYear(2025)).thenReturn(List.of());
        when(companyExpenseRepo.findMonthlyExpensesByYear(2025)).thenReturn(List.of());

        CompanyRevenueDTO result = statsService.getCompanyRevenueChart(filter(ChartMode.MONTHLY, 2025, null));

        assertEquals(12, result.getRevenueData().size());
        assertEquals(12, result.getExpensesData().size());
        assertEquals(12, result.getIncomeData().size());
    }

    @Test
    void getCompanyRevenueChart_shouldReturn30DataPoints_forApril_whenModeIsDaily() {
        when(visitRepo.findCompanyDailyRevenueByYearAndMonth(2025, 4)).thenReturn(List.of());
        when(companyExpenseRepo.findDailyExpensesByYearAndMonth(2025, 4)).thenReturn(List.of());

        CompanyRevenueDTO result = statsService.getCompanyRevenueChart(filter(ChartMode.DAILY, 2025, 4));

        assertEquals(30, result.getRevenueData().size());
    }

    @Test
    void getCompanyRevenueChart_shouldUseProjectionData_whenDataPresent() {
        CompanyRevenueProjection rev = mock(CompanyRevenueProjection.class);
        when(rev.getPeriod()).thenReturn(1);
        when(rev.getRevenue()).thenReturn(BigDecimal.valueOf(500));

        CompanyExpenseProjection exp = mock(CompanyExpenseProjection.class);
        when(exp.getPeriod()).thenReturn(1);
        when(exp.getTotalExpense()).thenReturn(BigDecimal.valueOf(200));

        when(visitRepo.findCompanyMonthlyRevenueByYear(2025)).thenReturn(List.of(rev));
        when(companyExpenseRepo.findMonthlyExpensesByYear(2025)).thenReturn(List.of(exp));

        CompanyRevenueDTO result = statsService.getCompanyRevenueChart(filter(ChartMode.MONTHLY, 2025, null));

        assertEquals(BigDecimal.valueOf(500), result.getRevenueData().getFirst());
        assertEquals(BigDecimal.valueOf(200), result.getExpensesData().getFirst());
        assertEquals(BigDecimal.valueOf(300), result.getIncomeData().getFirst());
    }
}

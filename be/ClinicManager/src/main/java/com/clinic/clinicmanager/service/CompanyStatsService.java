package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.CompanyFinancialSummaryDTO;
import com.clinic.clinicmanager.DTO.CompanyRevenueDTO;
import com.clinic.clinicmanager.DTO.CompanyStatsDTO;
import com.clinic.clinicmanager.DTO.request.EmployeeRevenueFilterDTO;

public interface CompanyStatsService {

    CompanyFinancialSummaryDTO getFinancialSummary(EmployeeRevenueFilterDTO filter);

    CompanyStatsDTO getCompanyStats(EmployeeRevenueFilterDTO filter);

    CompanyRevenueDTO getCompanyRevenueChart(EmployeeRevenueFilterDTO filter);
}

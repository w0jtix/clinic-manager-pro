package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.EmployeeRevenueDTO;
import com.clinic.clinicmanager.DTO.EmployeeBonusDTO;
import com.clinic.clinicmanager.DTO.EmployeeStatsDTO;
import com.clinic.clinicmanager.DTO.request.EmployeeBonusFilterDTO;
import com.clinic.clinicmanager.DTO.request.EmployeeRevenueFilterDTO;

import java.util.List;

public interface StatisticsService {

    EmployeeRevenueDTO getEmployeeRevenue(EmployeeRevenueFilterDTO filter);

    List<EmployeeStatsDTO> getEmployeeStats(EmployeeRevenueFilterDTO filter);

    EmployeeBonusDTO getEmployeeBonus(EmployeeBonusFilterDTO filter);
}

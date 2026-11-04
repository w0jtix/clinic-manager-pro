package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.EmployeeDTO;

import java.util.List;

public interface EmployeeService {

    List<EmployeeDTO> getAllEmployees();

    EmployeeDTO getEmployeeById(Long id);

    EmployeeDTO createEmployee(EmployeeDTO employee);

    EmployeeDTO updateEmployee(Long id, EmployeeDTO employee);

    void deleteEmployeeById(Long id);
}

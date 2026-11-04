package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.BonusChangePointDTO;
import com.clinic.clinicmanager.DTO.EmployeeDTO;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.service.BonusParamsSnapshotService;
import com.clinic.clinicmanager.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/employee")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final BonusParamsSnapshotService bonusParamsSnapshotService;

    @GetMapping("/all")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<List<EmployeeDTO>> getAllEmployees() {
        List<EmployeeDTO> employeeList = employeeService.getAllEmployees();
        return new ResponseEntity<>(employeeList, employeeList.isEmpty() ? HttpStatus.NO_CONTENT : HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<EmployeeDTO> getEmployeeById(@PathVariable(value="id") Long id) {
        EmployeeDTO employee = employeeService.getEmployeeById(id);
        return new ResponseEntity<>(employee, HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<EmployeeDTO> createEmployee(@Valid @RequestBody EmployeeDTO employee) {
        EmployeeDTO createdEmployee = employeeService.createEmployee(employee);
        return new ResponseEntity<>(createdEmployee, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<EmployeeDTO> updateEmployee(@PathVariable(value="id") Long id, @Valid @RequestBody EmployeeDTO employee) {
        EmployeeDTO saved = employeeService.updateEmployee(id, employee);
        return new ResponseEntity<>(saved, HttpStatus.OK);
    }

    @GetMapping("/{id}/bonus-change-history")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<List<BonusChangePointDTO>> getBonusChangeHistory(@PathVariable(value="id") Long id) {
        List<BonusChangePointDTO> history = bonusParamsSnapshotService.getBonusChangeHistory(id);
        return new ResponseEntity<>(history, history.isEmpty() ? HttpStatus.NO_CONTENT : HttpStatus.OK);
    }
}

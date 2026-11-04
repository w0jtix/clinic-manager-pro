package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.EmployeeDTO;
import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.repo.UserRepo;
import com.clinic.clinicmanager.repo.projection.EmployeeAccountRoleProjection;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.EmployeeService;
import com.clinic.clinicmanager.utils.DrawingColors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepo employeeRepo;
    private final AuditLogService auditLogService;
    private final UserRepo userRepo;

    @Override
    public List<EmployeeDTO> getAllEmployees() {
        List<Employee> employees = employeeRepo.findAllActive();
        if (employees.isEmpty()) {
            return List.of();
        }
        Map<Long, EmployeeAccount> accounts = findAccounts(employees.stream().map(Employee::getId).toList());
        return employees.stream()
                .map(employee -> toDTO(employee, accounts.get(employee.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public EmployeeDTO getEmployeeById(Long id) {
        Employee employee = employeeRepo.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with given id: " + id));
        return toDTO(employee, findAccounts(List.of(id)).get(id));
    }

    private EmployeeDTO toDTO(Employee employee, EmployeeAccount account) {
        return account != null
                ? new EmployeeDTO(employee, account.role(), account.avatar())
                : new EmployeeDTO(employee);
    }

    private Map<Long, EmployeeAccount> findAccounts(Collection<Long> employeeIds) {
        Map<Long, Map<Long, List<EmployeeAccountRoleProjection>>> rowsByEmployeeAndUser =
                userRepo.findAccountRolesByEmployeeIdIn(employeeIds).stream()
                        .filter(row -> row.getRoleName() != null)
                        .collect(Collectors.groupingBy(
                                EmployeeAccountRoleProjection::getEmployeeId,
                                Collectors.groupingBy(EmployeeAccountRoleProjection::getUserId)));
        Map<Long, EmployeeAccount> accounts = new HashMap<>();
        rowsByEmployeeAndUser.forEach((employeeId, rowsByUser) -> {
            List<EmployeeAccount> userAccounts = rowsByUser.values().stream()
                    .map(this::toAccount)
                    .toList();
            String highest = AppRoleHierarchy.highestRole(userAccounts.stream()
                    .map(EmployeeAccount::role)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
            if (highest != null) {
                String avatar = userAccounts.stream()
                        .filter(account -> highest.equals(account.role()))
                        .map(EmployeeAccount::avatar)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);
                accounts.put(employeeId, new EmployeeAccount(highest, avatar));
            }
        });
        return accounts;
    }

    private EmployeeAccount toAccount(List<EmployeeAccountRoleProjection> userRows) {
        Set<String> roles = userRows.stream()
                .map(row -> row.getRoleName().name())
                .collect(Collectors.toSet());
        return new EmployeeAccount(AppRoleHierarchy.highestRole(roles), userRows.getFirst().getAvatar());
    }

    private record EmployeeAccount(String role, String avatar) {
    }

    @Override
    public EmployeeDTO createEmployee(EmployeeDTO employee) {
        try{
            if(isNull(employee)){
                return null;
            }
            Employee toSave = employee.toEntity();
            toSave.setDrawingColor(DrawingColors.pick(employeeRepo.findAllDrawingColors()));
            Employee savedEmployee = employeeRepo.save(toSave);
            EmployeeDTO savedDTO = new EmployeeDTO(savedEmployee);
            auditLogService.logCreate("Employee", savedDTO.getId(), savedDTO.getName() + savedDTO.getLastName(), savedDTO);
            return savedDTO;
        } catch (ResourceNotFoundException | ConflictException | ValidationException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new CreationException("Failed to create Employee.", e);
        }

    }

    @Override
    public EmployeeDTO updateEmployee(Long id, EmployeeDTO employee) {
        try{
            Employee existing = employeeRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found with given id: " + id));
            EmployeeDTO oldEmployeeSnapshot = new EmployeeDTO(existing);

            employee.setId(id);
            Employee toSave = employee.toEntity();
            toSave.setDrawingColor(existing.getDrawingColor());
            EmployeeDTO savedDTO = new EmployeeDTO(employeeRepo.save(toSave));
            auditLogService.logUpdate("Employee", id, oldEmployeeSnapshot.getName() + oldEmployeeSnapshot.getLastName(), oldEmployeeSnapshot, savedDTO);
            return savedDTO;
        } catch (ResourceNotFoundException | ConflictException | ValidationException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update Employee.", e);
        }
    }

    @Override
    public void deleteEmployeeById(Long id) {
        try {
            Employee employee = employeeRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + id));

            if (employee.getIsDeleted()) {
                throw new DeletionException("Employee is already soft-deleted.");
            }

            EmployeeDTO employeeSnapshot = new EmployeeDTO(employee);
            employee.softDelete();
            employeeRepo.save(employee);
            auditLogService.logDelete("Employee", id, employeeSnapshot.getName() + employeeSnapshot.getLastName(), employeeSnapshot);
        } catch (ResourceNotFoundException | DeletionException | ConflictException | ValidationException
                 | AccessDeniedException | CreationException | UpdateException e) {
            throw e;
        } catch (Exception e) {
            throw new DeletionException("Failed to delete Employee.", e);
        }
    }
}

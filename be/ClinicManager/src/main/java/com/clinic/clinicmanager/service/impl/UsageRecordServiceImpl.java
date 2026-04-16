package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.UsageRecordDTO;
import com.clinic.clinicmanager.DTO.request.UsageRecordFilterDTO;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.InsufficientSupplyException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.UsageRecord;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.repo.UsageRecordRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.UsageRecordService;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
public class UsageRecordServiceImpl implements UsageRecordService {
    private final UsageRecordRepo usageRecordRepo;
    private final ProductRepo productRepo;
    private final EmployeeRepo employeeRepo;
    private final AuditLogService auditLogService;
    private final OwnershipService ownershipService;

    @Override
    public UsageRecordDTO getUsageRecordById(Long id) {
        return new UsageRecordDTO(usageRecordRepo.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("UsageRecord not found with given id: " + id)));
    }

    @Override
    public Page<UsageRecordDTO> getUsageRecords(UsageRecordFilterDTO filter, int page, int size) {
        if (isNull(filter)) {
            filter = new UsageRecordFilterDTO();
        }

        LocalDate startDate = filter.getStartDate() != null ? filter.getStartDate()  : LocalDate.of(1900, 1, 1);
        LocalDate endDate = filter.getEndDate() != null ? filter.getEndDate() : LocalDate.of(2100, 12, 31);

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("usageDate"), Sort.Order.desc("id")));

        Page<UsageRecord> usageRecords = usageRecordRepo.findAllWithFilters(
                filter.getKeyword(),
                filter.getEmployeeIds(),
                filter.getUsageReason(),
                startDate,
                endDate,
                pageable);

        return usageRecords.map(UsageRecordDTO::new);
    }

    @Override
    @Transactional
    public UsageRecordDTO createUsageRecord(UsageRecordDTO usageRecordDTO) {
        try {
            Product product = productRepo.findOneById(usageRecordDTO.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + usageRecordDTO.getProduct().getId()));

            Employee employee = employeeRepo.findOneById(usageRecordDTO.getEmployee().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + usageRecordDTO.getEmployee().getId()));

            product.reduceSupply(usageRecordDTO.getQuantity());
            productRepo.save(product);

            UsageRecord usageRecord = UsageRecord.builder()
                    .product(product)
                    .employee(employee)
                    .usageDate(usageRecordDTO.getUsageDate())
                    .quantity(usageRecordDTO.getQuantity())
                    .usageReason(usageRecordDTO.getUsageReason())
                    .createdByUserId(SessionUtils.getUserIdFromSession())
                    .build();

            UsageRecordDTO savedDTO = new UsageRecordDTO(usageRecordRepo.save(usageRecord));
            auditLogService.logCreate("UsageRecord", savedDTO.getId(), "Zużycie: " + savedDTO.getProduct().getName(), savedDTO);
            return savedDTO;
        } catch (ResourceNotFoundException | InsufficientSupplyException e) {
            throw e;
        } catch (Exception e) {
            throw new CreationException("Failed to create UsageRecord. Reason: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public List<UsageRecordDTO> createUsageRecords(List<UsageRecordDTO> usageRecords) {
        return usageRecords.stream()
                .map(this::createUsageRecord)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteUsageRecordById(Long id) {
        try {
            UsageRecord usageRecord = usageRecordRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("UsageRecord not found with id: " + id));

            ownershipService.checkOwnershipOrAdmin(usageRecord.getCreatedByUserId());
            UsageRecordDTO usageRecordSnapshot = new UsageRecordDTO(usageRecord);

            Product product = usageRecord.getProduct();
            if(product.getIsDeleted()) {
                product.restore(usageRecord.getQuantity());
            } else {
                product.addToSupply(usageRecord.getQuantity());
            }
            productRepo.save(product);

            usageRecordRepo.deleteById(id);
            auditLogService.logDelete("UsageRecord", id, "Zużycie: " + usageRecordSnapshot.getProduct().getName(), usageRecordSnapshot);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DeletionException("Failed to delete UsageRecord. Reason: " + e.getMessage(), e);
        }
    }
}

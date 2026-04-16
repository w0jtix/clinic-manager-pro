package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.BrandDTO;
import com.clinic.clinicmanager.DTO.SupplierDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.model.Supplier;
import com.clinic.clinicmanager.repo.SupplierRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.SupplierService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {
    private final SupplierRepo supplierRepo;
    private final AuditLogService auditLogService;

    @Override
    public SupplierDTO getSupplierById(Long id) {
        return new SupplierDTO(supplierRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Supplier not found with given id: " + id)));
    }

    @Override
    public List<SupplierDTO> getSuppliers() {
        return supplierRepo.findAll()
                .stream()
                .map(SupplierDTO::new)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public SupplierDTO createSupplier(SupplierDTO supplier) {
        try{
            if (supplierAlreadyExists(supplier)) {
                throw new ConflictException("Supplier already exists: " + supplier.getName());
            }
            SupplierDTO savedDTO = new SupplierDTO(supplierRepo.save(supplier.toEntity()));
            auditLogService.logCreate("Supplier", savedDTO.getId(), savedDTO.getName(), savedDTO);
            return savedDTO;
        } catch (ConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new CreationException("Failed to create Supplier. Reason: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public SupplierDTO updateSupplier(Long id, SupplierDTO supplier) {
        try{
            SupplierDTO oldSupplierSnapshot = getSupplierById(id);

            checkForDuplicatesExcludingCurrent(supplier, id);
            supplier.setId(id);
            SupplierDTO savedDTO = new SupplierDTO(supplierRepo.save(supplier.toEntity()));
            auditLogService.logUpdate("Supplier", id, oldSupplierSnapshot.getName(), oldSupplierSnapshot, savedDTO);
            return savedDTO;
        } catch (ResourceNotFoundException | ConflictException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update Supplier, Reason: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void deleteSupplierById(Long id) {
        try{
            SupplierDTO supplierSnapshot = getSupplierById(id);
            supplierRepo.deleteById(id);
            auditLogService.logDelete("Supplier", id, supplierSnapshot.getName(), supplierSnapshot);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DeletionException("Failed to delete Supplier, Reason: " + e.getMessage(), e);
        }
    }

    private boolean supplierAlreadyExists(SupplierDTO supplierDTO) {
        return supplierRepo.findBySupplierName(supplierDTO.getName()).isPresent();
    }

    private void checkForDuplicatesExcludingCurrent(SupplierDTO supplierDTO, Long currentId) {
        Optional<Supplier> duplicate = supplierRepo.findBySupplierName(
                supplierDTO.getName()
        );

        if (duplicate.isPresent() && !duplicate.get().getId().equals(currentId)) {
            throw new ConflictException("Supplier with provided details already exists.");
        }
    }
}

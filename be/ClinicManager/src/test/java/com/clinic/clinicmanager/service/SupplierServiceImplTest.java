package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.SupplierDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Supplier;
import com.clinic.clinicmanager.repo.SupplierRepo;
import com.clinic.clinicmanager.service.impl.SupplierServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SupplierServiceImplTest {

    @Mock
    SupplierRepo supplierRepo;

    @Mock
    AuditLogService auditLogService;

    @InjectMocks
    SupplierServiceImpl supplierService;

    @Test
    void getSupplierById_shouldReturnSupplierDTO_whenSupplierExists() {
        Supplier supplier = Supplier.builder().id(1L).name("Supp").build();
        when(supplierRepo.findById(1L)).thenReturn(Optional.of(supplier));

        SupplierDTO result =  supplierService.getSupplierById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Supp", result.getName());
    }

    @Test
    void getSupplierById_shouldThrowResourceNotFoundException_whenSupplierNotFound() {
        when(supplierRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> supplierService.getSupplierById(99L));
    }

    @Test
    void createSupplier_shouldReturnSupplierDTO_whenSupplierIsNew() {
        SupplierDTO inputDTO = new SupplierDTO(null, "Supp", "Website");
        Supplier savedEntity = Supplier.builder().id(1L).name("Supp").websiteUrl("Website").build();

        when(supplierRepo.findBySupplierName("Supp")).thenReturn(Optional.empty());

        when(supplierRepo.save(any(Supplier.class))).thenReturn(savedEntity);

        SupplierDTO result = supplierService.createSupplier(inputDTO);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Supp", result.getName());
        assertEquals("Website", result.getWebsiteUrl());

        verify(supplierRepo, times(1)).save(any(Supplier.class));
    }

    @Test
    void createSupplier_shouldThrowConflictException_whenSupplierAlreadyExists() {
        SupplierDTO inputDTO = new SupplierDTO(null, "Supp", "Website");
        Supplier existingEntity = Supplier.builder().id(1L).name("Supp").websiteUrl("Website").build();

        when(supplierRepo.findBySupplierName("Supp")).thenReturn(Optional.of(existingEntity));

        assertThrows(ConflictException.class,
                () -> supplierService.createSupplier(inputDTO));

        verify(supplierRepo, never()).save(any());
    }

    @Test
    void updateSupplier_shouldReturnSupplierDTO_whenSupplierExistsAndNoConflict() {
        Supplier existingEntity = Supplier.builder().id(1L).name("Supp").websiteUrl("Website").build();
        Supplier updatedEntity = Supplier.builder().id(1L).name("Supp2").websiteUrl("Website").build();

        SupplierDTO updateDTO = new SupplierDTO(null, "Supp2", "Website");

        when(supplierRepo.findById(1L)).thenReturn(Optional.of(existingEntity));
        when(supplierRepo.findBySupplierName("Supp2")).thenReturn(Optional.empty());
        when(supplierRepo.save(any(Supplier.class))).thenReturn(updatedEntity);

        SupplierDTO result = supplierService.updateSupplier(1L, updateDTO);

        assertEquals(1L, result.getId());
        assertEquals("Supp2", result.getName());
        assertEquals("Website", result.getWebsiteUrl());

        verify(supplierRepo, times(1)).save(any(Supplier.class));
    }

    @Test
    void updateSupplier_shouldThrowResourceNotFoundException_whenSupplierNotFound() {

        when(supplierRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> supplierService.updateSupplier(99L, new SupplierDTO(null, "Supp", null)));

    }

    @Test
    void updateSupplier_shouldThrowConflictException_whenSupplierAlreadyExists() {
        Supplier currentSupplier = Supplier.builder().id(1L).name("Supp").websiteUrl("Website").build();
        SupplierDTO updateDTO = new SupplierDTO(null, "Supp2", "Website");
        Supplier existingEntity = Supplier.builder().id(2L).name("Supp2").websiteUrl("Website2").build();

        when(supplierRepo.findById(1L)).thenReturn(Optional.of(currentSupplier));
        when(supplierRepo.findBySupplierName(updateDTO.getName())).thenReturn(Optional.of(existingEntity));

        assertThrows(ConflictException.class,
                () -> supplierService.updateSupplier(1L, updateDTO));
    }

    @Test
    void deleteSupplierById_shouldDeleteSupplier_whenSupplierExists() {
        Supplier currentSupplier = Supplier.builder().id(1L).name("Supp").websiteUrl("Website").build();

        when(supplierRepo.findById(1L)).thenReturn(Optional.of(currentSupplier));

        supplierService.deleteSupplierById(1L);

        verify(supplierRepo, times(1)).deleteById(1L);
    }

    @Test
    void deleteSupplierById_shouldThrowResourceNotFoundException_whenSupplierNotFound() {
        when(supplierRepo.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> supplierService.deleteSupplierById(99L));

        verify(supplierRepo, never()).deleteById(any());
    }


}

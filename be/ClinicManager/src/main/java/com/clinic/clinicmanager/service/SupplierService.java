package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.SupplierDTO;

import java.util.List;

public interface SupplierService {

    SupplierDTO getSupplierById(Long id);

    List<SupplierDTO> getSuppliers();

    SupplierDTO createSupplier(SupplierDTO supplier);

    SupplierDTO updateSupplier(Long id, SupplierDTO supplier);

    void deleteSupplierById(Long id);

}

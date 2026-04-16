package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.InventoryReportDTO;
import com.clinic.clinicmanager.DTO.request.InventoryReportFilterDTO;
import org.springframework.data.domain.Page;

public interface InventoryReportService {

    InventoryReportDTO getReportById(Long id);

    Page<InventoryReportDTO> getReports(InventoryReportFilterDTO filter, int page, int size);

    InventoryReportDTO createReport(InventoryReportDTO reportDTO);

    void deleteReportById(Long id);

    InventoryReportDTO updateReport(Long id, InventoryReportDTO reportDTO);

    InventoryReportDTO approveReport(Long id);

    boolean areAllApproved();
}

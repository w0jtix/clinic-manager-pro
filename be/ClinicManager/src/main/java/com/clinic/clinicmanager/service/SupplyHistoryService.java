package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.SupplyChartDTO;
import com.clinic.clinicmanager.DTO.SupplyHistoryDTO;
import com.clinic.clinicmanager.DTO.SupplySummaryDTO;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.constants.SupplyChangeSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface SupplyHistoryService {

    void record(Product product, int delta, SupplyChangeSource source, Long sourceId, String sourceLabel);

    Page<SupplyHistoryDTO> getHistory(Long productId, List<SupplyChangeSource> sources, Pageable pageable);

    SupplyChartDTO getChart(Long productId, Integer year);

    SupplySummaryDTO getSummary(Long productId);
}

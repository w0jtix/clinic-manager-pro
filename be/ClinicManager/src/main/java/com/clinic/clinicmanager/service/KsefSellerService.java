package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.CompanyExpenseDTO;
import com.clinic.clinicmanager.DTO.KsefSellerDTO;
import com.clinic.clinicmanager.DTO.KsefSellerItemMappingDTO;
import com.clinic.clinicmanager.DTO.KsefSellerSummaryDTO;
import com.clinic.clinicmanager.DTO.KsefSpendChartDTO;
import com.clinic.clinicmanager.model.KsefSeller;
import org.springframework.data.domain.Page;

import java.util.List;

public interface KsefSellerService {

    KsefSellerDTO getSellerById(Long id);

    KsefSellerDTO getSellerByNip(String nip);

    KsefSellerSummaryDTO getSummary(Long id);

    KsefSpendChartDTO getSpendChart(Long id, Integer year);

    Page<CompanyExpenseDTO> getManualExpenses(Long id, int page, int size);

    List<KsefSellerItemMappingDTO> getItemMappings(Long sellerId);

    void deleteItemMapping(Long sellerId, Long mappingId);

    void removeAutoMark(Long id);

    void setDefaultCategory(Long id, String category);

    void setDefaultCategory(KsefSeller seller, String category);
}

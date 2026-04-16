package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.BaseServiceDTO;
import com.clinic.clinicmanager.DTO.request.KeywordFilterDTO;
import com.clinic.clinicmanager.DTO.request.ServiceFilterDTO;

import java.util.List;

public interface BaseServiceService {

    BaseServiceDTO getBaseServiceById(Long id);

    List<BaseServiceDTO> getBaseServices(ServiceFilterDTO keyword);

    List<BaseServiceDTO> getBaseServicesByCategoryId(Long categoryId);

    BaseServiceDTO createBaseService(BaseServiceDTO service);

    BaseServiceDTO updateBaseService(Long id, BaseServiceDTO serviceDTO);

    void deleteBaseServiceById(Long id);
}

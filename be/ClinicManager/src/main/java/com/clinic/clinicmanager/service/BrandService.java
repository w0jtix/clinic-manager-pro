package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.BrandDTO;
import com.clinic.clinicmanager.DTO.request.KeywordFilterDTO;

import java.util.List;

public interface BrandService {

    BrandDTO getBrandById (Long id);

    List<BrandDTO> getBrands(KeywordFilterDTO filter);

    BrandDTO createBrand(BrandDTO brand);

    List<BrandDTO> createBrands(List<BrandDTO> brands);

    BrandDTO updateBrand(Long id, BrandDTO brand);

    void deleteBrandById(Long id);
}

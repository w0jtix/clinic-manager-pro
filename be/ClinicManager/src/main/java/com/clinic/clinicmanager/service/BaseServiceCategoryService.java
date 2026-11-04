package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.BaseServiceCategoryDTO;
import com.clinic.clinicmanager.DTO.CategoryUsageDTO;

import java.util.List;

public interface BaseServiceCategoryService {

    BaseServiceCategoryDTO getCategoryById(Long id);

    List<BaseServiceCategoryDTO> getCategories();

    BaseServiceCategoryDTO createCategory(BaseServiceCategoryDTO category);

    BaseServiceCategoryDTO updateCategory(Long id, BaseServiceCategoryDTO category);

    void deleteCategoryById(Long id);

    List<CategoryUsageDTO> getCategoryUsage();
}

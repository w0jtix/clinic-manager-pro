package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.CategoryUsageDTO;
import com.clinic.clinicmanager.DTO.ExpenseCategoryDTO;

import java.util.List;

public interface ExpenseCategoryService {

    List<ExpenseCategoryDTO> getCategories();

    List<CategoryUsageDTO> getCategoryUsage();

    ExpenseCategoryDTO createCategory(ExpenseCategoryDTO category);

    ExpenseCategoryDTO updateCategory(Long id, ExpenseCategoryDTO category);

    void deleteCategoryById(Long id);

    void requireExistingCode(String code);
}

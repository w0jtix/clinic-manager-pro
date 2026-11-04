package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.CompanyExpenseDTO;
import com.clinic.clinicmanager.DTO.StockChangePreviewDTO;
import com.clinic.clinicmanager.DTO.request.CompanyExpenseFilterDTO;
import com.clinic.clinicmanager.DTO.request.CompanyExpenseUpdateDTO;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

public interface CompanyExpenseService {

    CompanyExpenseDTO getExpenseById(Long id);

    List<CompanyExpenseDTO> getSalaryHistory(Long employeeId);

    Optional<CompanyExpenseDTO> getLatestExpenseByCategory(String category);

    Page<CompanyExpenseDTO> getExpenses(CompanyExpenseFilterDTO filter, int page, int size);

    CompanyExpenseDTO getExpensePreview(CompanyExpenseDTO expenseDTO);

    CompanyExpenseDTO createExpense(CompanyExpenseDTO expenseDTO);

    CompanyExpenseDTO updateExpense(Long id, CompanyExpenseUpdateDTO updateDTO);

    StockChangePreviewDTO getDeletionPreview(Long id);

    void deleteExpenseById(Long id);
}

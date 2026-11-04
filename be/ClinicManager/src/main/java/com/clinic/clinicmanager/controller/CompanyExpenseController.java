package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.CompanyExpenseDTO;
import com.clinic.clinicmanager.DTO.StockChangePreviewDTO;
import com.clinic.clinicmanager.DTO.request.CompanyExpenseFilterDTO;
import com.clinic.clinicmanager.DTO.request.CompanyExpenseUpdateDTO;
import com.clinic.clinicmanager.service.CompanyExpenseService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/company-expenses")
public class CompanyExpenseController {
    private final CompanyExpenseService companyExpenseService;

    @PostMapping("/search")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<Page<CompanyExpenseDTO>> getExpenses(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestBody CompanyExpenseFilterDTO filter) {
        Page<CompanyExpenseDTO> expensesPage = companyExpenseService.getExpenses(filter, page, size);
        return new ResponseEntity<>(expensesPage, expensesPage.isEmpty() ? HttpStatus.NO_CONTENT : HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<CompanyExpenseDTO> getExpenseById(@PathVariable(value = "id") Long id) {
        CompanyExpenseDTO expense = companyExpenseService.getExpenseById(id);
        return new ResponseEntity<>(expense, HttpStatus.OK);
    }

    @GetMapping("/salary-history/{employeeId}")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<List<CompanyExpenseDTO>> getSalaryHistory(@PathVariable(value = "employeeId") Long employeeId) {
        List<CompanyExpenseDTO> history = companyExpenseService.getSalaryHistory(employeeId);
        return new ResponseEntity<>(history, history.isEmpty() ? HttpStatus.NO_CONTENT : HttpStatus.OK);
    }

    @GetMapping("/latest")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<CompanyExpenseDTO> getLatestExpenseByCategory(@RequestParam String category) {
        return companyExpenseService.getLatestExpenseByCategory(category)
                .map(expense -> new ResponseEntity<>(expense, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NO_CONTENT));
    }

    @PostMapping("/preview")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<CompanyExpenseDTO> getExpensePreview(@NonNull @RequestBody CompanyExpenseDTO expense) {
        CompanyExpenseDTO previewExpense = companyExpenseService.getExpensePreview(expense);
        return new ResponseEntity<>(previewExpense, HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<CompanyExpenseDTO> createExpense(@NonNull @RequestBody CompanyExpenseDTO expense) {
        CompanyExpenseDTO newExpense = companyExpenseService.createExpense(expense);
        return new ResponseEntity<>(newExpense, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<CompanyExpenseDTO> updateExpense(@PathVariable(value = "id") Long id,
                                                           @NonNull @RequestBody CompanyExpenseUpdateDTO expense) {
        CompanyExpenseDTO updatedExpense = companyExpenseService.updateExpense(id, expense);
        return new ResponseEntity<>(updatedExpense, HttpStatus.OK);
    }

    @GetMapping("/{id}/deletion-preview")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<StockChangePreviewDTO> getDeletionPreview(@PathVariable(value = "id") Long id) {
        StockChangePreviewDTO preview = companyExpenseService.getDeletionPreview(id);
        return new ResponseEntity<>(preview, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<Void> deleteExpense(@PathVariable(value = "id") Long id) {
        companyExpenseService.deleteExpenseById(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}

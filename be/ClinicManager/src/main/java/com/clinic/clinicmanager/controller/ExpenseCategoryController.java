package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.CategoryUsageDTO;
import com.clinic.clinicmanager.DTO.ExpenseCategoryDTO;
import com.clinic.clinicmanager.service.ExpenseCategoryService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/expense-categories")
public class ExpenseCategoryController {
    private final ExpenseCategoryService expenseCategoryService;

    @GetMapping
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<List<ExpenseCategoryDTO>> getCategories() {
        return new ResponseEntity<>(expenseCategoryService.getCategories(), HttpStatus.OK);
    }

    @GetMapping("/usage")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<List<CategoryUsageDTO>> getCategoryUsage() {
        return new ResponseEntity<>(expenseCategoryService.getCategoryUsage(), HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<ExpenseCategoryDTO> createCategory(@NonNull @RequestBody ExpenseCategoryDTO category) {
        return new ResponseEntity<>(expenseCategoryService.createCategory(category), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<ExpenseCategoryDTO> updateCategory(@PathVariable(value = "id") Long id,
                                                             @NonNull @RequestBody ExpenseCategoryDTO category) {
        return new ResponseEntity<>(expenseCategoryService.updateCategory(id, category), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('COMPANY')")
    public ResponseEntity<Void> deleteCategory(@PathVariable(value = "id") Long id) {
        expenseCategoryService.deleteCategoryById(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}

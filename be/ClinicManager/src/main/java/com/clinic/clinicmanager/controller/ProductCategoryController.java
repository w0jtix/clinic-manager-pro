package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.CategoryUsageDTO;
import com.clinic.clinicmanager.DTO.ProductCategoryDTO;
import com.clinic.clinicmanager.service.ProductCategoryService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static java.util.Objects.nonNull;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/categories")
public class ProductCategoryController {
    private final ProductCategoryService productCategoryService;

    @GetMapping("/all")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<List<ProductCategoryDTO>> getCategories() {
        List<ProductCategoryDTO> categoryDTOList = productCategoryService.getCategories();
        return new ResponseEntity<>(categoryDTOList, categoryDTOList.isEmpty() ? HttpStatus.NO_CONTENT : HttpStatus.OK);
    }

    @GetMapping("/usage")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<List<CategoryUsageDTO>> getCategoryUsage() {
        return new ResponseEntity<>(productCategoryService.getCategoryUsage(), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<ProductCategoryDTO> getCategoryById(@PathVariable(value = "id") Long id){
        ProductCategoryDTO category = productCategoryService.getCategoryById(id);
        return new ResponseEntity<>(category, HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<ProductCategoryDTO> createCategory(@NonNull @RequestBody ProductCategoryDTO category) {
        ProductCategoryDTO newCategory = productCategoryService.createCategory(category);
        return new ResponseEntity<>(newCategory, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<ProductCategoryDTO> updateCategory(@PathVariable(value = "id") Long id, @NonNull @RequestBody ProductCategoryDTO category){
        ProductCategoryDTO saved = productCategoryService.updateCategory(id, category);
        return new ResponseEntity<>(saved, HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<Void> deleteCategory(@PathVariable(value = "id") Long id) {
        productCategoryService.deleteCategoryById(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}

package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.CompanyExpenseDTO;
import com.clinic.clinicmanager.DTO.KsefSellerDTO;
import com.clinic.clinicmanager.DTO.KsefSellerItemMappingDTO;
import com.clinic.clinicmanager.DTO.KsefSellerSummaryDTO;
import com.clinic.clinicmanager.DTO.KsefSpendChartDTO;
import com.clinic.clinicmanager.DTO.request.KsefSellerDefaultCategoryDTO;
import com.clinic.clinicmanager.service.KsefSellerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ksef/sellers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COMPANY')")
public class KsefSellerController {

    private final KsefSellerService sellerService;

    @GetMapping("/{id}")
    public ResponseEntity<KsefSellerDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(sellerService.getSellerById(id));
    }

    @GetMapping("/by-nip/{nip}")
    public ResponseEntity<KsefSellerDTO> getByNip(@PathVariable String nip) {
        return ResponseEntity.ok(sellerService.getSellerByNip(nip));
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<KsefSellerSummaryDTO> getSummary(@PathVariable Long id) {
        return ResponseEntity.ok(sellerService.getSummary(id));
    }

    @GetMapping("/{id}/spend-chart")
    public ResponseEntity<KsefSpendChartDTO> getSpendChart(
            @PathVariable Long id,
            @RequestParam(required = false) Integer year) {
        return ResponseEntity.ok(sellerService.getSpendChart(id, year));
    }

    @GetMapping("/{id}/expenses")
    public ResponseEntity<Page<CompanyExpenseDTO>> getManualExpenses(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(sellerService.getManualExpenses(id, page, size));
    }

    @GetMapping("/{id}/item-mappings")
    public ResponseEntity<List<KsefSellerItemMappingDTO>> getItemMappings(@PathVariable Long id) {
        return ResponseEntity.ok(sellerService.getItemMappings(id));
    }

    @DeleteMapping("/{sellerId}/item-mappings/{mappingId}")
    public ResponseEntity<Void> deleteItemMapping(
            @PathVariable Long sellerId,
            @PathVariable Long mappingId) {
        sellerService.deleteItemMapping(sellerId, mappingId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/default-category")
    public ResponseEntity<Void> setDefaultCategory(
            @PathVariable Long id,
            @RequestBody(required = false) KsefSellerDefaultCategoryDTO request) {
        sellerService.setDefaultCategory(id, request != null ? request.getCategory() : null);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/remove-auto-mark")
    public ResponseEntity<Void> removeAutoMark(@PathVariable Long id) {
        sellerService.removeAutoMark(id);
        return ResponseEntity.ok().build();
    }
}

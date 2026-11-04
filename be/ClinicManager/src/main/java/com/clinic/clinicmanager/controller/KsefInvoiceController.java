package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.KsefInvoiceCountDTO;
import com.clinic.clinicmanager.DTO.KsefInvoiceDTO;
import com.clinic.clinicmanager.DTO.KsefSyncStatusDTO;
import com.clinic.clinicmanager.DTO.StockChangePreviewDTO;
import com.clinic.clinicmanager.DTO.request.KsefBookCorrectionRequestDTO;
import com.clinic.clinicmanager.DTO.request.KsefInvoiceFilterDTO;
import com.clinic.clinicmanager.DTO.request.KsefVerifyProductsRequestDTO;
import com.clinic.clinicmanager.service.KsefInvoiceService;
import com.clinic.clinicmanager.service.KsefInvoiceSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/ksef/invoices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COMPANY')")
public class KsefInvoiceController {

    private final KsefInvoiceService invoiceService;
    private final KsefInvoiceSyncService syncService;

    @PostMapping("/sync")
    public ResponseEntity<Void> sync() {
        syncService.syncInvoices();
        return ResponseEntity.ok().build();
    }

    @PostMapping("/sync-issued-month")
    public ResponseEntity<Void> syncIssuedMonth(@RequestParam int year, @RequestParam int month) {
        syncService.syncIssuedInvoicesForMonth(YearMonth.of(year, month));
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/sync-status")
    public ResponseEntity<KsefSyncStatusDTO> syncStatus() {
        return ResponseEntity.ok(syncService.getSyncStatus());
    }

    @PostMapping("/count")
    public ResponseEntity<KsefInvoiceCountDTO> count(@RequestBody KsefInvoiceFilterDTO filter) {
        return ResponseEntity.ok(syncService.getInvoiceCount(filter));
    }

    @PostMapping("/search")
    public ResponseEntity<Page<KsefInvoiceDTO>> search(
            @RequestBody KsefInvoiceFilterDTO filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(invoiceService.getInvoices(filter, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KsefInvoiceDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.getInvoiceById(id));
    }

    @PutMapping("/{id}/toggle-paid")
    public ResponseEntity<KsefInvoiceDTO> togglePaid(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.togglePaidStatus(id));
    }

    @PutMapping("/{id}/toggle-product-invoice")
    public ResponseEntity<KsefInvoiceDTO> toggleProductInvoice(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.toggleProductInvoice(id));
    }

    @PutMapping("/{id}/category")
    public ResponseEntity<KsefInvoiceDTO> setCategory(
            @PathVariable Long id,
            @RequestParam String category,
            @RequestParam(defaultValue = "false") boolean rememberForSeller) {
        return ResponseEntity.ok(invoiceService.setCategory(id, category, rememberForSeller));
    }

    @PutMapping("/{id}/verify-products")
    public ResponseEntity<KsefInvoiceDTO> verifyProducts(
            @PathVariable Long id,
            @RequestBody KsefVerifyProductsRequestDTO request) {
        return ResponseEntity.ok(invoiceService.verifyProducts(id, request));
    }

    @PostMapping("/{id}/book-correction/preview")
    public ResponseEntity<StockChangePreviewDTO> previewBookCorrection(
            @PathVariable Long id,
            @RequestBody KsefBookCorrectionRequestDTO request) {
        return ResponseEntity.ok(invoiceService.previewCorrectionBooking(id, request));
    }

    @PostMapping("/{id}/book-correction")
    public ResponseEntity<KsefInvoiceDTO> bookCorrection(
            @PathVariable Long id,
            @RequestBody KsefBookCorrectionRequestDTO request) {
        return ResponseEntity.ok(invoiceService.bookCorrection(id, request));
    }

}

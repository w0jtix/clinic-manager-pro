package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.KsefIssuedInvoiceCountDTO;
import com.clinic.clinicmanager.DTO.KsefIssuedInvoiceDTO;
import com.clinic.clinicmanager.DTO.request.KsefIssuedInvoiceFilterDTO;
import com.clinic.clinicmanager.service.KsefIssuedInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ksef/issued-invoices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COMPANY')")
public class KsefIssuedInvoiceController {

    private final KsefIssuedInvoiceService issuedInvoiceService;

    @PostMapping("/search")
    public ResponseEntity<Page<KsefIssuedInvoiceDTO>> search(
            @RequestBody KsefIssuedInvoiceFilterDTO filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(issuedInvoiceService.getInvoices(filter, page, size));
    }

    @PostMapping("/count")
    public ResponseEntity<KsefIssuedInvoiceCountDTO> count(@RequestBody KsefIssuedInvoiceFilterDTO filter) {
        return ResponseEntity.ok(issuedInvoiceService.getInvoiceCount(filter));
    }

    @GetMapping("/{id}")
    public ResponseEntity<KsefIssuedInvoiceDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(issuedInvoiceService.getInvoiceById(id));
    }
}

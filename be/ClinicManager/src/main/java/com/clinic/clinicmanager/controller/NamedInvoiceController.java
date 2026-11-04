package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.NamedInvoiceDTO;
import com.clinic.clinicmanager.DTO.NamedInvoiceDraftDTO;
import com.clinic.clinicmanager.DTO.request.NamedInvoiceCreateDTO;
import com.clinic.clinicmanager.DTO.request.SendNamedInvoiceRequest;
import com.clinic.clinicmanager.service.NamedInvoiceEmailService;
import com.clinic.clinicmanager.service.NamedInvoicePdfService;
import com.clinic.clinicmanager.service.NamedInvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/named-invoices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MASTER')")
public class NamedInvoiceController {

    private final NamedInvoiceService namedInvoiceService;
    private final NamedInvoicePdfService namedInvoicePdfService;
    private final NamedInvoiceEmailService namedInvoiceEmailService;

    @GetMapping("/draft/{visitId}")
    public ResponseEntity<NamedInvoiceDraftDTO> draft(@PathVariable Long visitId) {
        return ResponseEntity.ok(namedInvoiceService.buildDraft(visitId));
    }

    @PostMapping
    public ResponseEntity<NamedInvoiceDTO> create(@RequestBody NamedInvoiceCreateDTO request) {
        return new ResponseEntity<>(namedInvoiceService.create(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<NamedInvoiceDTO> update(@PathVariable Long id,
                                                  @RequestBody NamedInvoiceCreateDTO request) {
        return ResponseEntity.ok(namedInvoiceService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        namedInvoiceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<NamedInvoiceDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(namedInvoiceService.getById(id));
    }

    @GetMapping("/by-client/{clientId}")
    public ResponseEntity<Page<NamedInvoiceDTO>> byClient(
            @PathVariable Long clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(namedInvoiceService.getByClient(clientId, page, size));
    }

    @GetMapping("/by-period")
    public ResponseEntity<List<NamedInvoiceDTO>> byPeriod(@RequestParam int year, @RequestParam int month) {
        return ResponseEntity.ok(namedInvoiceService.getByPeriod(year, month));
    }

    @PostMapping("/{id}/send")
    public ResponseEntity<NamedInvoiceDTO> send(@PathVariable Long id,
                                                @RequestBody SendNamedInvoiceRequest request) {
        return ResponseEntity.ok(namedInvoiceEmailService.send(id, request));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        byte[] content = namedInvoicePdfService.generateInvoicePdf(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("faktura-" + id + ".pdf", StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(content.length);

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }
}

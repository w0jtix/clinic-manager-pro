package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.ClientDrawingDTO;
import com.clinic.clinicmanager.DTO.DrawingMarkerDTO;
import com.clinic.clinicmanager.DTO.request.DrawingLayerUpdateRequest;
import com.clinic.clinicmanager.DTO.request.DrawingSheetCreateRequest;
import com.clinic.clinicmanager.service.ClientDrawingService;
import com.clinic.clinicmanager.service.DrawingMarkerService;
import com.clinic.clinicmanager.service.DrawingTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cards")
public class ClientDrawingController {

    private static final Duration TEMPLATE_CACHE_DURATION = Duration.ofDays(365);

    private final ClientDrawingService clientDrawingService;
    private final DrawingTemplateService drawingTemplateService;
    private final DrawingMarkerService drawingMarkerService;

    @GetMapping("/{clientId}/drawing")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<ClientDrawingDTO> getDrawing(@PathVariable(value = "clientId") Long clientId) {
        return new ResponseEntity<>(clientDrawingService.getDrawing(clientId), HttpStatus.OK);
    }

    @GetMapping("/{clientId}/drawing/sheets/{sheetId}/layers/{layerId}/items")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<byte[]> getLayerItems(
            @PathVariable(value = "clientId") Long clientId,
            @PathVariable(value = "sheetId") Long sheetId,
            @PathVariable(value = "layerId") Long layerId) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore())
                .body(clientDrawingService.getLayerItems(clientId, sheetId, layerId));
    }

    @PostMapping("/{clientId}/drawing/sheets/{sheetId}/view")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<Void> logSheetView(
            @PathVariable(value = "clientId") Long clientId,
            @PathVariable(value = "sheetId") Long sheetId) {
        clientDrawingService.logSheetView(clientId, sheetId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/{clientId}/drawing/sheets")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<ClientDrawingDTO> createSheet(
            @PathVariable(value = "clientId") Long clientId,
            @RequestBody DrawingSheetCreateRequest request) {
        return new ResponseEntity<>(clientDrawingService.createSheet(
                clientId, request.getTemplateId(), request.getItems(), request.getCloseSheetId()), HttpStatus.OK);
    }

    @PutMapping("/{clientId}/drawing/sheets/{sheetId}/layers/{authorUserId}")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<ClientDrawingDTO> updateLayerItems(
            @PathVariable(value = "clientId") Long clientId,
            @PathVariable(value = "sheetId") Long sheetId,
            @PathVariable(value = "authorUserId") Long authorUserId,
            @RequestBody DrawingLayerUpdateRequest request) {
        return new ResponseEntity<>(clientDrawingService.updateLayerItems(
                clientId, sheetId, authorUserId, request.getItems(), request.getExpectedVersion()), HttpStatus.OK);
    }

    @GetMapping("/drawing-markers")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<List<DrawingMarkerDTO>> getDrawingMarkers() {
        return new ResponseEntity<>(drawingMarkerService.getMarkers(), HttpStatus.OK);
    }

    @GetMapping("/drawing-template-images/{imageId}/image")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<byte[]> getTemplateImage(@PathVariable(value = "imageId") Long imageId) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(TEMPLATE_CACHE_DURATION).cachePrivate().immutable())
                .body(drawingTemplateService.getTemplateImage(imageId));
    }
}

package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.DrawingMarkerDTO;
import com.clinic.clinicmanager.DTO.DrawingTemplateSettingsDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireSettingsDTO;
import com.clinic.clinicmanager.DTO.QuestionnaireSettingsUpdateDTO;
import com.clinic.clinicmanager.DTO.request.DrawingMarkerOrderDTO;
import com.clinic.clinicmanager.DTO.request.DrawingMarkerRequest;
import com.clinic.clinicmanager.DTO.request.DrawingTemplateOrderDTO;
import com.clinic.clinicmanager.DTO.response.DrawingTemplateDeleteResponse;
import com.clinic.clinicmanager.service.DrawingMarkerService;
import com.clinic.clinicmanager.service.DrawingTemplateService;
import com.clinic.clinicmanager.service.QuestionnaireFieldService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/card-settings")
public class CardSettingsController {

    private final QuestionnaireFieldService questionnaireFieldService;
    private final DrawingTemplateService drawingTemplateService;
    private final DrawingMarkerService drawingMarkerService;

    @GetMapping("/questionnaire")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<QuestionnaireSettingsDTO> getQuestionnaireSettings() {
        return new ResponseEntity<>(questionnaireFieldService.getSettings(), HttpStatus.OK);
    }

    @PutMapping("/questionnaire")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<QuestionnaireSettingsDTO> updateQuestionnaireSettings(
            @RequestBody QuestionnaireSettingsUpdateDTO settings) {
        return new ResponseEntity<>(questionnaireFieldService.updateSettings(settings), HttpStatus.OK);
    }

    @GetMapping("/drawing-templates")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<List<DrawingTemplateSettingsDTO>> getDrawingTemplates() {
        return new ResponseEntity<>(drawingTemplateService.getTemplates(), HttpStatus.OK);
    }

    @PostMapping(value = "/drawing-templates", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<DrawingTemplateSettingsDTO> createDrawingTemplate(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return new ResponseEntity<>(drawingTemplateService.createTemplate(name, file), HttpStatus.OK);
    }

    @PutMapping(value = "/drawing-templates/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<DrawingTemplateSettingsDTO> updateDrawingTemplate(
            @PathVariable(value = "id") Long id,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "removeImage", required = false) Boolean removeImage) {
        return new ResponseEntity<>(drawingTemplateService.updateTemplate(
                id, name, file, Boolean.TRUE.equals(removeImage)), HttpStatus.OK);
    }

    @DeleteMapping("/drawing-templates/{id}")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<DrawingTemplateDeleteResponse> deleteDrawingTemplate(@PathVariable(value = "id") Long id) {
        return new ResponseEntity<>(new DrawingTemplateDeleteResponse(drawingTemplateService.deleteTemplate(id)),
                HttpStatus.OK);
    }

    @PutMapping("/drawing-templates/{id}/restore")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<DrawingTemplateSettingsDTO> restoreDrawingTemplate(@PathVariable(value = "id") Long id) {
        return new ResponseEntity<>(drawingTemplateService.restoreTemplate(id), HttpStatus.OK);
    }

    @PutMapping("/drawing-templates/order")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<List<DrawingTemplateSettingsDTO>> reorderDrawingTemplates(
            @RequestBody DrawingTemplateOrderDTO order) {
        return new ResponseEntity<>(drawingTemplateService.reorderTemplates(order.getIds()), HttpStatus.OK);
    }

    @GetMapping("/drawing-markers")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<List<DrawingMarkerDTO>> getDrawingMarkers() {
        return new ResponseEntity<>(drawingMarkerService.getMarkers(), HttpStatus.OK);
    }

    @PostMapping("/drawing-markers")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<DrawingMarkerDTO> createDrawingMarker(@RequestBody DrawingMarkerRequest request) {
        return new ResponseEntity<>(drawingMarkerService.createMarker(request), HttpStatus.OK);
    }

    @PutMapping("/drawing-markers/{id}")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<DrawingMarkerDTO> updateDrawingMarker(
            @PathVariable(value = "id") Long id,
            @RequestBody DrawingMarkerRequest request) {
        return new ResponseEntity<>(drawingMarkerService.updateMarker(id, request), HttpStatus.OK);
    }

    @DeleteMapping("/drawing-markers/{id}")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<Void> deleteDrawingMarker(@PathVariable(value = "id") Long id) {
        drawingMarkerService.deleteMarker(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PutMapping("/drawing-markers/order")
    @PreAuthorize(("hasRole('COMPANY')"))
    public ResponseEntity<List<DrawingMarkerDTO>> reorderDrawingMarkers(@RequestBody DrawingMarkerOrderDTO order) {
        return new ResponseEntity<>(drawingMarkerService.reorderMarkers(order.getIds()), HttpStatus.OK);
    }
}

package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.AccessControlDTO;
import com.clinic.clinicmanager.DTO.request.accesscontrol.GateRequest;
import com.clinic.clinicmanager.DTO.request.accesscontrol.PauseRequest;
import com.clinic.clinicmanager.DTO.request.accesscontrol.WorkingHoursUpdateRequest;
import com.clinic.clinicmanager.service.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/access-control")
@PreAuthorize("hasRole('ADMIN')")
public class AccessControlController {

    private final AccessControlService accessControlService;

    @GetMapping
    public ResponseEntity<AccessControlDTO> getSettings() {
        return new ResponseEntity<>(accessControlService.getSettings(), HttpStatus.OK);
    }

    @PutMapping("/gate")
    public ResponseEntity<AccessControlDTO> setGate(@RequestBody GateRequest request) {
        return new ResponseEntity<>(accessControlService.setGateEnabled(request), HttpStatus.OK);
    }

    @PostMapping("/pause")
    public ResponseEntity<AccessControlDTO> pause(@RequestBody PauseRequest request) {
        return new ResponseEntity<>(accessControlService.pause(request), HttpStatus.OK);
    }

    @DeleteMapping("/pause")
    public ResponseEntity<AccessControlDTO> resume() {
        return new ResponseEntity<>(accessControlService.resume(), HttpStatus.OK);
    }

    @PutMapping("/hours")
    public ResponseEntity<AccessControlDTO> updateHours(@RequestBody WorkingHoursUpdateRequest request) {
        return new ResponseEntity<>(accessControlService.updateHours(request), HttpStatus.OK);
    }
}

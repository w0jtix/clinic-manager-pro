package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.KsefCredentialsDTO;
import com.clinic.clinicmanager.service.KsefCredentialsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ksef/credentials")
public class KsefCredentialsController {

    private final KsefCredentialsService credentialsService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KsefCredentialsDTO> getCredentials() {
        return new ResponseEntity<>(credentialsService.getCredentials(), HttpStatus.OK);
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KsefCredentialsDTO> saveCredentials(@RequestBody KsefCredentialsDTO dto) {
        return new ResponseEntity<>(credentialsService.saveCredentials(dto), HttpStatus.OK);
    }
}

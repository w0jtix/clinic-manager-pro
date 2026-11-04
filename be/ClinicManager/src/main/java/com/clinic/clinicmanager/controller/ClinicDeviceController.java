package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.ClinicDeviceDTO;
import com.clinic.clinicmanager.DTO.RegistrationCodeDTO;
import com.clinic.clinicmanager.DTO.request.device.CreateRegistrationCodeRequest;
import com.clinic.clinicmanager.DTO.request.device.RenameDeviceRequest;
import com.clinic.clinicmanager.service.ClinicDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/devices")
@PreAuthorize("hasRole('COMPANY')")
public class ClinicDeviceController {

    private final ClinicDeviceService clinicDeviceService;

    @GetMapping
    public ResponseEntity<List<ClinicDeviceDTO>> getDevices(
            @RequestParam(value = "includeRevoked", defaultValue = "false") boolean includeRevoked) {
        return new ResponseEntity<>(clinicDeviceService.getDevices(includeRevoked), HttpStatus.OK);
    }

    @GetMapping("/codes")
    public ResponseEntity<List<RegistrationCodeDTO>> getActiveCodes() {
        return new ResponseEntity<>(clinicDeviceService.getActiveCodes(), HttpStatus.OK);
    }

    @PostMapping("/codes")
    public ResponseEntity<RegistrationCodeDTO> createCode(@RequestBody CreateRegistrationCodeRequest request) {
        return new ResponseEntity<>(clinicDeviceService.createCode(request), HttpStatus.CREATED);
    }

    @DeleteMapping("/codes/{id}")
    public ResponseEntity<Void> cancelCode(@PathVariable(value = "id") Long id) {
        clinicDeviceService.cancelCode(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClinicDeviceDTO> renameDevice(@PathVariable(value = "id") Long id,
                                                        @RequestBody RenameDeviceRequest request) {
        return new ResponseEntity<>(clinicDeviceService.renameDevice(id, request.getName()), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeDevice(@PathVariable(value = "id") Long id) {
        clinicDeviceService.revokeDevice(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @DeleteMapping("/{id}/permanent")
    public ResponseEntity<Void> deleteDevice(@PathVariable(value = "id") Long id) {
        clinicDeviceService.deleteDevice(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}

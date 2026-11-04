package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.request.auth.MfaCodeRequest;
import com.clinic.clinicmanager.DTO.response.MessageResponse;
import com.clinic.clinicmanager.DTO.response.MfaBackupCodesResponse;
import com.clinic.clinicmanager.DTO.response.MfaSetupResponse;
import com.clinic.clinicmanager.DTO.response.MfaStatusResponse;
import com.clinic.clinicmanager.config.security.MfaDeviceCookie;
import com.clinic.clinicmanager.service.MfaService;
import com.clinic.clinicmanager.utils.SessionUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user/me/mfa")
@PreAuthorize("hasRole('COMPANY')")
public class MfaController {

    private final MfaService mfaService;
    private final MfaDeviceCookie mfaDeviceCookie;

    @GetMapping
    public ResponseEntity<MfaStatusResponse> getStatus(HttpServletRequest request) {
        String deviceToken = mfaDeviceCookie.read(request, SessionUtils.getUserIdFromSession());
        return new ResponseEntity<>(mfaService.getStatus(deviceToken), HttpStatus.OK);
    }

    @PostMapping("/setup")
    public ResponseEntity<MfaSetupResponse> setup() {
        return new ResponseEntity<>(mfaService.setup(), HttpStatus.OK);
    }

    @PostMapping("/enable")
    public ResponseEntity<MfaBackupCodesResponse> enable(@Valid @RequestBody MfaCodeRequest codeRequest) {
        return new ResponseEntity<>(mfaService.enable(codeRequest.getCode()), HttpStatus.OK);
    }

    @PostMapping("/disable")
    public ResponseEntity<MessageResponse> disable(@Valid @RequestBody MfaCodeRequest codeRequest) {
        mfaService.disable(codeRequest.getCode());
        return new ResponseEntity<>(new MessageResponse("Weryfikacja dwuetapowa została wyłączona."), HttpStatus.OK);
    }

    @PostMapping("/backup-codes")
    public ResponseEntity<MfaBackupCodesResponse> regenerateBackupCodes(@Valid @RequestBody MfaCodeRequest codeRequest) {
        return new ResponseEntity<>(mfaService.regenerateBackupCodes(codeRequest.getCode()), HttpStatus.OK);
    }

    @PostMapping("/backup-codes/confirm")
    public ResponseEntity<MessageResponse> confirmBackupCodes() {
        mfaService.confirmBackupCodes();
        return new ResponseEntity<>(new MessageResponse("Zapisano nowe kody zapasowe."), HttpStatus.OK);
    }

    @DeleteMapping("/trusted-devices/{id}")
    public ResponseEntity<Void> revokeTrustedDevice(@PathVariable(value = "id") Long id) {
        mfaService.revokeTrustedDevice(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @DeleteMapping("/trusted-devices")
    public ResponseEntity<Void> revokeAllTrustedDevices() {
        mfaService.revokeAllTrustedDevices();
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}

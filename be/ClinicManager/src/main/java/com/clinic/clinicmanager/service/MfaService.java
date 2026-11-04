package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.TrustedDeviceDTO;
import com.clinic.clinicmanager.DTO.response.MfaBackupCodesResponse;
import com.clinic.clinicmanager.DTO.response.MfaSetupResponse;
import com.clinic.clinicmanager.DTO.response.MfaStatusResponse;

import java.util.List;

public interface MfaService {

    MfaStatusResponse getStatus(String currentDeviceToken);

    MfaSetupResponse setup();

    MfaBackupCodesResponse enable(String code);

    void disable(String code);

    MfaBackupCodesResponse regenerateBackupCodes(String code);

    void confirmBackupCodes();

    void revokeTrustedDevice(Long deviceId);

    void revokeAllTrustedDevices();

    String resetByAdmin(Long userId);

    List<TrustedDeviceDTO> getTrustedDevicesOfUser(Long userId);

    void purgeMfaArtifacts(Long userId);

}

package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ClinicDeviceDTO;
import com.clinic.clinicmanager.DTO.ClinicDeviceLoginResult;
import com.clinic.clinicmanager.DTO.ClinicDeviceRegistration;
import com.clinic.clinicmanager.DTO.LoginDeviceCheck;
import com.clinic.clinicmanager.DTO.RegistrationCodeDTO;
import com.clinic.clinicmanager.DTO.request.device.CreateRegistrationCodeRequest;

import java.util.List;

public interface ClinicDeviceService {

    List<ClinicDeviceDTO> getDevices(boolean includeRevoked);

    List<RegistrationCodeDTO> getActiveCodes();

    RegistrationCodeDTO createCode(CreateRegistrationCodeRequest request);

    void cancelCode(Long codeId);

    ClinicDeviceDTO renameDevice(Long deviceId, String name);

    void revokeDevice(Long deviceId);

    void deleteDevice(Long deviceId);

    ClinicDeviceRegistration register(String code, String currentRawToken, String userAgent, String ip);

    LoginDeviceCheck inspectForLogin(String rawToken, String ip, String userAgent);

    ClinicDeviceLoginResult recognizeOnLogin(String rawToken, String username, String ip, String userAgent);

    void recordLoginReuse(Long deviceId, String username, String ip);

}

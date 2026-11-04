package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.response.JwtResponse;

public interface CompanyModeService {

    JwtResponse elevate(String pin, String currentToken);

    JwtResponse dropElevation(String currentToken);

    String encodeCompanyPin(String pin);

    void changeOwnPin(String currentPin, String newPin);

    void setPinByAdmin(Long userId, String newPin);

}

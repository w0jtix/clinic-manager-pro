package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.KsefCredentialsDTO;

public interface KsefCredentialsService {

    KsefCredentialsDTO getCredentials();

    KsefCredentialsDTO saveCredentials(KsefCredentialsDTO dto);
}

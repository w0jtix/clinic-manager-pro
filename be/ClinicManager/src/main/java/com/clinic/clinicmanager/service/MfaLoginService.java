package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.MfaLoginResult;

public interface MfaLoginService {

    boolean isMfaEnabled(Long userId);

    String startLoginChallenge(Long userId);

    MfaLoginResult verifyLoginCode(String mfaToken, String code);

    MfaLoginResult verifyLoginBackupCode(String mfaToken, String code);

}

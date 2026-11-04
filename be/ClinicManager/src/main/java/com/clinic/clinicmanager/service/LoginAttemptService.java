package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.model.constants.LoginAttemptScope;

public interface LoginAttemptService {

    void recordFailure(String ip, String username, LoginAttemptScope scope);

    void recordSuccess(String ip, String username, LoginAttemptScope scope);

    boolean isBlocked(String ip, String username, LoginAttemptScope scope);

    boolean isShadowBanned(String ip, String username, LoginAttemptScope scope);

}

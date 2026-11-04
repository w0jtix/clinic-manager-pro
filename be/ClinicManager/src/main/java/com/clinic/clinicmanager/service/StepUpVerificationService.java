package com.clinic.clinicmanager.service;

public interface StepUpVerificationService {

    void verify(Long userId, String code, String password);

}

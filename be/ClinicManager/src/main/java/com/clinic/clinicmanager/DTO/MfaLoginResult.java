package com.clinic.clinicmanager.DTO;

public record MfaLoginResult(Long userId, String username, Integer remainingBackupCodes) {
}

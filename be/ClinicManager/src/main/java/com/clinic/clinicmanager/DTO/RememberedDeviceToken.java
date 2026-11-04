package com.clinic.clinicmanager.DTO;

import java.time.LocalDateTime;

public record RememberedDeviceToken(String token, LocalDateTime expiresAt) {
}

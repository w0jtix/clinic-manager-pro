package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.RememberedDeviceToken;
import com.clinic.clinicmanager.DTO.TrustedDeviceDTO;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TrustedDeviceService {

    Optional<RememberedDeviceToken> rotateRemembered(Long userId, String username, String rawToken, String userAgent, String ip);

    RememberedDeviceToken rememberDevice(Long userId, String userAgent, String ip);

    List<TrustedDeviceDTO> getActiveRemembered(Long userId, String currentRawToken);

    String revokeRemembered(Long userId, Long deviceId);

    int revokeAllRemembered(Long userId);

    int countActiveRemembered(Long userId);

    Map<Long, Integer> countActiveRememberedByUser();

}

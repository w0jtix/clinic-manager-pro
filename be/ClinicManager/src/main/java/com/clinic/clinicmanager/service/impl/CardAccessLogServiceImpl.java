package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.model.CardAccessLog;
import com.clinic.clinicmanager.model.constants.CardAccessAction;
import com.clinic.clinicmanager.repo.CardAccessLogRepo;
import com.clinic.clinicmanager.service.CardAccessLogService;
import com.clinic.clinicmanager.utils.RequestContextUtils;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CardAccessLogServiceImpl implements CardAccessLogService {

    private static final int IP_ADDRESS_MAX_LENGTH = 64;

    private final CardAccessLogRepo cardAccessLogRepo;
    private final ClinicDeviceResolver clinicDeviceResolver;

    @Override
    @Transactional
    public void log(CardAccessAction action, Long clientId, String resourceType, Long resourceId) {
        cardAccessLogRepo.save(CardAccessLog.builder()
                .at(LocalDateTime.now())
                .userId(SessionUtils.getUserIdFromSession())
                .ipAddress(truncate(RequestContextUtils.getClientIpAddress()))
                .action(action)
                .clientId(clientId)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .deviceName(clinicDeviceResolver.resolveCurrentName().orElse(null))
                .build());
    }

    private static String truncate(String ipAddress) {
        if (ipAddress == null || ipAddress.length() <= IP_ADDRESS_MAX_LENGTH) {
            return ipAddress;
        }
        return ipAddress.substring(0, IP_ADDRESS_MAX_LENGTH);
    }
}

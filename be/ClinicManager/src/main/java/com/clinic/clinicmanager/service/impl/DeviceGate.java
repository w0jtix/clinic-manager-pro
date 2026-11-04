package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.service.AccessControlService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeviceGate {

    private final AccessControlService accessControlService;
    private final ClinicDeviceResolver clinicDeviceResolver;

    public boolean deniesRequest(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl principal)) {
            return false;
        }
        if (!AppRoleHierarchy.isGatedAccount(principal.getAuthorities())) {
            return false;
        }
        if (!accessControlService.isGateActive()) {
            return false;
        }
        return !clinicDeviceResolver.isAllowed(request);
    }
}

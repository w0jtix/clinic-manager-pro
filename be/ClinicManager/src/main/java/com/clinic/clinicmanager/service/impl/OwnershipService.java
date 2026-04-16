package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class OwnershipService {

    public void checkOwnershipOrAdmin(Long createdByUserId) {
        if (SessionUtils.hasUserRole(RoleType.ROLE_ADMIN)) return;
        Long currentUserId = SessionUtils.getUserIdFromSession();
        if (currentUserId != null && Objects.equals(currentUserId, createdByUserId)) return;
        throw new AccessDeniedException("Access denied: you can only modify your own resources.");
    }
}

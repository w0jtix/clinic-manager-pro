package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.service.impl.OwnershipService;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OwnershipServiceTest {

    @InjectMocks
    OwnershipService ownershipService;

    @Test
    void checkOwnershipOrAdmin_shouldNotThrow_whenUserIsAdmin() {
        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasUserRole(RoleType.ROLE_ADMIN)).thenReturn(true);

            assertDoesNotThrow(() -> ownershipService.checkOwnershipOrAdmin(99L));
        }
    }

    @Test
    void checkOwnershipOrAdmin_shouldNotThrow_whenUserIsOwner() {
        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasUserRole(RoleType.ROLE_ADMIN)).thenReturn(false);
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            assertDoesNotThrow(() -> ownershipService.checkOwnershipOrAdmin(1L));
        }
    }

    @Test
    void checkOwnershipOrAdmin_shouldThrowAccessDeniedException_whenCreatedByUserIdIsNullAndNotAdmin() {
        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasUserRole(RoleType.ROLE_ADMIN)).thenReturn(false);
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            assertThrows(AccessDeniedException.class, () -> ownershipService.checkOwnershipOrAdmin(null));
        }
    }

    @Test
    void checkOwnershipOrAdmin_shouldNotThrow_whenCreatedByUserIdIsNullAndUserIsAdmin() {
        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasUserRole(RoleType.ROLE_ADMIN)).thenReturn(true);

            assertDoesNotThrow(() -> ownershipService.checkOwnershipOrAdmin(null));
        }
    }

    @Test
    void checkOwnershipOrAdmin_shouldThrowAccessDeniedException_whenBothUserIdAndCreatedByAreNull() {
        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasUserRole(RoleType.ROLE_ADMIN)).thenReturn(false);
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(null);

            assertThrows(AccessDeniedException.class, () -> ownershipService.checkOwnershipOrAdmin(null));
        }
    }

    @Test
    void checkOwnershipOrAdmin_shouldThrowAccessDeniedException_whenNotOwnerAndNotAdmin() {
        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasUserRole(RoleType.ROLE_ADMIN)).thenReturn(false);
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            assertThrows(AccessDeniedException.class, () -> ownershipService.checkOwnershipOrAdmin(2L));
        }
    }
}

package com.clinic.clinicmanager.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.AuditLogDTO;
import com.clinic.clinicmanager.DTO.request.AuditLogFilterDTO;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.exceptions.AuditException;
import com.clinic.clinicmanager.model.AuditLog;
import com.clinic.clinicmanager.model.constants.AuditAction;
import com.clinic.clinicmanager.repo.AuditLogRepo;
import com.clinic.clinicmanager.service.impl.AuditLogServiceImpl;
import com.clinic.clinicmanager.utils.RequestContextUtils;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceImplTest {

    @Mock
    AuditLogRepo auditLogRepo;

    AuditLogServiceImpl auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogServiceImpl(auditLogRepo, new ObjectMapper());
    }

    private void mockRequestContext(MockedStatic<RequestContextUtils> mock) {
        mock.when(RequestContextUtils::getClientIpAddress).thenReturn("127.0.0.1");
        mock.when(RequestContextUtils::getSessionId).thenReturn("session-1");
        mock.when(RequestContextUtils::getDeviceType).thenReturn("Desktop");
        mock.when(RequestContextUtils::getBrowserName).thenReturn("Chrome");
    }

    @Test
    void logCreate_shouldSaveAuditLog_withLoggedInUser() {
        UserDetailsImpl user = mock(UserDetailsImpl.class);
        when(user.getUsername()).thenReturn("admin");

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class);
             MockedStatic<RequestContextUtils> requestUtils = mockStatic(RequestContextUtils.class)) {

            sessionUtils.when(SessionUtils::getUserDetailsFromSession).thenReturn(user);
            mockRequestContext(requestUtils);

            auditLogService.logCreate("Brand", 1L, "Nike", Map.of("name", "Nike"));

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepo).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertEquals(AuditAction.CREATE, saved.getAction());
            assertEquals("Brand", saved.getEntityType());
            assertEquals(1L, saved.getEntityId());
            assertEquals("admin", saved.getPerformedBy());
            assertNull(saved.getOldValue());
            assertNotNull(saved.getNewValue());
        }
    }

    @Test
    void logCreate_shouldSetPerformedBySystem_whenNoSessionUser() {
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class);
             MockedStatic<RequestContextUtils> requestUtils = mockStatic(RequestContextUtils.class)) {

            sessionUtils.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);
            mockRequestContext(requestUtils);

            auditLogService.logCreate("Brand", 1L, "Nike", Map.of("name", "Nike"));

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepo).save(captor.capture());
            assertEquals("SYSTEM", captor.getValue().getPerformedBy());
        }
    }

    @Test
    void logCreate_shouldRethrowAuditException_whenRepoThrowsAuditException() {
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class);
             MockedStatic<RequestContextUtils> requestUtils = mockStatic(RequestContextUtils.class)) {

            sessionUtils.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);
            mockRequestContext(requestUtils);
            when(auditLogRepo.save(any())).thenThrow(new AuditException("existing audit error"));

            assertThrows(AuditException.class,
                    () -> auditLogService.logCreate("Brand", 1L, null, Map.of()));
        }
    }

    @Test
    void logCreate_shouldThrowAuditException_whenRepoThrowsRuntimeException() {
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class);
             MockedStatic<RequestContextUtils> requestUtils = mockStatic(RequestContextUtils.class)) {

            sessionUtils.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);
            mockRequestContext(requestUtils);
            when(auditLogRepo.save(any())).thenThrow(new RuntimeException("DB error"));

            assertThrows(AuditException.class,
                    () -> auditLogService.logCreate("Brand", 1L, null, Map.of()));
        }
    }

    @Test
    void logUpdate_shouldNotSave_whenNoFieldsChanged() {
        Map<String, Object> entity = Map.of("name", "Nike", "percentageValue", 10);

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class);
             MockedStatic<RequestContextUtils> requestUtils = mockStatic(RequestContextUtils.class)) {

            sessionUtils.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);
            mockRequestContext(requestUtils);

            auditLogService.logUpdate("Brand", 1L, "Nike", entity, entity);

            verify(auditLogRepo, never()).save(any());
        }
    }

    @Test
    void logUpdate_shouldSaveWithChangedFields_whenFieldChanged() {
        Map<String, Object> oldEntity = Map.of("name", "Nike", "value", 10);
        Map<String, Object> newEntity = Map.of("name", "Adidas", "value", 10);

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class);
             MockedStatic<RequestContextUtils> requestUtils = mockStatic(RequestContextUtils.class)) {

            sessionUtils.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);
            mockRequestContext(requestUtils);

            auditLogService.logUpdate("Brand", 1L, "Nike", oldEntity, newEntity);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepo).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertEquals(AuditAction.UPDATE, saved.getAction());
            assertTrue(saved.getChangedFields().contains("name"));
        }
    }

    @Test
    void logUpdate_shouldNotSave_whenSmartListCompareDetectsNoRealChange() {
        // Order entity triggers smart list compare — items with different id but same content
        // are treated as unchanged (id and isDeleted are ignored in normalization)
        Map<String, Object> oldItem = new LinkedHashMap<>();
        oldItem.put("id", 1);
        oldItem.put("product", Map.of("id", 5));
        oldItem.put("quantity", 3);
        oldItem.put("isDeleted", false);

        Map<String, Object> newItem = new LinkedHashMap<>();
        newItem.put("id", 2);
        newItem.put("product", Map.of("id", 5));
        newItem.put("quantity", 3);
        newItem.put("isDeleted", false);

        Map<String, Object> oldEntity = Map.of("products", List.of(oldItem));
        Map<String, Object> newEntity = Map.of("products", List.of(newItem));

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class);
             MockedStatic<RequestContextUtils> requestUtils = mockStatic(RequestContextUtils.class)) {

            sessionUtils.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);
            mockRequestContext(requestUtils);

            auditLogService.logUpdate("Order", 1L, null, oldEntity, newEntity);

            verify(auditLogRepo, never()).save(any());
        }
    }

    @Test
    void logUpdate_shouldSave_whenSmartEntityHasDifferentListContent() {
        // Order entity triggers smart list compare
        Map<String, Object> oldItem = new LinkedHashMap<>();
        oldItem.put("id", 1);
        oldItem.put("product", Map.of("id", 5));
        oldItem.put("quantity", 3);
        oldItem.put("isDeleted", false);

        Map<String, Object> newItem = new LinkedHashMap<>();
        newItem.put("id", 1);
        newItem.put("product", Map.of("id", 5));
        newItem.put("quantity", 10);
        newItem.put("isDeleted", false);

        Map<String, Object> oldEntity = Map.of("products", List.of(oldItem));
        Map<String, Object> newEntity = Map.of("products", List.of(newItem));

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class);
             MockedStatic<RequestContextUtils> requestUtils = mockStatic(RequestContextUtils.class)) {

            sessionUtils.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);
            mockRequestContext(requestUtils);

            auditLogService.logUpdate("Order", 1L, null, oldEntity, newEntity);

            verify(auditLogRepo).save(any());
        }
    }

    @Test
    void logUpdate_shouldSave_whenNonSmartEntityHasDifferentListContent() {
        // Non-smart entity — list comparison uses Objects.equals, different map content = change
        Map<String, Object> oldItem = Map.of("id", 1, "quantity", 3);
        Map<String, Object> newItem = Map.of("id", 2, "quantity", 3);

        Map<String, Object> oldEntity = Map.of("items", List.of(oldItem));
        Map<String, Object> newEntity = Map.of("items", List.of(newItem));

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class);
             MockedStatic<RequestContextUtils> requestUtils = mockStatic(RequestContextUtils.class)) {

            sessionUtils.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);
            mockRequestContext(requestUtils);

            auditLogService.logUpdate("Brand", 1L, null, oldEntity, newEntity);

            verify(auditLogRepo).save(any());
        }
    }

    @Test
    void logUpdate_shouldRethrowAuditException_whenRepoThrowsAuditException() {
        Map<String, Object> oldEntity = Map.of("name", "Nike");
        Map<String, Object> newEntity = Map.of("name", "Adidas");

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class);
             MockedStatic<RequestContextUtils> requestUtils = mockStatic(RequestContextUtils.class)) {

            sessionUtils.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);
            mockRequestContext(requestUtils);
            when(auditLogRepo.save(any())).thenThrow(new AuditException("existing audit error"));

            assertThrows(AuditException.class,
                    () -> auditLogService.logUpdate("Brand", 1L, null, oldEntity, newEntity));
        }
    }

    @Test
    void logUpdate_shouldThrowAuditException_whenRepoThrowsRuntimeException() {
        Map<String, Object> oldEntity = Map.of("name", "Nike");
        Map<String, Object> newEntity = Map.of("name", "Adidas");

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class);
             MockedStatic<RequestContextUtils> requestUtils = mockStatic(RequestContextUtils.class)) {

            sessionUtils.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);
            mockRequestContext(requestUtils);
            when(auditLogRepo.save(any())).thenThrow(new RuntimeException("DB error"));

            assertThrows(AuditException.class,
                    () -> auditLogService.logUpdate("Brand", 1L, null, oldEntity, newEntity));
        }
    }

    @Test
    void logDelete_shouldSaveAuditLog_withDeleteAction() {
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class);
             MockedStatic<RequestContextUtils> requestUtils = mockStatic(RequestContextUtils.class)) {

            sessionUtils.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);
            mockRequestContext(requestUtils);

            auditLogService.logDelete("Brand", 1L, "Nike", Map.of("name", "Nike"));

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepo).save(captor.capture());

            AuditLog saved = captor.getValue();
            assertEquals(AuditAction.DELETE, saved.getAction());
            assertNotNull(saved.getOldValue());
            assertNull(saved.getNewValue());
        }
    }

    @Test
    void logDelete_shouldRethrowAuditException_whenRepoThrowsAuditException() {
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class);
             MockedStatic<RequestContextUtils> requestUtils = mockStatic(RequestContextUtils.class)) {

            sessionUtils.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);
            mockRequestContext(requestUtils);
            when(auditLogRepo.save(any())).thenThrow(new AuditException("existing audit error"));

            assertThrows(AuditException.class,
                    () -> auditLogService.logDelete("Brand", 1L, null, Map.of()));
        }
    }

    @Test
    void logDelete_shouldThrowAuditException_whenRepoThrowsRuntimeException() {
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class);
             MockedStatic<RequestContextUtils> requestUtils = mockStatic(RequestContextUtils.class)) {

            sessionUtils.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);
            mockRequestContext(requestUtils);
            when(auditLogRepo.save(any())).thenThrow(new RuntimeException("DB error"));

            assertThrows(AuditException.class,
                    () -> auditLogService.logDelete("Brand", 1L, null, Map.of()));
        }
    }


    @Test
    void getAuditLogs_shouldUseDefaultFilter_whenFilterIsNull() {
        when(auditLogRepo.findAllWithFilters(isNull(), isNull(), isNull(), isNull(),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        Page<AuditLogDTO> result = auditLogService.getAuditLogs(null, 0, 10);

        assertNotNull(result);
        verify(auditLogRepo).findAllWithFilters(isNull(), isNull(), isNull(), isNull(),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class));
    }

    @Test
    void getAuditLogs_shouldUseDateFrom1900_whenDateFromIsNull() {
        AuditLogFilterDTO filter = new AuditLogFilterDTO();

        when(auditLogRepo.findAllWithFilters(any(), any(), any(), any(),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        auditLogService.getAuditLogs(filter, 0, 10);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditLogRepo).findAllWithFilters(any(), any(), any(), any(),
                fromCaptor.capture(), any(), any());

        assertEquals(1900, fromCaptor.getValue().getYear());
    }

    @Test
    void getAuditLogs_shouldUseDateToNow_whenDateToIsNull() {
        AuditLogFilterDTO filter = new AuditLogFilterDTO();

        when(auditLogRepo.findAllWithFilters(any(), any(), any(), any(),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        LocalDateTime before = LocalDateTime.now();
        auditLogService.getAuditLogs(filter, 0, 10);
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditLogRepo).findAllWithFilters(any(), any(), any(), any(),
                any(), toCaptor.capture(), any());

        LocalDateTime captured = toCaptor.getValue();
        assertFalse(captured.isBefore(before));
        assertFalse(captured.isAfter(after));
    }

    @Test
    void getAuditLogs_shouldUseDateFromAtStartOfDay_whenDateFromProvided() {
        AuditLogFilterDTO filter = new AuditLogFilterDTO();
        filter.setDateFrom(LocalDate.of(2024, 6, 15));

        when(auditLogRepo.findAllWithFilters(any(), any(), any(), any(),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        auditLogService.getAuditLogs(filter, 0, 10);

        ArgumentCaptor<LocalDateTime> fromCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditLogRepo).findAllWithFilters(any(), any(), any(), any(),
                fromCaptor.capture(), any(), any());

        assertEquals(LocalDateTime.of(2024, 6, 15, 0, 0), fromCaptor.getValue());
    }

    @Test
    void getAuditLogs_shouldUseDateToAtEndOfDay_whenDateToProvided() {
        AuditLogFilterDTO filter = new AuditLogFilterDTO();
        filter.setDateTo(LocalDate.of(2024, 6, 15));

        when(auditLogRepo.findAllWithFilters(any(), any(), any(), any(),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        auditLogService.getAuditLogs(filter, 0, 10);

        ArgumentCaptor<LocalDateTime> toCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditLogRepo).findAllWithFilters(any(), any(), any(), any(),
                any(), toCaptor.capture(), any());

        assertEquals(LocalDateTime.of(2024, 6, 15, 23, 59, 59), toCaptor.getValue());
    }

    @Test
    void getAuditLogs_shouldMapAuditLogsToDTO() {
        AuditLog log = AuditLog.builder()
                .id(1L).entityType("Brand").entityId(1L).action(AuditAction.CREATE)
                .performedBy("admin").timestamp(LocalDateTime.now())
                .build();

        when(auditLogRepo.findAllWithFilters(any(), any(), any(), any(),
                any(LocalDateTime.class), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log)));

        Page<AuditLogDTO> result = auditLogService.getAuditLogs(new AuditLogFilterDTO(), 0, 10);

        assertEquals(1, result.getTotalElements());
        assertEquals("Brand", result.getContent().getFirst().getEntityType());
    }
}

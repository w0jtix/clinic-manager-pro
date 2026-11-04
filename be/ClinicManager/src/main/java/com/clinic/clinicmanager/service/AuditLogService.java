package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.AuditLogDTO;
import com.clinic.clinicmanager.DTO.request.AuditLogFilterDTO;
import com.clinic.clinicmanager.model.AuditLog;
import org.springframework.data.domain.Page;

public interface AuditLogService {

    <T> void logCreate(String entityType, Long entityId, String entityKeyTrait, T newEntity);

    <T> void logUpdate(String entityType, Long entityId,String entityKeyTrait, T oldEntity, T newEntity);

    <T> void logDelete(String entityType, Long entityId,String entityKeyTrait, T deletedEntity);

    <T> void logSend(String entityType, Long entityId, String entityKeyTrait, T entity);

    <T> void logSecurity(String entityType, Long entityId, String entityKeyTrait, T newValue);

    <T> void logSecurity(String entityType, Long entityId, String entityKeyTrait, T newValue, String clinicDeviceName);

    <T> void logSecurityInNewTransaction(String entityType, Long entityId, String entityKeyTrait, T newValue);

    Page<AuditLogDTO> getAuditLogs(AuditLogFilterDTO filter, int page, int size);
}

package com.clinic.clinicmanager.DTO.request;

import com.clinic.clinicmanager.model.constants.AuditAction;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class AuditLogFilterDTO {
    private String entityType;
    private AuditAction action;
    private String performedBy;
    private String keyword;
    private LocalDate dateFrom;
    private LocalDate dateTo;
}

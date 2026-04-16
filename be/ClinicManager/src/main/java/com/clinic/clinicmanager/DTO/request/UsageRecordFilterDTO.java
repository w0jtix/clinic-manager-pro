package com.clinic.clinicmanager.DTO.request;

import com.clinic.clinicmanager.model.constants.UsageReason;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class UsageRecordFilterDTO {
    private String keyword;
    private List<Long> employeeIds;
    private UsageReason usageReason;
    private LocalDate startDate;
    private LocalDate endDate;
}

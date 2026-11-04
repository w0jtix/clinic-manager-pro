package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.UsageRecordDTO;
import com.clinic.clinicmanager.DTO.request.UsageRecordFilterDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface UsageRecordService {

    UsageRecordDTO getUsageRecordById(Long id);

    Page<UsageRecordDTO> getUsageRecords(UsageRecordFilterDTO filter, int page, int size);

    UsageRecordDTO createUsageRecord(UsageRecordDTO usageRecord);

    List<UsageRecordDTO> createUsageRecords(List<UsageRecordDTO> usageRecords);

    void deleteUsageRecordById(Long id);
}

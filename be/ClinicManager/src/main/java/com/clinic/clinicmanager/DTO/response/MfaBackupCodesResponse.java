package com.clinic.clinicmanager.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MfaBackupCodesResponse {
    private List<String> backupCodes;
}

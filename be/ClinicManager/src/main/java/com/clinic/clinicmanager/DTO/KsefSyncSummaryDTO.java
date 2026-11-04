package com.clinic.clinicmanager.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class KsefSyncSummaryDTO {

    private boolean fullySynced;
    private long totalLocal;
    private Long totalKsef;
    private List<KsefMonthMismatchDTO> monthsWithMismatch;
}

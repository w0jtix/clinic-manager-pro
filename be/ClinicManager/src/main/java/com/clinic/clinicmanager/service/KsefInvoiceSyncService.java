package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.KsefInvoiceCountDTO;
import com.clinic.clinicmanager.DTO.KsefSyncStatusDTO;
import com.clinic.clinicmanager.DTO.request.KsefInvoiceFilterDTO;

import java.time.YearMonth;

public interface KsefInvoiceSyncService {

    void syncInvoices();

    void syncIssuedInvoicesForMonth(YearMonth month);

    KsefSyncStatusDTO getSyncStatus();

    KsefInvoiceCountDTO getInvoiceCount(KsefInvoiceFilterDTO filter);

    void backfillItemLines();
}

package com.clinic.clinicmanager.config;

import com.clinic.clinicmanager.service.KsefInvoiceSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class KsefItemLineBackfillRunner implements ApplicationRunner {

    private final KsefInvoiceSyncService syncService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            syncService.backfillItemLines();
        } catch (Exception e) {
            log.warn("KSeF item line backfill failed - startup continues: {}", e.getMessage());
        }
    }
}

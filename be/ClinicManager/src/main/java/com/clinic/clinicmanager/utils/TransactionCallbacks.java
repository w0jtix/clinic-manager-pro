package com.clinic.clinicmanager.utils;

import lombok.experimental.UtilityClass;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@UtilityClass
public class TransactionCallbacks {

    public static void runNowAndAfterCommit(Runnable action) {
        action.run();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        }
    }
}

package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.model.constants.CardAccessAction;

public interface CardAccessLogService {

    void log(CardAccessAction action, Long clientId, String resourceType, Long resourceId);
}

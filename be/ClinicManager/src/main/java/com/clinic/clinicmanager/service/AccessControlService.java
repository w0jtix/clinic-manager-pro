package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.AccessControlDTO;
import com.clinic.clinicmanager.DTO.request.accesscontrol.GateRequest;
import com.clinic.clinicmanager.DTO.request.accesscontrol.PauseRequest;
import com.clinic.clinicmanager.DTO.request.accesscontrol.WorkingHoursUpdateRequest;

public interface AccessControlService {

    AccessControlDTO getSettings();

    AccessControlDTO setGateEnabled(GateRequest request);

    AccessControlDTO pause(PauseRequest request);

    AccessControlDTO resume();

    AccessControlDTO updateHours(WorkingHoursUpdateRequest request);

    boolean isGateActive();

    boolean isWithinWorkingHours();

}

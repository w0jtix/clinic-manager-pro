package com.clinic.clinicmanager.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AccessControlDTO {
    private boolean gateEnabled;
    private boolean gateActive;
    private LocalDateTime pausedUntil;
    private String pausedBy;
    private boolean hoursEnabled;
    private List<WorkingHoursDTO> hours;
    private long activeDeviceCount;
}

package com.clinic.clinicmanager.DTO.request.accesscontrol;

import com.clinic.clinicmanager.DTO.WorkingHoursDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkingHoursUpdateRequest {
    private Boolean hoursEnabled;
    private List<WorkingHoursDTO> hours;
}

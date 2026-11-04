package com.clinic.clinicmanager.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.clinic.clinicmanager.model.ClinicWorkingHours;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class WorkingHoursDTO {
    private Integer dayOfWeek;
    private boolean open;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    public WorkingHoursDTO(ClinicWorkingHours hours) {
        this.dayOfWeek = hours.getDayOfWeek();
        this.open = hours.isOpen();
        this.startTime = hours.getStartTime();
        this.endTime = hours.getEndTime();
    }
}

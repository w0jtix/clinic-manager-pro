package com.clinic.clinicmanager.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class KsefMonthMismatchDTO {

    private int year;
    private int month;
    private long localCount;
    private Long ksefCount;
    private long missing;
}

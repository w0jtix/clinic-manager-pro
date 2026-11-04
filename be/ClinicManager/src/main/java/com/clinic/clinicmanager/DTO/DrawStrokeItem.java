package com.clinic.clinicmanager.DTO;

public record DrawStrokeItem(Double width, Boolean pressureSensitive, double[][] points) implements DrawItem {
}

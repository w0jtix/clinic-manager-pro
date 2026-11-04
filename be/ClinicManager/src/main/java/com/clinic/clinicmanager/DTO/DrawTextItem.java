package com.clinic.clinicmanager.DTO;

public record DrawTextItem(String text, Double x, Double y, Double size, Boolean bold) implements DrawItem {
}

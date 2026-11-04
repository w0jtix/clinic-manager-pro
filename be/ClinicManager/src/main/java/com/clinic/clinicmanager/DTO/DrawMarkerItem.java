package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.constants.DrawingMarkerShape;

public record DrawMarkerItem(Long markerId, DrawingMarkerShape shape, String color, Boolean filled, String name,
                             Double x, Double y, Double size) implements DrawItem {
}

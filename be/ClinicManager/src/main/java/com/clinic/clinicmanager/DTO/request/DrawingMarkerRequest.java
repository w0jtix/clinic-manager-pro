package com.clinic.clinicmanager.DTO.request;

import com.clinic.clinicmanager.model.constants.DrawingMarkerShape;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DrawingMarkerRequest {
    private String name;
    private DrawingMarkerShape shape;
    private String color;
    private Boolean filled;
}

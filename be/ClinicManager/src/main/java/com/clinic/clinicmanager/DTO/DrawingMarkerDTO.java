package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.DrawingMarker;
import com.clinic.clinicmanager.model.constants.DrawingMarkerShape;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DrawingMarkerDTO {
    private Long id;
    private String name;
    private DrawingMarkerShape shape;
    private String color;
    private Boolean filled;
    private Integer displayOrder;

    public DrawingMarkerDTO(DrawingMarker marker) {
        this.id = marker.getId();
        this.name = marker.getName();
        this.shape = marker.getShape();
        this.color = marker.getColor();
        this.filled = marker.isFilled();
        this.displayOrder = marker.getDisplayOrder();
    }
}

package com.clinic.clinicmanager.DTO.request;

import com.clinic.clinicmanager.DTO.DrawItem;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DrawingLayerUpdateRequest {
    private List<DrawItem> items;
    private Long expectedVersion;
}

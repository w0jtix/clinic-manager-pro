package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.DrawingMarkerDTO;
import com.clinic.clinicmanager.DTO.request.DrawingMarkerRequest;

import java.util.List;

public interface DrawingMarkerService {

    List<DrawingMarkerDTO> getMarkers();

    DrawingMarkerDTO createMarker(DrawingMarkerRequest request);

    DrawingMarkerDTO updateMarker(Long id, DrawingMarkerRequest request);

    void deleteMarker(Long id);

    List<DrawingMarkerDTO> reorderMarkers(List<Long> ids);
}

package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ClientDrawingDTO;
import com.clinic.clinicmanager.DTO.DrawItem;

import java.util.List;

public interface ClientDrawingService {

    ClientDrawingDTO getDrawing(Long clientId);

    byte[] getLayerItems(Long clientId, Long sheetId, Long layerId);

    void logSheetView(Long clientId, Long sheetId);

    ClientDrawingDTO createSheet(Long clientId, Long templateId, List<DrawItem> items, Long closeSheetId);

    ClientDrawingDTO updateLayerItems(Long clientId, Long sheetId, Long authorUserId, List<DrawItem> items,
                                      Long expectedVersion);
}

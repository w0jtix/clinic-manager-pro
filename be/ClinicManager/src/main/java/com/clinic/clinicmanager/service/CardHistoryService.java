package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.CardHistoryItemDTO;
import org.springframework.data.domain.Page;

public interface CardHistoryService {

    Page<CardHistoryItemDTO> getHistory(Long clientId, int page, int size);
}

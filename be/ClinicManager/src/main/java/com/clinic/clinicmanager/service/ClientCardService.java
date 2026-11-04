package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ClientCardDTO;
import com.clinic.clinicmanager.DTO.ClientCardDataDTO;
import com.clinic.clinicmanager.DTO.ClientCardListItemDTO;
import com.clinic.clinicmanager.DTO.request.ClientCardFilterDTO;
import com.clinic.clinicmanager.model.ClientCard;
import org.springframework.data.domain.Page;

public interface ClientCardService {

    Page<ClientCardListItemDTO> search(ClientCardFilterDTO filter, int page, int size);

    ClientCardDTO getCard(Long clientId);

    ClientCardDTO createCardWithClient(ClientCardDataDTO dto);

    ClientCardDTO updateClientData(Long clientId, ClientCardDataDTO dto);

    ClientCard getOrCreateCard(Long clientId);
}

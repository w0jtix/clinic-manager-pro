package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ClientCardDataDTO;
import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.DTO.ClientDuplicateCheckDTO;
import com.clinic.clinicmanager.DTO.ClientIdentityDTO;
import com.clinic.clinicmanager.DTO.ClientSpendChartDTO;
import com.clinic.clinicmanager.DTO.ClientSummaryDTO;
import com.clinic.clinicmanager.DTO.request.ClientFilterDTO;

import java.util.List;

public interface ClientService {

    ClientDTO getClientById(Long id);

    ClientSummaryDTO getClientSummary(Long id);

    ClientSpendChartDTO getSpendChart(Long id, Integer year);

    List<ClientDTO> getClients(ClientFilterDTO filter);

    ClientDTO createClient(ClientDTO client);

    ClientDTO updateClient(Long id, ClientDTO client);

    void deleteClientById(Long id);

    ClientDuplicateCheckDTO checkDuplicates(String firstName, String lastName, String phoneNumber, Long excludeId);

    ClientDTO updateFromCard(Long id, ClientCardDataDTO data);

    ClientDTO restoreClient(Long id, ClientIdentityDTO identity);
}

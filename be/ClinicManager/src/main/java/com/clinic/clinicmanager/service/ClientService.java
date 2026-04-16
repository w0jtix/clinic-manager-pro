package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.DTO.request.ClientFilterDTO;

import java.util.List;

public interface ClientService {

    ClientDTO getClientById(Long id);

    List<ClientDTO> getClients(ClientFilterDTO filter);

    ClientDTO createClient(ClientDTO client);

    ClientDTO updateClient(Long id, ClientDTO client);

    void deleteClientById(Long id);
}

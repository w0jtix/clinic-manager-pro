package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ClientNoteDTO;

import java.util.List;

public interface ClientNoteService {

    ClientNoteDTO getClientNoteById(Long id);

    List<ClientNoteDTO> getClientNotesByClientId(Long clientId);

    ClientNoteDTO createClientNote(ClientNoteDTO clientNote);

    ClientNoteDTO updateClientNote(Long id, ClientNoteDTO clientNote);

    void deleteClientNoteById(Long id);
}

package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.ClientNoteDTO;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientNote;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.repo.ClientNoteRepo;
import com.clinic.clinicmanager.repo.ClientRepo;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.ClientNoteService;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ClientNoteServiceImpl implements ClientNoteService {
    private final ClientNoteRepo clientNoteRepo;
    private final ClientRepo clientRepo;
    private final EmployeeRepo employeeRepo;
    private final AuditLogService auditLogService;
    private final OwnershipService ownershipService;

    @Override
    public ClientNoteDTO getClientNoteById(Long id) {
        return new ClientNoteDTO(clientNoteRepo.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ClientNote not found with given id: " + id)));
    }

    @Override
    public List<ClientNoteDTO> getClientNotesByClientId(Long clientId) {
        return clientNoteRepo.findByClientId(clientId).stream()
                .map(ClientNoteDTO::new)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ClientNoteDTO createClientNote(ClientNoteDTO clientNoteDTO) {
        try {
            ClientNote noteEntity = clientNoteDTO.toEntity();
            noteEntity.setCreatedByUserId(SessionUtils.getUserIdFromSession());
            ClientNoteDTO savedNote = new ClientNoteDTO(clientNoteRepo.save(noteEntity));
            auditLogService.logCreate("ClientNote", savedNote.getId(), savedNote.getClient().getFirstName() + savedNote.getClient().getLastName(), savedNote);
            return savedNote;
        } catch (Exception e) {
            throw new CreationException("Failed to create ClientNote. Reason: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public ClientNoteDTO updateClientNote(Long id, ClientNoteDTO clientNoteDTO) {
        try {
            ClientNote oldNote = clientNoteRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("ClientNote not found with given id: " + id));
            ownershipService.checkOwnershipOrAdmin(oldNote.getCreatedByUserId());
            ClientNoteDTO oldNoteSnapshot = new ClientNoteDTO(oldNote);
            clientNoteDTO.setId(id);

            ClientNote entityToSave = clientNoteDTO.toEntity();
            entityToSave.setCreatedByUserId(oldNote.getCreatedByUserId());
            ClientNoteDTO savedNote = new ClientNoteDTO(clientNoteRepo.save(entityToSave));
            auditLogService.logUpdate("ClientNote", id, oldNoteSnapshot.getClient().getFirstName() + oldNoteSnapshot.getClient().getLastName(), oldNoteSnapshot, savedNote);
            return savedNote;
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update ClientNote. Reason: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public void deleteClientNoteById(Long id) {
        try {
            ClientNote existingNote = clientNoteRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("ClientNote not found with given id: " + id));
            ownershipService.checkOwnershipOrAdmin(existingNote.getCreatedByUserId());
            ClientNoteDTO noteSnapshot = new ClientNoteDTO(existingNote);
            clientNoteRepo.deleteById(id);
            auditLogService.logDelete("ClientNote", id, noteSnapshot.getClient().getFirstName() + noteSnapshot.getClient().getLastName(), noteSnapshot);
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new DeletionException("Failed to delete ClientNote. Reason: " + e.getMessage(), e);
        }
    }
}

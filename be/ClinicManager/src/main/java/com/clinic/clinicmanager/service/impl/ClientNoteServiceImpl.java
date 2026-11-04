package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.ClientNoteDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.exceptions.ValidationException;
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
import org.springframework.security.access.AccessDeniedException;
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
        return clientNoteRepo.findByClientIdOrderByCreatedAtDescIdDesc(clientId).stream()
                .map(ClientNoteDTO::new)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ClientNoteDTO createClientNote(ClientNoteDTO clientNoteDTO) {
        Long clientId = clientIdOf(clientNoteDTO);
        Long employeeId = createdByIdOf(clientNoteDTO);
        if (clientId == null || employeeId == null) {
            throw new CreationException("Failed to create ClientNote. Reason: client id and createdBy id are required.");
        }
        Client client = requireClient(clientId);
        if (Boolean.TRUE.equals(client.getIsDeleted())) {
            throw new ConflictException("Client is deleted. Restore the client before adding a note.");
        }
        Employee createdBy = requireEmployee(employeeId);

        try {
            ClientNote noteEntity = clientNoteDTO.toEntity();
            noteEntity.setClient(client);
            noteEntity.setCreatedBy(createdBy);
            noteEntity.setCreatedByUserId(SessionUtils.getUserIdFromSession());
            ClientNoteDTO savedNote = new ClientNoteDTO(clientNoteRepo.save(noteEntity));
            auditLogService.logCreate("ClientNote", savedNote.getId(), savedNote.getClient().getFirstName() + savedNote.getClient().getLastName(), savedNote);
            return savedNote;
        } catch (ResourceNotFoundException | ConflictException | ValidationException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new CreationException("Failed to create ClientNote.", e);
        }
    }

    @Override
    @Transactional
    public ClientNoteDTO updateClientNote(Long id, ClientNoteDTO clientNoteDTO) {
        Long clientId = clientIdOf(clientNoteDTO);
        Long employeeId = createdByIdOf(clientNoteDTO);
        if (clientId == null || employeeId == null) {
            throw new UpdateException("Failed to update ClientNote. Reason: client id and createdBy id are required.");
        }
        Client client = requireClient(clientId);
        Employee createdBy = requireEmployee(employeeId);

        try {
            ClientNote oldNote = clientNoteRepo.findOneById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("ClientNote not found with given id: " + id));
            ownershipService.checkOwnershipOrAdmin(oldNote.getCreatedByUserId());
            ClientNoteDTO oldNoteSnapshot = new ClientNoteDTO(oldNote);
            clientNoteDTO.setId(id);

            ClientNote entityToSave = clientNoteDTO.toEntity();
            entityToSave.setClient(client);
            entityToSave.setCreatedBy(createdBy);
            entityToSave.setCreatedByUserId(oldNote.getCreatedByUserId());
            ClientNoteDTO savedNote = new ClientNoteDTO(clientNoteRepo.save(entityToSave));
            auditLogService.logUpdate("ClientNote", id, oldNoteSnapshot.getClient().getFirstName() + oldNoteSnapshot.getClient().getLastName(), oldNoteSnapshot, savedNote);
            return savedNote;
        } catch (ResourceNotFoundException | ConflictException | ValidationException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to update ClientNote.", e);
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
        } catch (ResourceNotFoundException | ConflictException | ValidationException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new DeletionException("Failed to delete ClientNote.", e);
        }
    }

    private Long clientIdOf(ClientNoteDTO clientNoteDTO) {
        return clientNoteDTO.getClient() != null ? clientNoteDTO.getClient().getId() : null;
    }

    private Long createdByIdOf(ClientNoteDTO clientNoteDTO) {
        return clientNoteDTO.getCreatedBy() != null ? clientNoteDTO.getCreatedBy().getId() : null;
    }

    private Client requireClient(Long clientId) {
        return clientRepo.findOneById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with given id: " + clientId));
    }

    private Employee requireEmployee(Long employeeId) {
        return employeeRepo.findOneById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with given id: " + employeeId));
    }
}

package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ClientNoteDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientNote;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.repo.ClientNoteRepo;
import com.clinic.clinicmanager.repo.ClientRepo;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.service.impl.ClientNoteServiceImpl;
import com.clinic.clinicmanager.service.impl.OwnershipService;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientNoteServiceImplTest {

    @Mock ClientNoteRepo clientNoteRepo;
    @Mock ClientRepo clientRepo;
    @Mock EmployeeRepo employeeRepo;
    @Mock AuditLogService auditLogService;
    @Mock OwnershipService ownershipService;

    @InjectMocks
    ClientNoteServiceImpl clientNoteService;

    private Client client() {
        Client c = new Client();
        c.setId(1L);
        c.setFirstName("Jan");
        c.setLastName("Kowalski");
        return c;
    }

    private ClientNote note(Long id) {
        return ClientNote.builder()
                .id(id)
                .content("Treść notatki")
                .createdAt(LocalDate.of(2025, 4, 14))
                .createdBy(Employee.builder().id(1L).name("Anna").lastName("Nowak").build())
                .client(client())
                .createdByUserId(10L)
                .build();
    }

    @Test
    void getClientNoteById_shouldReturnDTO_whenFound() {
        when(clientNoteRepo.findOneById(1L)).thenReturn(Optional.of(note(1L)));

        ClientNoteDTO result = clientNoteService.getClientNoteById(1L);

        assertEquals(1L, result.getId());
        assertEquals("Treść notatki", result.getContent());
    }

    @Test
    void getClientNoteById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(clientNoteRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> clientNoteService.getClientNoteById(99L));
    }

    @Test
    void getClientNotesByClientId_shouldDelegateToRepo() {
        when(clientNoteRepo.findByClientId(1L)).thenReturn(List.of(note(1L), note(2L)));

        List<ClientNoteDTO> result = clientNoteService.getClientNotesByClientId(1L);

        assertEquals(2, result.size());
    }

    @Test
    void createClientNote_shouldSetCreatedByUserIdFromSession_andCallAuditLog() {
        ClientNoteDTO input = new ClientNoteDTO();
        input.setContent("Nowa notatka");

        try (MockedStatic<SessionUtils> mocked = mockStatic(SessionUtils.class)) {
            mocked.when(SessionUtils::getUserIdFromSession).thenReturn(10L);
            when(clientNoteRepo.save(any())).thenReturn(note(1L));

            ClientNoteDTO result = clientNoteService.createClientNote(input);

            assertEquals(1L, result.getId());
            verify(auditLogService).logCreate(eq("ClientNote"), eq(1L), anyString(), any());
        }
    }

    @Test
    void updateClientNote_shouldThrowResourceNotFoundException_whenNotFound() {
        when(clientNoteRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> clientNoteService.updateClientNote(99L, new ClientNoteDTO()));
    }

    @Test
    void updateClientNote_shouldSaveAndReturnDTO_whenOwnershipPasses() {
        when(clientNoteRepo.findOneById(1L)).thenReturn(Optional.of(note(1L)));
        doNothing().when(ownershipService).checkOwnershipOrAdmin(anyLong());
        when(clientNoteRepo.save(any())).thenReturn(note(1L));

        ClientNoteDTO input = new ClientNoteDTO();
        input.setContent("Zaktualizowana");

        ClientNoteDTO result = clientNoteService.updateClientNote(1L, input);

        assertEquals(1L, result.getId());
        verify(auditLogService).logUpdate(eq("ClientNote"), eq(1L), anyString(), any(), any());
    }

    @Test
    void updateClientNote_shouldThrowUpdateException_whenOwnershipFails() {
        when(clientNoteRepo.findOneById(1L)).thenReturn(Optional.of(note(1L)));
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(ownershipService).checkOwnershipOrAdmin(anyLong());

        assertThrows(com.clinic.clinicmanager.exceptions.UpdateException.class,
                () -> clientNoteService.updateClientNote(1L, new ClientNoteDTO()));
        verify(clientNoteRepo, never()).save(any());
    }

    @Test
    void deleteClientNoteById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(clientNoteRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> clientNoteService.deleteClientNoteById(99L));
    }

    @Test
    void deleteClientNoteById_shouldDeleteAndAudit_whenOwnershipPasses() {
        when(clientNoteRepo.findOneById(1L)).thenReturn(Optional.of(note(1L)));
        doNothing().when(ownershipService).checkOwnershipOrAdmin(anyLong());

        clientNoteService.deleteClientNoteById(1L);

        verify(clientNoteRepo).deleteById(1L);
        verify(auditLogService).logDelete(eq("ClientNote"), eq(1L), anyString(), any());
    }

    @Test
    void deleteClientNoteById_shouldThrowDeletionException_whenOwnershipFails() {
        when(clientNoteRepo.findOneById(1L)).thenReturn(Optional.of(note(1L)));
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(ownershipService).checkOwnershipOrAdmin(anyLong());

        assertThrows(com.clinic.clinicmanager.exceptions.DeletionException.class,
                () -> clientNoteService.deleteClientNoteById(1L));
        verify(clientNoteRepo, never()).deleteById(any());
    }
}

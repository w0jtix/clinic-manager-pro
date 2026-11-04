package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.DTO.ClientNoteDTO;
import com.clinic.clinicmanager.DTO.EmployeeSummaryDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
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

    private Employee employee() {
        return Employee.builder().id(1L).name("Anna").lastName("Nowak").build();
    }

    private ClientNoteDTO inputDTO(String content) {
        ClientDTO clientDTO = new ClientDTO();
        clientDTO.setId(1L);

        EmployeeSummaryDTO employeeDTO = new EmployeeSummaryDTO();
        employeeDTO.setId(1L);

        ClientNoteDTO dto = new ClientNoteDTO();
        dto.setContent(content);
        dto.setClient(clientDTO);
        dto.setCreatedBy(employeeDTO);
        dto.setCreatedAt(LocalDate.of(2025, 4, 14));
        return dto;
    }

    private ClientNote note(Long id) {
        return note(id, "Treść notatki", LocalDate.of(2025, 4, 14));
    }

    private ClientNote note(Long id, String content, LocalDate createdAt) {
        return ClientNote.builder()
                .id(id)
                .content(content)
                .createdAt(createdAt)
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
        when(clientNoteRepo.findByClientIdOrderByCreatedAtDescIdDesc(1L))
                .thenReturn(List.of(note(1L), note(2L)));

        List<ClientNoteDTO> result = clientNoteService.getClientNotesByClientId(1L);

        assertEquals(2, result.size());
        verify(clientNoteRepo).findByClientIdOrderByCreatedAtDescIdDesc(1L);
    }

    @Test
    void getClientNotesByClientId_shouldPreserveRepoOrder_newestFirst() {
        ClientNote newest = note(3L, "Najnowsza", LocalDate.of(2025, 5, 2));
        ClientNote sameDayOlder = note(2L, "Tego samego dnia, wcześniejsza", LocalDate.of(2025, 5, 2));
        ClientNote oldest = note(1L, "Najstarsza", LocalDate.of(2025, 4, 14));
        when(clientNoteRepo.findByClientIdOrderByCreatedAtDescIdDesc(1L))
                .thenReturn(List.of(newest, sameDayOlder, oldest));

        List<ClientNoteDTO> result = clientNoteService.getClientNotesByClientId(1L);

        assertEquals(List.of(3L, 2L, 1L), result.stream().map(ClientNoteDTO::getId).toList());
        assertEquals("Najnowsza", result.get(0).getContent());
        assertEquals(LocalDate.of(2025, 4, 14), result.get(2).getCreatedAt());
    }

    @Test
    void createClientNote_shouldSetCreatedByUserIdFromSession_andCallAuditLog() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client()));
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee()));

        try (MockedStatic<SessionUtils> mocked = mockStatic(SessionUtils.class)) {
            mocked.when(SessionUtils::getUserIdFromSession).thenReturn(10L);
            when(clientNoteRepo.save(any())).thenReturn(note(1L));

            ClientNoteDTO result = clientNoteService.createClientNote(inputDTO("Nowa notatka"));

            assertEquals(1L, result.getId());
            verify(auditLogService).logCreate(eq("ClientNote"), eq(1L), anyString(), any());
        }
    }

    @Test
    void createClientNote_shouldPersistManagedClientAndEmployee_notThePayloadCopies() {
        Client managedClient = client();
        Employee managedEmployee = employee();
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(managedClient));
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(managedEmployee));

        ClientNoteDTO input = inputDTO("Nowa notatka");
        input.getClient().setFirstName("Podmieniony");

        try (MockedStatic<SessionUtils> mocked = mockStatic(SessionUtils.class)) {
            mocked.when(SessionUtils::getUserIdFromSession).thenReturn(10L);
            when(clientNoteRepo.save(any())).thenReturn(note(1L));

            clientNoteService.createClientNote(input);

            verify(clientNoteRepo).save(argThat(saved ->
                    saved.getClient() == managedClient && saved.getCreatedBy() == managedEmployee));
        }
    }

    @Test
    void createClientNote_shouldThrowCreationException_whenClientIdIsMissing() {
        ClientNoteDTO input = inputDTO("Nowa notatka");
        input.getClient().setId(null);

        assertThrows(CreationException.class, () -> clientNoteService.createClientNote(input));

        verify(clientNoteRepo, never()).save(any());
    }

    @Test
    void createClientNote_shouldThrowCreationException_whenClientIsAbsentEntirely() {
        ClientNoteDTO input = inputDTO("Nowa notatka");
        input.setClient(null);

        assertThrows(CreationException.class, () -> clientNoteService.createClientNote(input));

        verify(clientNoteRepo, never()).save(any());
    }

    @Test
    void createClientNote_shouldThrowResourceNotFoundException_whenClientDoesNotExist() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> clientNoteService.createClientNote(inputDTO("Nowa notatka")));

        verify(clientNoteRepo, never()).save(any());
    }

    @Test
    void createClientNote_shouldThrowConflictException_whenClientIsDeleted() {
        Client deletedClient = client();
        deletedClient.softDelete();
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(deletedClient));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> clientNoteService.createClientNote(inputDTO("Nowa notatka")));

        assertEquals("Client is deleted. Restore the client before adding a note.", ex.getMessage());
        verify(clientNoteRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void updateClientNote_shouldThrowResourceNotFoundException_whenNotFound() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client()));
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee()));
        when(clientNoteRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> clientNoteService.updateClientNote(99L, inputDTO("Zaktualizowana")));
    }

    @Test
    void updateClientNote_shouldSaveAndReturnDTO_whenOwnershipPasses() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client()));
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee()));
        when(clientNoteRepo.findOneById(1L)).thenReturn(Optional.of(note(1L)));
        doNothing().when(ownershipService).checkOwnershipOrAdmin(anyLong());
        when(clientNoteRepo.save(any())).thenReturn(note(1L));

        ClientNoteDTO result = clientNoteService.updateClientNote(1L, inputDTO("Zaktualizowana"));

        assertEquals(1L, result.getId());
        verify(auditLogService).logUpdate(eq("ClientNote"), eq(1L), anyString(), any(), any());
    }

    @Test
    void updateClientNote_shouldRethrowAccessDeniedException_whenOwnershipFails() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client()));
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee()));
        when(clientNoteRepo.findOneById(1L)).thenReturn(Optional.of(note(1L)));
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(ownershipService).checkOwnershipOrAdmin(anyLong());

        org.springframework.security.access.AccessDeniedException ex = assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> clientNoteService.updateClientNote(1L, inputDTO("Zaktualizowana")));
        assertEquals("denied", ex.getMessage());
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
    void deleteClientNoteById_shouldRethrowAccessDeniedException_whenOwnershipFails() {
        when(clientNoteRepo.findOneById(1L)).thenReturn(Optional.of(note(1L)));
        doThrow(new org.springframework.security.access.AccessDeniedException("denied"))
                .when(ownershipService).checkOwnershipOrAdmin(anyLong());

        org.springframework.security.access.AccessDeniedException ex = assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> clientNoteService.deleteClientNoteById(1L));
        assertEquals("denied", ex.getMessage());
        verify(clientNoteRepo, never()).deleteById(any());
    }
}

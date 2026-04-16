package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.DTO.DiscountDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.Discount;
import com.clinic.clinicmanager.repo.ClientRepo;
import com.clinic.clinicmanager.repo.DiscountRepo;
import com.clinic.clinicmanager.service.impl.DiscountServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiscountServiceImplTest {

    @Mock
    DiscountRepo discountRepo;

    @Mock
    ClientRepo clientRepo;

    @Mock
    AuditLogService auditLogService;

    @InjectMocks
    DiscountServiceImpl discountService;

    @Test
    void getDiscountById_shouldReturnDiscountDTO_whenDiscountExists() {
        Discount discount = Discount.builder().id(1L).name("VIP").percentageValue(10).build();
        when(discountRepo.findOneById(1L)).thenReturn(Optional.of(discount));

        DiscountDTO result = discountService.getDiscountById(1L);

        assertEquals(1L, result.getId());
        assertEquals("VIP", result.getName());
        assertEquals(10, result.getPercentageValue());
    }

    @Test
    void getDiscountById_shouldThrowResourceNotFoundException_whenDiscountNotFound() {
        when(discountRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> discountService.getDiscountById(99L));
    }

    @Test
    void getDiscounts_shouldReturnListFromRepo() {
        List<DiscountDTO> expected = List.of(
                new DiscountDTO(1L, "VIP", 10, 3L),
                new DiscountDTO(2L, "Sen", 15, 1L)
        );
        when(discountRepo.findAllWithClientCount()).thenReturn(expected);

        List<DiscountDTO> result = discountService.getDiscounts();

        assertEquals(2, result.size());
        verify(discountRepo).findAllWithClientCount();
    }

    @Test
    void createDiscount_shouldSaveAndReturnDTO_whenNoClientsProvided() {
        DiscountDTO input = new DiscountDTO();
        input.setName("VIP");
        input.setPercentageValue(10);
        input.setClients(List.of());

        Discount saved = Discount.builder().id(1L).name("VIP").percentageValue(10).build();
        when(discountRepo.save(any(Discount.class))).thenReturn(saved);

        DiscountDTO result = discountService.createDiscount(input);

        assertEquals(1L, result.getId());
        assertEquals("VIP", result.getName());
        verify(discountRepo).save(any(Discount.class));
        verify(clientRepo, never()).saveAll(any());
    }

    @Test
    void createDiscount_shouldAssignDiscountToClients_whenClientsProvided() {
        ClientDTO clientDTO = new ClientDTO();
        clientDTO.setId(10L);

        DiscountDTO input = new DiscountDTO();
        input.setName("VIP");
        input.setPercentageValue(10);
        input.setClients(List.of(clientDTO));

        Discount saved = Discount.builder().id(1L).name("VIP").percentageValue(10).build();
        Client client = new Client();
        client.setId(10L);

        when(discountRepo.save(any(Discount.class))).thenReturn(saved);
        when(clientRepo.findAllById(List.of(10L))).thenReturn(List.of(client));
        // logDiscountClientChanges calls findAllById for added clients
        when(clientRepo.findAllById(List.of(10L))).thenReturn(List.of(client));

        discountService.createDiscount(input);

        verify(clientRepo).saveAll(argThat(clients ->
                ((List<Client>) clients).size() == 1 &&
                ((List<Client>) clients).getFirst().getDiscount() != null
        ));
    }

    @Test
    void createDiscount_shouldCallAuditLogCreate() {
        DiscountDTO input = new DiscountDTO();
        input.setName("VIP");
        input.setPercentageValue(10);
        input.setClients(List.of());

        Discount saved = Discount.builder().id(1L).name("VIP").percentageValue(10).build();
        when(discountRepo.save(any(Discount.class))).thenReturn(saved);

        discountService.createDiscount(input);

        verify(auditLogService).logCreate(eq("Discount"), eq(1L), anyString(), any());
    }

    @Test
    void updateDiscount_shouldReturnUpdatedDTO_whenClientListUnchanged() {
        Discount existing = Discount.builder().id(1L).name("VIP").percentageValue(10).build();
        Client client = new Client();
        client.setId(5L);

        DiscountDTO input = new DiscountDTO();
        input.setName("VIP");
        input.setPercentageValue(20);
        ClientDTO clientDTO = new ClientDTO();
        clientDTO.setId(5L);
        input.setClients(List.of(clientDTO));

        Discount updated = Discount.builder().id(1L).name("VIP").percentageValue(20).build();

        when(discountRepo.findOneById(1L)).thenReturn(Optional.of(existing));
        when(discountRepo.save(any(Discount.class))).thenReturn(updated);
        when(clientRepo.findAllByDiscountId(1L)).thenReturn(List.of(client));

        DiscountDTO result = discountService.updateDiscount(1L, input);

        assertEquals(1L, result.getId());
        assertEquals(20, result.getPercentageValue());
        verify(clientRepo, never()).saveAll(any());
    }

    @Test
    void updateDiscount_shouldReassignClients_whenClientListChanged() {
        Discount existing = Discount.builder().id(1L).name("VIP").percentageValue(10).build();

        Client oldClient = new Client();
        oldClient.setId(5L);
        oldClient.setFirstName("Jan");
        oldClient.setLastName("Kowalski");

        ClientDTO newClientDTO = new ClientDTO();
        newClientDTO.setId(7L);

        DiscountDTO input = new DiscountDTO();
        input.setName("VIP");
        input.setPercentageValue(10);
        input.setClients(List.of(newClientDTO));

        Discount updated = Discount.builder().id(1L).name("VIP").percentageValue(10).build();
        Client newClient = new Client();
        newClient.setId(7L);

        when(discountRepo.findOneById(1L)).thenReturn(Optional.of(existing));
        when(discountRepo.save(any(Discount.class))).thenReturn(updated);
        when(clientRepo.findAllByDiscountId(1L)).thenReturn(List.of(oldClient));
        when(clientRepo.findAllById(List.of(7L))).thenReturn(List.of(newClient));

        discountService.updateDiscount(1L, input);

        // saveAll called twice: unassign old + assign new
        verify(clientRepo, times(2)).saveAll(any());
    }

    @Test
    void updateDiscount_shouldThrowResourceNotFoundException_whenDiscountNotFound() {
        when(discountRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> discountService.updateDiscount(99L, new DiscountDTO()));
    }

    @Test
    void deleteDiscountById_shouldDeleteDiscount_whenNoClientsAssigned() {
        Discount discount = Discount.builder().id(1L).name("VIP").percentageValue(10).build();
        when(discountRepo.findOneById(1L)).thenReturn(Optional.of(discount));
        when(clientRepo.findAllByDiscountId(1L)).thenReturn(List.of());

        discountService.deleteDiscountById(1L);

        verify(discountRepo).deleteById(1L);
        verify(clientRepo, never()).saveAll(any());
    }

    @Test
    void deleteDiscountById_shouldUnassignClientsBeforeDeleting_whenClientsAssigned() {
        Discount discount = Discount.builder().id(1L).name("VIP").percentageValue(10).build();
        Client client = new Client();
        client.setId(5L);
        client.setFirstName("Anna");
        client.setLastName("Nowak");
        client.setDiscount(discount);

        when(discountRepo.findOneById(1L)).thenReturn(Optional.of(discount));
        when(clientRepo.findAllByDiscountId(1L)).thenReturn(List.of(client));

        discountService.deleteDiscountById(1L);

        verify(clientRepo).saveAll(argThat(clients ->
                ((List<Client>) clients).getFirst().getDiscount() == null
        ));
        verify(discountRepo).deleteById(1L);
    }

    @Test
    void deleteDiscountById_shouldThrowResourceNotFoundException_whenDiscountNotFound() {
        when(discountRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> discountService.deleteDiscountById(99L));

        verify(discountRepo, never()).deleteById(any());
    }
}

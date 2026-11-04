package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.DTO.ClientDuplicateCheckDTO;
import com.clinic.clinicmanager.DTO.ClientIdentityDTO;
import com.clinic.clinicmanager.DTO.ClientSpendChartDTO;
import com.clinic.clinicmanager.DTO.ClientSummaryDTO;
import com.clinic.clinicmanager.DTO.request.ClientFilterDTO;
import com.clinic.clinicmanager.service.ClientService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/clients")
public class ClientController {
    private final ClientService clientService;

    @GetMapping("/duplicates")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<ClientDuplicateCheckDTO> checkDuplicates(
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @RequestParam(required = false) String phoneNumber,
            @RequestParam(required = false) Long excludeId) {
        return new ResponseEntity<>(clientService.checkDuplicates(firstName, lastName, phoneNumber, excludeId), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<ClientDTO> getClientById(@PathVariable(value = "id") Long id) {
        ClientDTO client = clientService.getClientById(id);
        return new ResponseEntity<>(client, HttpStatus.OK);
    }

    @GetMapping("/{id}/summary")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<ClientSummaryDTO> getClientSummary(@PathVariable(value = "id") Long id) {
        return new ResponseEntity<>(clientService.getClientSummary(id), HttpStatus.OK);
    }

    @GetMapping("/{id}/spend-chart")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<ClientSpendChartDTO> getSpendChart(
            @PathVariable(value = "id") Long id,
            @RequestParam(required = false) Integer year) {
        return new ResponseEntity<>(clientService.getSpendChart(id, year), HttpStatus.OK);
    }

    @PostMapping("/search")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<List<ClientDTO>> getClients(@RequestBody(required = false) ClientFilterDTO filter) {
        List<ClientDTO> clientList = clientService.getClients(filter);
        return new ResponseEntity<>(clientList, clientList.isEmpty() ? HttpStatus.NO_CONTENT : HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<ClientDTO> createClient(@NonNull @RequestBody ClientDTO client) {
        ClientDTO createdClient = clientService.createClient(client);
        return  new ResponseEntity<>(createdClient, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<ClientDTO> updateClient(@PathVariable(value = "id") Long id, @NonNull @RequestBody ClientDTO client) {
        ClientDTO saved = clientService.updateClient(id, client);
        return new ResponseEntity<>(saved, HttpStatus.OK);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<ClientDTO> restoreClient(
            @PathVariable(value = "id") Long id,
            @RequestBody(required = false) ClientIdentityDTO identity) {
        return new ResponseEntity<>(clientService.restoreClient(id, identity), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<Void> deleteClient(@PathVariable(value = "id") Long id) {
        clientService.deleteClientById(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}

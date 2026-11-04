package com.clinic.clinicmanager.controller;

import com.clinic.clinicmanager.DTO.CardHistoryItemDTO;
import com.clinic.clinicmanager.DTO.ClientCardDTO;
import com.clinic.clinicmanager.DTO.ClientCardDataDTO;
import com.clinic.clinicmanager.DTO.ClientCardListItemDTO;
import com.clinic.clinicmanager.DTO.request.ClientCardFilterDTO;
import com.clinic.clinicmanager.service.CardHistoryService;
import com.clinic.clinicmanager.service.ClientCardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/cards")
public class ClientCardController {
    private final ClientCardService clientCardService;
    private final CardHistoryService cardHistoryService;

    @PostMapping("/search")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<Page<ClientCardListItemDTO>> searchCards(
            @RequestBody(required = false) ClientCardFilterDTO filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Page<ClientCardListItemDTO> cardPage = clientCardService.search(filter, page, size);
        return new ResponseEntity<>(cardPage, cardPage.isEmpty() ? HttpStatus.NO_CONTENT : HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<ClientCardDTO> createCard(@Valid @RequestBody ClientCardDataDTO card) {
        return new ResponseEntity<>(clientCardService.createCardWithClient(card), HttpStatus.CREATED);
    }

    @GetMapping("/{clientId}")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<ClientCardDTO> getCard(@PathVariable(value = "clientId") Long clientId) {
        return new ResponseEntity<>(clientCardService.getCard(clientId), HttpStatus.OK);
    }

    @PutMapping("/{clientId}/client-data")
    @PreAuthorize(("hasRole('USER')"))
    public ResponseEntity<ClientCardDTO> updateClientData(
            @PathVariable(value = "clientId") Long clientId,
            @Valid @RequestBody ClientCardDataDTO data) {
        return new ResponseEntity<>(clientCardService.updateClientData(clientId, data), HttpStatus.OK);
    }

    @GetMapping("/{clientId}/history")
    @PreAuthorize(("hasRole('MASTER')"))
    public ResponseEntity<Page<CardHistoryItemDTO>> getHistory(
            @PathVariable(value = "clientId") Long clientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return new ResponseEntity<>(cardHistoryService.getHistory(clientId, page, size), HttpStatus.OK);
    }
}

package com.clinic.clinicmanager.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.ClientCardDTO;
import com.clinic.clinicmanager.DTO.ClientCardDataDTO;
import com.clinic.clinicmanager.DTO.ClientCardListItemDTO;
import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.DTO.request.ClientCardFilterDTO;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientCard;
import com.clinic.clinicmanager.model.ClientCardDataRevision;
import com.clinic.clinicmanager.model.constants.CardAccessAction;
import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.model.revision.ClientCardDataField;
import com.clinic.clinicmanager.model.revision.ClientDataFieldChange;
import com.clinic.clinicmanager.repo.ClientCardDataRevisionRepo;
import com.clinic.clinicmanager.repo.ClientCardRepo;
import com.clinic.clinicmanager.repo.ClientRepo;
import com.clinic.clinicmanager.repo.VisitRepo;
import com.clinic.clinicmanager.service.CardAccessLogService;
import com.clinic.clinicmanager.service.ClientCardService;
import com.clinic.clinicmanager.service.ClientService;
import com.clinic.clinicmanager.utils.SessionUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ClientCardServiceImpl implements ClientCardService {

    static final String DATA_REVISION_RESOURCE_TYPE = "CLIENT_DATA_REVISION";

    private static final Pattern PHONE_KEYWORD = Pattern.compile("[0-9\\s()+-]+");
    private static final int MIN_PHONE_DIGITS = 3;

    private final ClientRepo clientRepo;
    private final ClientCardRepo clientCardRepo;
    private final VisitRepo visitRepo;
    private final ClientService clientService;
    private final CardAccessLogService cardAccessLogService;
    private final ClientCardDataRevisionRepo dataRevisionRepo;
    private final ObjectMapper objectMapper;

    @Override
    public Page<ClientCardListItemDTO> search(ClientCardFilterDTO filter, int page, int size) {
        String keyword = filter != null && filter.getKeyword() != null ? filter.getKeyword().trim() : "";
        String mode = resolveSearchMode(keyword);
        String keywordParam = ClientRepo.CARD_SEARCH_MODE_NAME.equals(mode) ? escapeLike(keyword) : keyword;
        boolean includeDeleted = SessionUtils.hasRole(AppRoleHierarchy.MASTER);

        return clientRepo.searchCards(mode, keywordParam, includeDeleted, PageRequest.of(page, size))
                .map(ClientCardListItemDTO::new);
    }

    @Override
    @Transactional
    public ClientCardDTO getCard(Long clientId) {
        Client client = findClient(clientId);
        if (Boolean.TRUE.equals(client.getIsDeleted()) && !SessionUtils.hasRole(AppRoleHierarchy.MASTER)) {
            throw new ResourceNotFoundException("Client not found with given id: " + clientId);
        }
        ClientCard card = clientCardRepo.findByClientId(clientId).orElse(null);
        cardAccessLogService.log(CardAccessAction.VIEW_CARD, clientId, null, null);
        return toCardDTO(client, card);
    }

    @Override
    @Transactional
    public ClientCardDTO createCardWithClient(ClientCardDataDTO dto) {
        ClientDTO created = clientService.createClient(dto.toClientDTO());
        ClientCard card = getOrCreateCard(created.getId());
        cardAccessLogService.log(CardAccessAction.CREATE_CARD, created.getId(), null, null);
        return toCardDTO(findClient(created.getId()), card);
    }

    @Override
    @Transactional
    public ClientCardDTO updateClientData(Long clientId, ClientCardDataDTO dto) {
        Map<ClientCardDataField, String> before = snapshot(findClient(clientId));
        clientService.updateFromCard(clientId, dto);
        ClientCard card = getOrCreateCard(clientId);
        Client client = findClient(clientId);

        List<ClientDataFieldChange> changes = diff(before, snapshot(client));
        Long revisionId = null;
        if (!changes.isEmpty()) {
            revisionId = dataRevisionRepo.save(ClientCardDataRevision.builder()
                    .client(client)
                    .changedAt(LocalDateTime.now())
                    .changedByUserId(SessionUtils.getUserIdFromSession())
                    .changes(toJson(changes))
                    .build()).getId();
        }
        cardAccessLogService.log(CardAccessAction.UPDATE_CLIENT_DATA, clientId,
                revisionId == null ? null : DATA_REVISION_RESOURCE_TYPE, revisionId);
        return toCardDTO(client, card);
    }

    @Override
    @Transactional
    public ClientCard getOrCreateCard(Long clientId) {
        return clientCardRepo.findByClientId(clientId).orElseGet(() -> {
            clientCardRepo.insertIfAbsent(clientId, LocalDateTime.now(), SessionUtils.getUserIdFromSession());
            return clientCardRepo.findByClientId(clientId)
                    .orElseThrow(() -> new CreationException("Failed to create card for client: " + clientId));
        });
    }

    private Client findClient(Long clientId) {
        return clientRepo.findOneById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with given id: " + clientId));
    }

    private static Map<ClientCardDataField, String> snapshot(Client client) {
        Map<ClientCardDataField, String> values = new EnumMap<>(ClientCardDataField.class);
        for (ClientCardDataField field : ClientCardDataField.values()) {
            values.put(field, field.valueOf(client));
        }
        return values;
    }

    private static List<ClientDataFieldChange> diff(Map<ClientCardDataField, String> before,
                                                    Map<ClientCardDataField, String> after) {
        List<ClientDataFieldChange> changes = new ArrayList<>();
        for (ClientCardDataField field : ClientCardDataField.values()) {
            if (!Objects.equals(before.get(field), after.get(field))) {
                changes.add(new ClientDataFieldChange(field.getKey(), before.get(field), after.get(field)));
            }
        }
        return changes;
    }

    private String toJson(List<ClientDataFieldChange> changes) {
        try {
            return objectMapper.writeValueAsString(changes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize client data revision.", e);
        }
    }

    private ClientCardDTO toCardDTO(Client client, ClientCard card) {
        return new ClientCardDTO(client, card,
                visitRepo.findLastVisitDateByClientId(client.getId()),
                visitRepo.findLastAbsenceUnpaidDateByClientId(client.getId()));
    }

    private static String resolveSearchMode(String keyword) {
        if (keyword.isEmpty()) {
            return ClientRepo.CARD_SEARCH_MODE_ALL;
        }
        if (PHONE_KEYWORD.matcher(keyword).matches()) {
            long digits = keyword.chars().filter(Character::isDigit).count();
            return digits >= MIN_PHONE_DIGITS ? ClientRepo.CARD_SEARCH_MODE_PHONE : ClientRepo.CARD_SEARCH_MODE_ALL;
        }
        return ClientRepo.CARD_SEARCH_MODE_NAME;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}

package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.ClientCardDTO;
import com.clinic.clinicmanager.DTO.ClientCardDataDTO;
import com.clinic.clinicmanager.DTO.ClientCardListItemDTO;
import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.DTO.request.ClientCardFilterDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientCard;
import com.clinic.clinicmanager.model.ClientCardDataRevision;
import com.clinic.clinicmanager.model.constants.CardAccessAction;
import com.clinic.clinicmanager.model.revision.ClientDataFieldChange;
import com.clinic.clinicmanager.repo.ClientCardDataRevisionRepo;
import com.clinic.clinicmanager.repo.ClientCardRepo;
import com.clinic.clinicmanager.repo.ClientRepo;
import com.clinic.clinicmanager.repo.VisitRepo;
import com.clinic.clinicmanager.repo.projection.ClientCardListProjection;
import com.clinic.clinicmanager.service.impl.ClientCardServiceImpl;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientCardServiceImplTest {

    @Mock ClientRepo clientRepo;
    @Mock ClientCardRepo clientCardRepo;
    @Mock VisitRepo visitRepo;
    @Mock ClientService clientService;
    @Mock CardAccessLogService cardAccessLogService;
    @Mock ClientCardDataRevisionRepo dataRevisionRepo;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks ClientCardServiceImpl clientCardService;

    private Client client(boolean deleted) {
        return Client.builder()
                .id(1L).firstName("Jan").lastName("Kowalski").phoneNumber("600100200")
                .birthDate(LocalDate.of(1980, 1, 1))
                .redFlag(true).signedRegulations(true).isDeleted(deleted)
                .build();
    }

    private ClientCard card(Client client) {
        return ClientCard.builder()
                .id(10L).client(client).version(0L)
                .createdAt(LocalDateTime.of(2026, 9, 1, 12, 0))
                .build();
    }

    private void stubEmptySearch() {
        when(clientRepo.searchCards(anyString(), anyString(), anyBoolean(), any()))
                .thenReturn(new PageImpl<>(List.of()));
    }

    @Test
    void search_nameMode_escapesLikeWildcards() {
        stubEmptySearch();

        try (MockedStatic<SessionUtils> ignored = mockStatic(SessionUtils.class)) {
            clientCardService.search(new ClientCardFilterDTO("  a%b_c\\ "), 0, 25);
        }

        verify(clientRepo).searchCards(ClientRepo.CARD_SEARCH_MODE_NAME, "a\\%b\\_c\\\\", false, PageRequest.of(0, 25));
    }

    @Test
    void search_phoneMode_whenKeywordHasNoLetters() {
        stubEmptySearch();

        try (MockedStatic<SessionUtils> ignored = mockStatic(SessionUtils.class)) {
            clientCardService.search(new ClientCardFilterDTO("+48 (600) 100-2"), 1, 10);
        }

        verify(clientRepo).searchCards(ClientRepo.CARD_SEARCH_MODE_PHONE, "+48 (600) 100-2", false, PageRequest.of(1, 10));
    }

    @Test
    void search_allMode_whenBlankOrTooFewDigits() {
        stubEmptySearch();

        try (MockedStatic<SessionUtils> ignored = mockStatic(SessionUtils.class)) {
            clientCardService.search(null, 0, 25);
            clientCardService.search(new ClientCardFilterDTO("   "), 0, 25);
            clientCardService.search(new ClientCardFilterDTO("+4"), 0, 25);
        }

        verify(clientRepo, times(2)).searchCards(ClientRepo.CARD_SEARCH_MODE_ALL, "", false, PageRequest.of(0, 25));
        verify(clientRepo).searchCards(ClientRepo.CARD_SEARCH_MODE_ALL, "+4", false, PageRequest.of(0, 25));
    }

    @Test
    void search_includesDeleted_onlyForAdmin_andMapsRows() {
        ClientCardListProjection row = mock(ClientCardListProjection.class);
        when(row.getId()).thenReturn(1L);
        when(row.getLastName()).thenReturn("Kowalski");
        when(row.getIsDeleted()).thenReturn(true);
        when(row.getLastVisitDate()).thenReturn(LocalDate.of(2026, 10, 20));
        when(row.getLastVisitAbsence()).thenReturn(true);
        when(clientRepo.searchCards(anyString(), anyString(), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(row)));

        Page<ClientCardListItemDTO> result;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(true);

            result = clientCardService.search(new ClientCardFilterDTO("kow"), 0, 25);
        }

        assertThat(result.getContent()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(1L);
            assertThat(item.getLastName()).isEqualTo("Kowalski");
            assertThat(item.getIsDeleted()).isTrue();
            assertThat(item.getLastVisitDate()).isEqualTo(LocalDate.of(2026, 10, 20));
            assertThat(item.getLastVisitAbsence()).isTrue();
        });
    }

    @Test
    void getCard_throwsNotFound_forUserWhenClientDeleted() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client(true)));

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(false);

            assertThatThrownBy(() -> clientCardService.getCard(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        verifyNoInteractions(cardAccessLogService);
    }

    @Test
    void getCard_adminSeesDeletedClient_andLogsView() {
        Client deleted = client(true);
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(deleted));
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.of(card(deleted)));
        when(visitRepo.findLastVisitDateByClientId(1L)).thenReturn(LocalDate.of(2026, 3, 5));

        ClientCardDTO result;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(true);

            result = clientCardService.getCard(1L);
        }

        assertThat(result.getIsDeleted()).isTrue();
        assertThat(result.getLastVisitDate()).isEqualTo(LocalDate.of(2026, 3, 5));
        assertThat(result.getLastAbsenceUnpaidDate()).isNull();
        assertThat(result.getCardCreatedAt()).isEqualTo(LocalDateTime.of(2026, 9, 1, 12, 0));
        verify(cardAccessLogService).log(CardAccessAction.VIEW_CARD, 1L, null, null);
    }

    @Test
    void getCard_returnsLastAbsenceUnpaidDate_alongsideLastVisitDate() {
        Client client = client(false);
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.of(card(client)));
        when(visitRepo.findLastVisitDateByClientId(1L)).thenReturn(LocalDate.of(2026, 9, 1));
        when(visitRepo.findLastAbsenceUnpaidDateByClientId(1L)).thenReturn(LocalDate.of(2026, 9, 22));

        ClientCardDTO result;
        try (MockedStatic<SessionUtils> ignored = mockStatic(SessionUtils.class)) {
            result = clientCardService.getCard(1L);
        }

        assertThat(result.getLastVisitDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(result.getLastAbsenceUnpaidDate()).isEqualTo(LocalDate.of(2026, 9, 22));
    }

    @Test
    void getCard_returnsNullDates_whenClientHasNoVisits() {
        Client client = client(false);
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.of(card(client)));
        when(visitRepo.findLastVisitDateByClientId(1L)).thenReturn(null);
        when(visitRepo.findLastAbsenceUnpaidDateByClientId(1L)).thenReturn(null);

        ClientCardDTO result;
        try (MockedStatic<SessionUtils> ignored = mockStatic(SessionUtils.class)) {
            result = clientCardService.getCard(1L);
        }

        assertThat(result.getLastVisitDate()).isNull();
        assertThat(result.getLastAbsenceUnpaidDate()).isNull();
    }

    @Test
    void getCard_withoutCardRecord_returnsNullCreatedAt_andDoesNotCreateCard() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client(false)));
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.empty());

        ClientCardDTO result;
        try (MockedStatic<SessionUtils> ignored = mockStatic(SessionUtils.class)) {
            result = clientCardService.getCard(1L);
        }

        assertThat(result.getClientId()).isEqualTo(1L);
        assertThat(result.getCardCreatedAt()).isNull();
        verify(clientCardRepo, never()).insertIfAbsent(any(), any(), any());
        verify(cardAccessLogService).log(CardAccessAction.VIEW_CARD, 1L, null, null);
    }

    @Test
    void createCardWithClient_createsClientAndCard_andLogs() {
        Client created = client(false);
        ClientDTO createdDTO = new ClientDTO(created);
        ClientCard card = card(created);
        when(clientService.createClient(any())).thenReturn(createdDTO);
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.empty(), Optional.of(card));
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(created));

        ClientCardDTO result;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(SessionUtils::getUserIdFromSession).thenReturn(7L);

            result = clientCardService.createCardWithClient(
                    new ClientCardDataDTO("Jan", "Kowalski", LocalDate.of(1980, 1, 1), "600100200", true, true, null));
        }

        ArgumentCaptor<ClientDTO> captor = ArgumentCaptor.forClass(ClientDTO.class);
        verify(clientService).createClient(captor.capture());
        ClientDTO payload = captor.getValue();
        assertThat(payload.getFirstName()).isEqualTo("Jan");
        assertThat(payload.getBirthDate()).isEqualTo(LocalDate.of(1980, 1, 1));
        assertThat(payload.getSignedRegulations()).isTrue();
        assertThat(payload.getBoostClient()).isFalse();
        assertThat(payload.getRedFlag()).isFalse();
        assertThat(payload.getIsDeleted()).isFalse();

        verify(clientCardRepo).insertIfAbsent(eq(1L), any(LocalDateTime.class), eq(7L));
        verify(cardAccessLogService).log(CardAccessAction.CREATE_CARD, 1L, null, null);
        assertThat(result.getClientId()).isEqualTo(1L);
        assertThat(result.getCardCreatedAt()).isNotNull();
    }

    @Test
    void updateClientData_delegatesToClientService_createsCardIfMissing_andLogs() {
        Client existing = client(false);
        ClientCardDataDTO data = new ClientCardDataDTO("Jan", "Nowak", null, null, false, false, null);
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.empty(), Optional.of(card(existing)));
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(existing));

        try (MockedStatic<SessionUtils> ignored = mockStatic(SessionUtils.class)) {
            clientCardService.updateClientData(1L, data);
        }

        verify(clientService).updateFromCard(1L, data);
        verify(clientCardRepo).insertIfAbsent(eq(1L), any(LocalDateTime.class), any());
        verify(cardAccessLogService).log(CardAccessAction.UPDATE_CLIENT_DATA, 1L, null, null);
    }

    @Test
    void updateClientData_existingCard_isNotInsertedAgain() {
        Client existing = client(false);
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.of(card(existing)));
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(existing));

        clientCardService.updateClientData(1L, new ClientCardDataDTO("Jan", "Nowak", null, null, false, false, null));

        verify(clientCardRepo, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void getOrCreateCard_returnsCardInsertedConcurrently() {
        ClientCard concurrent = card(client(false));
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.empty(), Optional.of(concurrent));
        when(clientCardRepo.insertIfAbsent(eq(1L), any(), any())).thenReturn(0);

        ClientCard result;
        try (MockedStatic<SessionUtils> ignored = mockStatic(SessionUtils.class)) {
            result = clientCardService.getOrCreateCard(1L);
        }

        assertThat(result).isSameAs(concurrent);
        verify(clientCardRepo, times(2)).findByClientId(1L);
    }

    @Test
    void updateClientData_withChanges_savesRevisionOfChangedFields_andLogsRevision() throws Exception {
        Client before = Client.builder()
                .id(1L).firstName("Jan").lastName("Kowalski").phoneNumber("600100200")
                .birthDate(LocalDate.of(1980, 1, 1)).signedRegulations(false).redFlag(false)
                .build();
        Client after = Client.builder()
                .id(1L).firstName("Janusz").lastName("Nowak").phoneNumber(null)
                .birthDate(LocalDate.of(1981, 2, 3)).signedRegulations(true).redFlag(true)
                .build();
        ClientCardDataDTO data = new ClientCardDataDTO("Janusz", "Nowak", LocalDate.of(1981, 2, 3), null, true, true, null);
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(before), Optional.of(after));
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.of(card(after)));
        when(dataRevisionRepo.save(any(ClientCardDataRevision.class))).thenAnswer(invocation -> {
            ClientCardDataRevision revision = invocation.getArgument(0);
            revision.setId(300L);
            return revision;
        });

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(SessionUtils::getUserIdFromSession).thenReturn(7L);
            clientCardService.updateClientData(1L, data);
        }

        ArgumentCaptor<ClientCardDataRevision> captor = ArgumentCaptor.forClass(ClientCardDataRevision.class);
        verify(dataRevisionRepo).save(captor.capture());
        ClientCardDataRevision revision = captor.getValue();
        assertThat(revision.getClient()).isSameAs(after);
        assertThat(revision.getChangedByUserId()).isEqualTo(7L);
        assertThat(revision.getChangedAt()).isNotNull();
        List<ClientDataFieldChange> changes = objectMapper.readValue(revision.getChanges(), new TypeReference<>() {
        });
        assertThat(changes).containsExactly(
                new ClientDataFieldChange("firstName", "Jan", "Janusz"),
                new ClientDataFieldChange("lastName", "Kowalski", "Nowak"),
                new ClientDataFieldChange("birthDate", "1980-01-01", "1981-02-03"),
                new ClientDataFieldChange("phoneNumber", "600100200", null),
                new ClientDataFieldChange("signedRegulations", "false", "true"));
        verify(cardAccessLogService).log(CardAccessAction.UPDATE_CLIENT_DATA, 1L, "CLIENT_DATA_REVISION", 300L);
    }

    @Test
    void updateClientData_onlyRedFlagChanged_doesNotSaveRevision() {
        Client before = client(false);
        Client after = client(false);
        after.setRedFlag(false);
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(before), Optional.of(after));
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.of(card(after)));

        clientCardService.updateClientData(1L, new ClientCardDataDTO("Jan", "Kowalski",
                LocalDate.of(1980, 1, 1), "600100200", false, true, null));

        verifyNoInteractions(dataRevisionRepo);
        verify(cardAccessLogService).log(CardAccessAction.UPDATE_CLIENT_DATA, 1L, null, null);
    }

    @Test
    void getCard_returnsRemarks() {
        Client client = client(false);
        client.setRemarks("x");
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.of(card(client)));

        ClientCardDTO result;
        try (MockedStatic<SessionUtils> ignored = mockStatic(SessionUtils.class)) {
            result = clientCardService.getCard(1L);
        }

        assertThat(result.getRemarks()).isEqualTo("x");
    }

    @Test
    void updateClientData_returnsSavedRemarks() {
        Client before = client(false);
        Client after = client(false);
        after.setRemarks("Rocznica ślubu 12.05");
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(before), Optional.of(after));
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.of(card(after)));
        when(dataRevisionRepo.save(any(ClientCardDataRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ClientCardDTO result;
        try (MockedStatic<SessionUtils> ignored = mockStatic(SessionUtils.class)) {
            result = clientCardService.updateClientData(1L, new ClientCardDataDTO("Jan", "Kowalski",
                    LocalDate.of(1980, 1, 1), "600100200", true, true, "Rocznica ślubu 12.05"));
        }

        assertThat(result.getRemarks()).isEqualTo("Rocznica ślubu 12.05");
    }

    @Test
    void updateClientData_remarksChanged_savesRevisionWithRemarksChangeOnly() throws Exception {
        Client before = client(false);
        before.setRemarks("a");
        Client after = client(false);
        after.setRemarks("b");
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(before), Optional.of(after));
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.of(card(after)));
        when(dataRevisionRepo.save(any(ClientCardDataRevision.class))).thenAnswer(invocation -> {
            ClientCardDataRevision revision = invocation.getArgument(0);
            revision.setId(301L);
            return revision;
        });

        try (MockedStatic<SessionUtils> ignored = mockStatic(SessionUtils.class)) {
            clientCardService.updateClientData(1L, new ClientCardDataDTO("Jan", "Kowalski",
                    LocalDate.of(1980, 1, 1), "600100200", true, true, "b"));
        }

        ArgumentCaptor<ClientCardDataRevision> captor = ArgumentCaptor.forClass(ClientCardDataRevision.class);
        verify(dataRevisionRepo).save(captor.capture());
        List<ClientDataFieldChange> changes = objectMapper.readValue(captor.getValue().getChanges(), new TypeReference<>() {
        });
        assertThat(changes).containsExactly(new ClientDataFieldChange("remarks", "a", "b"));
        verify(cardAccessLogService).log(CardAccessAction.UPDATE_CLIENT_DATA, 1L, "CLIENT_DATA_REVISION", 301L);
    }

    @Test
    void updateClientData_remarksCleared_savesRevisionWithNullAfter() throws Exception {
        Client before = client(false);
        before.setRemarks("a");
        Client after = client(false);
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(before), Optional.of(after));
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.of(card(after)));
        when(dataRevisionRepo.save(any(ClientCardDataRevision.class))).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<SessionUtils> ignored = mockStatic(SessionUtils.class)) {
            clientCardService.updateClientData(1L, new ClientCardDataDTO("Jan", "Kowalski",
                    LocalDate.of(1980, 1, 1), "600100200", true, true, "   "));
        }

        ArgumentCaptor<ClientCardDataRevision> captor = ArgumentCaptor.forClass(ClientCardDataRevision.class);
        verify(dataRevisionRepo).save(captor.capture());
        List<ClientDataFieldChange> changes = objectMapper.readValue(captor.getValue().getChanges(), new TypeReference<>() {
        });
        assertThat(changes).containsExactly(new ClientDataFieldChange("remarks", "a", null));
    }

    @Test
    void updateClientData_sameDataIncludingRemarks_doesNotSaveRevision() {
        Client before = client(false);
        before.setRemarks("Rocznica ślubu 12.05");
        Client after = client(false);
        after.setRemarks("Rocznica ślubu 12.05");
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(before), Optional.of(after));
        when(clientCardRepo.findByClientId(1L)).thenReturn(Optional.of(card(after)));

        try (MockedStatic<SessionUtils> ignored = mockStatic(SessionUtils.class)) {
            clientCardService.updateClientData(1L, new ClientCardDataDTO("Jan", "Kowalski",
                    LocalDate.of(1980, 1, 1), "600100200", true, true, "Rocznica ślubu 12.05"));
        }

        verifyNoInteractions(dataRevisionRepo);
        verify(cardAccessLogService).log(CardAccessAction.UPDATE_CLIENT_DATA, 1L, null, null);
    }
}

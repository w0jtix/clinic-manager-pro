package com.clinic.clinicmanager.integration;

import com.clinic.clinicmanager.support.AbstractPostgresTest;

import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.model.CardAccessLog;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientCard;
import com.clinic.clinicmanager.model.TherapyEntry;
import com.clinic.clinicmanager.model.constants.CardAccessAction;
import com.clinic.clinicmanager.repo.CardAccessLogRepo;
import com.clinic.clinicmanager.repo.ClientCardRepo;
import com.clinic.clinicmanager.repo.ClientRepo;
import com.clinic.clinicmanager.repo.TherapyEntryRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CardHistoryAccessLogIntegrationTest extends AbstractPostgresTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 13, 10, 0);
    private static final UserDetailsImpl ADMIN = new UserDetailsImpl(7L, "admin", "pwd", null, null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN")));

    @Autowired private MockMvc mockMvc;
    @Autowired private ClientRepo clientRepo;
    @Autowired private ClientCardRepo clientCardRepo;
    @Autowired private TherapyEntryRepo therapyEntryRepo;
    @Autowired private CardAccessLogRepo cardAccessLogRepo;
    @Autowired private EntityManager em;

    @MockBean
    private AuditLogService auditLogService;

    private Client client;

    @BeforeEach
    void setUp() {
        client = clientRepo.save(Client.builder().firstName("Jan").lastName("Historia").build());
    }

    @AfterEach
    void tearDown() {
        cardAccessLogRepo.deleteAll(logsOf(client.getId()));
        clientRepo.deleteById(client.getId());
    }

    private MockHttpServletRequestBuilder history() {
        return get("/api/cards/" + client.getId() + "/history").with(user(ADMIN));
    }

    private List<CardAccessLog> logsOf(Long clientId) {
        return cardAccessLogRepo.findAll().stream()
                .filter(log -> log.getClientId().equals(clientId))
                .toList();
    }

    private String rawContent(Long entryId) {
        return (String) em.createNativeQuery("SELECT content_enc FROM therapy_entry WHERE id = :id")
                .setParameter("id", entryId)
                .getSingleResult();
    }

    @Test
    void getHistory_commitsOneViewCardHistoryLogPerOpening_andNeverListsIt() throws Exception {
        mockMvc.perform(history().param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
        mockMvc.perform(history().param("page", "1"))
                .andExpect(status().isOk());
        mockMvc.perform(history().param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(history())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        assertThat(logsOf(client.getId()))
                .extracting(CardAccessLog::getAction, CardAccessLog::getUserId,
                        CardAccessLog::getResourceType, CardAccessLog::getResourceId)
                .containsExactly(
                        tuple(CardAccessAction.VIEW_CARD_HISTORY, 7L, null, null),
                        tuple(CardAccessAction.VIEW_CARD_HISTORY, 7L, null, null));
    }

    @Test
    void getHistory_doesNotRewriteEncryptedEntryContent() throws Exception {
        ClientCard card = clientCardRepo.save(ClientCard.builder().client(client).createdAt(NOW).build());
        TherapyEntry entry = therapyEntryRepo.save(TherapyEntry.builder()
                .card(card).entryDate(LocalDate.of(2026, 9, 15)).content("Opatrunek").authorUserId(7L)
                .createdAt(NOW)
                .build());
        cardAccessLogRepo.save(CardAccessLog.builder()
                .at(NOW).userId(7L).action(CardAccessAction.CREATE_THERAPY_ENTRY).clientId(client.getId())
                .resourceType("THERAPY_ENTRY").resourceId(entry.getId())
                .build());
        String contentBefore = rawContent(entry.getId());

        mockMvc.perform(history())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].createdEntry.content").value("Opatrunek"));

        assertThat(rawContent(entry.getId())).isEqualTo(contentBefore);
    }
}

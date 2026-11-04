package com.clinic.clinicmanager.integration;

import com.clinic.clinicmanager.support.AbstractPostgresTest;

import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.DrawingSheetStatus;
import com.clinic.clinicmanager.repo.*;
import com.clinic.clinicmanager.service.AuditLogService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClientDeleteIntegrationTest extends AbstractPostgresTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ClientRepo clientRepo;
    @Autowired private DiscountRepo discountRepo;
    @Autowired private VisitRepo visitRepo;
    @Autowired private EmployeeRepo employeeRepo;
    @Autowired private ClientCardRepo clientCardRepo;
    @Autowired private DrawingTemplateRepo drawingTemplateRepo;
    @Autowired private DrawingSheetRepo drawingSheetRepo;
    @Autowired private DrawingLayerRepo drawingLayerRepo;
    @Autowired private TherapyEntryRepo therapyEntryRepo;
    @Autowired private EntityManager em;

    @MockBean
    private AuditLogService auditLogService;

    /**
     * Deleting a client who has a visit AND an assigned discount.
     * Expected behavior:
     * - visit reference exists → soft-delete (isDeleted=true) instead of physical delete
     * - discount is explicitly cleared (client.setDiscount(null)) before soft-delete
     */
    @Test
    @WithMockUser(roles = {"USER", "ADMIN"})
    void deleteClient_withVisitAndDiscount_shouldSoftDeleteAndClearDiscount() throws Exception {

        Discount discount = discountRepo.save(
                Discount.builder().name("VIP10").percentageValue(10).build());

        Client client = clientRepo.save(Client.builder()
                .firstName("Jan").lastName("Kowalski")
                .signedRegulations(false).boostClient(false)
                .discount(discount)
                .build());
        Long clientId = client.getId();

        Employee employee = employeeRepo.save(
                Employee.builder().name("Anna").lastName("Nowak").drawingColor("#2563eb").build());

        visitRepo.save(Visit.builder()
                .client(client).employee(employee)
                .date(LocalDate.now())
                .items(new ArrayList<>())
                .build());

        mockMvc.perform(delete("/api/clients/" + clientId))
                .andExpect(status().isNoContent());

        em.flush();
        em.clear();

        Client result = clientRepo.findById(clientId).orElseThrow();
        assertThat(result.getIsDeleted()).isTrue();
        assertThat(result.getDiscount()).isNull();
    }

    @Test
    @WithMockUser(roles = {"USER", "ADMIN"})
    void deleteClient_withDrawingLayer_shouldSoftDelete() throws Exception {
        Long clientId = clientWithSheet(true);

        mockMvc.perform(delete("/api/clients/" + clientId))
                .andExpect(status().isNoContent());

        em.flush();
        em.clear();

        assertThat(clientRepo.findById(clientId)).hasValueSatisfying(c -> assertThat(c.getIsDeleted()).isTrue());
        assertThat(drawingLayerRepo.existsBySheetCardClientId(clientId)).isTrue();
    }

    @Test
    @WithMockUser(roles = {"USER", "ADMIN"})
    void deleteClient_withOnlyBlankSheet_shouldHardDelete() throws Exception {
        Long clientId = clientWithSheet(false);

        mockMvc.perform(delete("/api/clients/" + clientId))
                .andExpect(status().isNoContent());

        em.flush();
        em.clear();

        assertThat(clientRepo.findById(clientId)).isEmpty();
        assertThat(clientCardRepo.existsByClientId(clientId)).isFalse();
    }

    @Test
    @WithMockUser(roles = {"USER", "ADMIN"})
    void deleteClient_withInvalidatedTherapyEntry_shouldSoftDelete() throws Exception {
        Client client = clientRepo.save(Client.builder()
                .firstName("Ewa").lastName("Terapia")
                .signedRegulations(false).boostClient(false)
                .build());
        clientCardRepo.insertIfAbsent(client.getId(), LocalDateTime.now(), null);
        ClientCard card = clientCardRepo.findByClientId(client.getId()).orElseThrow();
        therapyEntryRepo.saveAndFlush(TherapyEntry.builder()
                .card(card).entryDate(LocalDate.now()).content("Opatrunek").authorUserId(1L)
                .createdAt(LocalDateTime.now())
                .invalidatedAt(LocalDateTime.now()).invalidatedByUserId(1L)
                .build());
        Long clientId = client.getId();
        em.clear();

        mockMvc.perform(delete("/api/clients/" + clientId))
                .andExpect(status().isNoContent());

        em.flush();
        em.clear();

        assertThat(clientRepo.findById(clientId)).hasValueSatisfying(c -> assertThat(c.getIsDeleted()).isTrue());
        assertThat(therapyEntryRepo.existsByCardClientId(clientId)).isTrue();
    }

    private Long clientWithSheet(boolean withLayer) {
        Client client = clientRepo.save(Client.builder()
                .firstName("Anna").lastName("Rysunek")
                .signedRegulations(false).boostClient(false)
                .build());
        clientCardRepo.insertIfAbsent(client.getId(), LocalDateTime.now(), null);
        ClientCard card = clientCardRepo.findByClientId(client.getId()).orElseThrow();
        DrawingTemplate template = drawingTemplateRepo.saveAndFlush(DrawingTemplate.builder()
                .name("Stopy").displayOrder(1)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());
        DrawingSheet sheet = drawingSheetRepo.saveAndFlush(DrawingSheet.builder()
                .card(card).templateId(template.getId()).sheetNumber(1).width(1536).height(2048)
                .status(DrawingSheetStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .build());
        if (withLayer) {
            drawingLayerRepo.saveAndFlush(DrawingLayer.builder()
                    .sheet(sheet).authorUserId(1L)
                    .fileKey("drawings/0b6f3a52-6a55-4a1e-9f0e-1a2b3c4d5e6f.bin")
                    .sizeBytes(10).contentSha256("a".repeat(64))
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).updatedByUserId(1L)
                    .build());
        }
        em.clear();
        return client.getId();
    }
}

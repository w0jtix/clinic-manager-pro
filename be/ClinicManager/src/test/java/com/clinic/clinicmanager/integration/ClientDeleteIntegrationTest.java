package com.clinic.clinicmanager.integration;

import com.clinic.clinicmanager.model.*;
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
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClientDeleteIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ClientRepo clientRepo;
    @Autowired private DiscountRepo discountRepo;
    @Autowired private VisitRepo visitRepo;
    @Autowired private EmployeeRepo employeeRepo;
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
                Employee.builder().name("Anna").lastName("Nowak").build());

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
}

package com.clinic.clinicmanager.integration;

import com.clinic.clinicmanager.support.AbstractPostgresTest;

import com.clinic.clinicmanager.model.BaseService;
import com.clinic.clinicmanager.model.BaseServiceCategory;
import com.clinic.clinicmanager.repo.BaseServiceCategoryRepo;
import com.clinic.clinicmanager.repo.BaseServiceRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ServiceOrderingIntegrationTest extends AbstractPostgresTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private BaseServiceRepo baseServiceRepo;
    @Autowired private BaseServiceCategoryRepo baseServiceCategoryRepo;
    @Autowired private EntityManager em;

    @MockBean
    private AuditLogService auditLogService;

    @Test
    @WithMockUser(roles = {"USER", "ADMIN"})
    void moveService_afterSeveralMoves_shouldKeepDisplayOrderGaplessAndUnique() throws Exception {
        BaseServiceCategory category = baseServiceCategoryRepo.save(BaseServiceCategory.builder()
                .name("Ordering").color("#abcdef").isDeleted(false).build());

        BaseService a = persistService(category, "A", 0);
        BaseService b = persistService(category, "B", 1);
        BaseService c = persistService(category, "C", 2);
        BaseService d = persistService(category, "D", 3);
        BaseService e = persistService(category, "E", 4);

        move(e.getId(), a.getId());
        assertOrder(category.getId(), "A", "E", "B", "C", "D");

        move(c.getId(), null);
        assertOrder(category.getId(), "C", "A", "E", "B", "D");

        move(a.getId(), d.getId());
        assertOrder(category.getId(), "C", "E", "B", "D", "A");

        move(b.getId(), c.getId());
        assertOrder(category.getId(), "C", "B", "E", "D", "A");
    }

    private BaseService persistService(BaseServiceCategory category, String name, int displayOrder) {
        return baseServiceRepo.save(BaseService.builder()
                .name(name)
                .price(BigDecimal.valueOf(100))
                .duration(30)
                .category(category)
                .displayOrder(displayOrder)
                .isDeleted(false)
                .variants(new HashSet<>())
                .build());
    }

    private void move(Long serviceId, Long afterServiceId) throws Exception {
        String body = afterServiceId == null
                ? "{}"
                : "{\"afterServiceId\":" + afterServiceId + "}";

        mockMvc.perform(put("/api/services/" + serviceId + "/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNoContent());
    }

    private void assertOrder(Long categoryId, String... expectedNames) {
        em.flush();
        em.clear();

        List<BaseService> services = baseServiceRepo.findAllByCategoryId(categoryId);

        assertThat(services).extracting(BaseService::getName).containsExactly(expectedNames);
        assertThat(services).extracting(BaseService::getDisplayOrder)
                .containsExactly(0, 1, 2, 3, 4);
    }
}

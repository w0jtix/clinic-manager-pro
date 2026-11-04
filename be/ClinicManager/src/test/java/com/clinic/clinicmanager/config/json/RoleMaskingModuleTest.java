package com.clinic.clinicmanager.config.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.DTO.ClientDebtDTO;
import com.clinic.clinicmanager.DTO.ClientNoteDTO;
import com.clinic.clinicmanager.DTO.DebtRedemptionDTO;
import com.clinic.clinicmanager.DTO.EmployeeDTO;
import com.clinic.clinicmanager.DTO.VisitDTO;
import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RoleMaskingModuleTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new RoleMaskingModule());

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority(role))));
    }

    private ClientNoteDTO noteWithClientAddress() {
        ClientDTO client = new ClientDTO();
        client.setId(1L);
        client.setFirstName("Jan");
        client.setAddress("Kwiatowa 5/2");
        client.setPostalCode("00-001");
        client.setCity("Warszawa");
        ClientNoteDTO note = new ClientNoteDTO();
        note.setId(5L);
        note.setClient(client);
        return note;
    }

    private JsonNode writeAsHttpResponse(Object value) throws Exception {
        String json = objectMapper.writer()
                .withAttribute(RoleMaskingModule.HTTP_RESPONSE_ATTRIBUTE, Boolean.TRUE)
                .writeValueAsString(value);
        return objectMapper.readTree(json);
    }

    @Test
    void httpResponse_masksNestedClientAddress_whenCallerLacksMaster() throws Exception {
        loginAs("ROLE_USER");

        JsonNode client = writeAsHttpResponse(noteWithClientAddress()).get("client");

        assertThat(client.get("firstName").asText()).isEqualTo("Jan");
        assertThat(client.has("address")).isTrue();
        assertThat(client.get("address").isNull()).isTrue();
        assertThat(client.get("postalCode").isNull()).isTrue();
        assertThat(client.get("city").isNull()).isTrue();
    }

    @Test
    void httpResponse_keepsNestedClientAddress_whenCallerReachesMasterThroughHierarchy() throws Exception {
        loginAs("ROLE_COMPANY");

        JsonNode client = writeAsHttpResponse(noteWithClientAddress()).get("client");

        assertThat(client.get("address").asText()).isEqualTo("Kwiatowa 5/2");
        assertThat(client.get("postalCode").asText()).isEqualTo("00-001");
        assertThat(client.get("city").asText()).isEqualTo("Warszawa");
    }

    @Test
    void httpResponse_masksAddress_whenNoAuthentication() throws Exception {
        JsonNode client = writeAsHttpResponse(noteWithClientAddress()).get("client");

        assertThat(client.get("address").isNull()).isTrue();
    }

    @Test
    void internalSerialization_keepsAddress_evenWhenCallerLacksMaster() throws Exception {
        loginAs("ROLE_USER");

        JsonNode client = objectMapper.readTree(objectMapper.writeValueAsString(noteWithClientAddress())).get("client");

        assertThat(client.get("address").asText()).isEqualTo("Kwiatowa 5/2");
        assertThat(client.get("postalCode").asText()).isEqualTo("00-001");
        assertThat(client.get("city").asText()).isEqualTo("Warszawa");
    }

    @Test
    void deserialization_isNotAffectedByMasking() throws Exception {
        loginAs("ROLE_USER");

        ClientDTO client = objectMapper.readValue("{\"firstName\":\"Jan\",\"address\":\"Polna 1\"}", ClientDTO.class);

        assertThat(client.getAddress()).isEqualTo("Polna 1");
    }

    private VisitDTO visitWithoutReceiptAndInvoice(Long id) {
        VisitDTO visit = new VisitDTO();
        visit.setId(id);
        visit.setNotes("Kontrola");
        visit.setReceipt(false);
        visit.setNamedInvoiceId(77L);
        return visit;
    }

    private ClientDebtDTO debtWithSourceVisit() {
        ClientDebtDTO debt = new ClientDebtDTO();
        debt.setId(9L);
        debt.setSourceVisit(visitWithoutReceiptAndInvoice(5L));
        return debt;
    }

    @Test
    void httpResponse_masksVisitReceiptAndNamedInvoiceId_whenCallerIsUser() throws Exception {
        loginAs(AppRoleHierarchy.USER);

        JsonNode visit = writeAsHttpResponse(visitWithoutReceiptAndInvoice(1L));

        assertThat(visit.get("id").asLong()).isEqualTo(1L);
        assertThat(visit.get("notes").asText()).isEqualTo("Kontrola");
        assertThat(visit.has("receipt")).isTrue();
        assertThat(visit.get("receipt").isNull()).isTrue();
        assertThat(visit.has("namedInvoiceId")).isTrue();
        assertThat(visit.get("namedInvoiceId").isNull()).isTrue();
    }

    @Test
    void httpResponse_masksVisitReceiptAndNamedInvoiceId_whenNoAuthentication() throws Exception {
        JsonNode visit = writeAsHttpResponse(visitWithoutReceiptAndInvoice(1L));

        assertThat(visit.get("receipt").isNull()).isTrue();
        assertThat(visit.get("namedInvoiceId").isNull()).isTrue();
    }

    @Test
    void httpResponse_keepsVisitReceiptAndNamedInvoiceId_whenCallerIsMaster() throws Exception {
        loginAs(AppRoleHierarchy.MASTER);

        JsonNode visit = writeAsHttpResponse(visitWithoutReceiptAndInvoice(1L));

        assertThat(visit.get("receipt").isBoolean()).isTrue();
        assertThat(visit.get("receipt").asBoolean()).isFalse();
        assertThat(visit.get("namedInvoiceId").asLong()).isEqualTo(77L);
    }

    @Test
    void httpResponse_keepsVisitReceiptAndNamedInvoiceId_whenCallerIsAdmin() throws Exception {
        loginAs(AppRoleHierarchy.ADMIN);

        JsonNode visit = writeAsHttpResponse(visitWithoutReceiptAndInvoice(1L));

        assertThat(visit.get("receipt").asBoolean()).isFalse();
        assertThat(visit.get("namedInvoiceId").asLong()).isEqualTo(77L);
    }

    @Test
    void httpResponse_masksDebtSourceVisitReceiptAndNamedInvoiceId_whenCallerIsUser() throws Exception {
        loginAs(AppRoleHierarchy.USER);

        JsonNode sourceVisit = writeAsHttpResponse(debtWithSourceVisit()).get("sourceVisit");

        assertThat(sourceVisit.get("id").asLong()).isEqualTo(5L);
        assertThat(sourceVisit.get("receipt").isNull()).isTrue();
        assertThat(sourceVisit.get("namedInvoiceId").isNull()).isTrue();
    }

    @Test
    void httpResponse_keepsDebtSourceVisitReceipt_whenCallerIsMaster() throws Exception {
        loginAs(AppRoleHierarchy.MASTER);

        JsonNode sourceVisit = writeAsHttpResponse(debtWithSourceVisit()).get("sourceVisit");

        assertThat(sourceVisit.get("receipt").asBoolean()).isFalse();
        assertThat(sourceVisit.get("namedInvoiceId").asLong()).isEqualTo(77L);
    }

    @Test
    void httpResponse_masksRedeemedDebtSourceVisitReceipt_whenCallerIsUser() throws Exception {
        loginAs(AppRoleHierarchy.USER);
        DebtRedemptionDTO redemption = new DebtRedemptionDTO();
        redemption.setId(3L);
        redemption.setDebtSource(debtWithSourceVisit());
        VisitDTO visit = visitWithoutReceiptAndInvoice(1L);
        visit.setDebtRedemptions(List.of(redemption));

        JsonNode json = writeAsHttpResponse(visit);
        JsonNode nestedVisit = json.get("debtRedemptions").get(0).get("debtSource").get("sourceVisit");

        assertThat(json.get("receipt").isNull()).isTrue();
        assertThat(nestedVisit.get("receipt").isNull()).isTrue();
        assertThat(nestedVisit.get("namedInvoiceId").isNull()).isTrue();
    }

    @Test
    void internalSerialization_keepsVisitReceiptAndNamedInvoiceId_evenWhenCallerIsUser() throws Exception {
        loginAs(AppRoleHierarchy.USER);
        VisitDTO dto = visitWithoutReceiptAndInvoice(1L);

        JsonNode visit = objectMapper.readTree(objectMapper.writeValueAsString(dto));

        assertThat(visit.get("receipt").asBoolean()).isFalse();
        assertThat(visit.get("namedInvoiceId").asLong()).isEqualTo(77L);
        assertThat(dto.getReceipt()).isFalse();
        assertThat(dto.getNamedInvoiceId()).isEqualTo(77L);
    }

    @Test
    void deserialization_keepsVisitReceiptFromRequest_whenCallerIsUser() throws Exception {
        loginAs(AppRoleHierarchy.USER);

        VisitDTO visit = objectMapper.readValue("{\"receipt\":false,\"namedInvoiceId\":5}", VisitDTO.class);

        assertThat(visit.getReceipt()).isFalse();
        assertThat(visit.getNamedInvoiceId()).isEqualTo(5L);
    }

    private EmployeeDTO employeeWithAdminAccount() {
        EmployeeDTO employee = new EmployeeDTO();
        employee.setId(4L);
        employee.setName("Anna");
        employee.setAccountRole(AppRoleHierarchy.ADMIN);
        employee.setAvatar("avatar3");
        return employee;
    }

    @Test
    void httpResponse_masksEmployeeAccountRole_butKeepsAvatar_whenCallerIsUser() throws Exception {
        loginAs(AppRoleHierarchy.USER);

        JsonNode employee = writeAsHttpResponse(employeeWithAdminAccount());

        assertThat(employee.get("name").asText()).isEqualTo("Anna");
        assertThat(employee.has("accountRole")).isTrue();
        assertThat(employee.get("accountRole").isNull()).isTrue();
        assertThat(employee.get("avatar").asText()).isEqualTo("avatar3");
    }

    @Test
    void httpResponse_masksEmployeeAccountRole_whenNoAuthentication() throws Exception {
        JsonNode employee = writeAsHttpResponse(employeeWithAdminAccount());

        assertThat(employee.get("accountRole").isNull()).isTrue();
    }

    @Test
    void httpResponse_keepsEmployeeAccountRole_whenCallerIsMaster() throws Exception {
        loginAs(AppRoleHierarchy.MASTER);

        JsonNode employee = writeAsHttpResponse(employeeWithAdminAccount());

        assertThat(employee.get("accountRole").asText()).isEqualTo("ROLE_ADMIN");
        assertThat(employee.get("avatar").asText()).isEqualTo("avatar3");
    }

    @Test
    void httpResponse_keepsEmployeeAccountRole_whenCallerIsCompanyOrAdmin() throws Exception {
        loginAs(AppRoleHierarchy.COMPANY);
        assertThat(writeAsHttpResponse(employeeWithAdminAccount()).get("accountRole").asText()).isEqualTo("ROLE_ADMIN");

        loginAs(AppRoleHierarchy.ADMIN);
        assertThat(writeAsHttpResponse(employeeWithAdminAccount()).get("accountRole").asText()).isEqualTo("ROLE_ADMIN");
    }

    @Test
    void httpResponse_masksAccountRoleInEmployeeList_whenCallerIsUser() throws Exception {
        loginAs(AppRoleHierarchy.USER);

        JsonNode employees = writeAsHttpResponse(List.of(employeeWithAdminAccount()));

        assertThat(employees.get(0).get("accountRole").isNull()).isTrue();
        assertThat(employees.get(0).get("avatar").asText()).isEqualTo("avatar3");
    }
}

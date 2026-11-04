package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.DTO.ClientCardDataDTO;
import com.clinic.clinicmanager.DTO.ClientDTO;
import com.clinic.clinicmanager.DTO.ClientDuplicateCheckDTO;
import com.clinic.clinicmanager.DTO.ClientIdentityDTO;
import com.clinic.clinicmanager.DTO.ClientSpendChartDTO;
import com.clinic.clinicmanager.DTO.ClientSummaryDTO;
import com.clinic.clinicmanager.DTO.DiscountDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.DuplicateClientException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientDebt;
import com.clinic.clinicmanager.model.Discount;
import com.clinic.clinicmanager.model.constants.DebtType;
import com.clinic.clinicmanager.model.constants.PaymentStatus;
import com.clinic.clinicmanager.model.constants.ReviewSource;
import com.clinic.clinicmanager.model.constants.ClientMatchReason;
import com.clinic.clinicmanager.model.constants.VoucherStatus;
import com.clinic.clinicmanager.repo.*;
import com.clinic.clinicmanager.repo.projection.ClientMatchProjection;
import com.clinic.clinicmanager.repo.projection.CompanyRevenueProjection;
import com.clinic.clinicmanager.repo.projection.NameQuantityProjection;
import com.clinic.clinicmanager.service.impl.ClientServiceImpl;
import com.clinic.clinicmanager.service.impl.OwnershipService;
import com.clinic.clinicmanager.utils.SessionUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceImplTest {

    @Mock ClientRepo clientRepo;
    @Mock VisitRepo visitRepo;
    @Mock ClientDebtRepo clientDebtRepo;
    @Mock VoucherRepo voucherRepo;
    @Mock ReviewRepo reviewRepo;
    @Mock AuditLogService auditLogService;
    @Mock OwnershipService ownershipService;
    @Mock ClientCardContentChecker clientCardContentChecker;

    @InjectMocks ClientServiceImpl clientService;


    @Test
    void deleteClientById_softDelete_clearsDiscountBeforeSaving() {
        Discount discount = Discount.builder().id(1L).name("VIP10").percentageValue(10).build();
        Client client = Client.builder()
                .id(1L).firstName("Jan").lastName("Kowalski")
                .discount(discount).isDeleted(false).build();

        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        doNothing().when(ownershipService).checkOwnershipOrAdmin(any());
        when(visitRepo.existsByClientId(1L)).thenReturn(true);
        when(clientRepo.save(any())).thenReturn(client);
        doNothing().when(auditLogService).logDelete(any(), any(), any(), any());

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            clientService.deleteClientById(1L);
        }

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepo).save(captor.capture());

        Client saved = captor.getValue();
        assertThat(saved.getDiscount()).isNull();
        assertThat(saved.getIsDeleted()).isTrue();
    }

    @Test
    void deleteClientById_permanentDelete_clientWithDiscountIsDeleted() {
        Discount discount = Discount.builder().id(1L).name("VIP10").percentageValue(10).build();
        Client client = Client.builder()
                .id(1L).firstName("Jan").lastName("Kowalski")
                .discount(discount).isDeleted(false).build();

        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        doNothing().when(ownershipService).checkOwnershipOrAdmin(any());
        when(visitRepo.existsByClientId(1L)).thenReturn(false);
        when(clientDebtRepo.existsByClientId(1L)).thenReturn(false);
        when(voucherRepo.existsByClientId(1L)).thenReturn(false);
        when(reviewRepo.existsByClientId(1L)).thenReturn(false);
        doNothing().when(clientRepo).deleteById(1L);
        doNothing().when(auditLogService).logDelete(any(), any(), any(), any());

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            clientService.deleteClientById(1L);
        }

        verify(clientRepo).deleteById(1L);
        verify(clientRepo, never()).save(any());
    }

    private NameQuantityProjection nameQuantity(String name, long quantity) {
        NameQuantityProjection projection = mock(NameQuantityProjection.class);
        lenient().when(projection.getName()).thenReturn(name);
        lenient().when(projection.getQuantity()).thenReturn(quantity);
        return projection;
    }

    private CompanyRevenueProjection monthlySpend(int month, String revenue, Integer visitCount) {
        CompanyRevenueProjection projection = mock(CompanyRevenueProjection.class);
        lenient().when(projection.getPeriod()).thenReturn(month);
        lenient().when(projection.getRevenue()).thenReturn(new BigDecimal(revenue));
        lenient().when(projection.getVisitCount()).thenReturn(visitCount);
        return projection;
    }

    private ClientDebt debt(String value, PaymentStatus status) {
        return ClientDebt.builder()
                .value(new BigDecimal(value))
                .type(DebtType.UNPAID)
                .paymentStatus(status)
                .build();
    }

    @Test
    void getClientSummary_throwsNotFound_whenClientDoesNotExist() {
        when(clientRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> clientService.getClientSummary(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(visitRepo, clientDebtRepo, voucherRepo, reviewRepo);
    }

    @Test
    void getClientSummary_returnsVisitCounters_andTotalSpent() {
        when(clientRepo.existsById(1L)).thenReturn(true);
        when(visitRepo.countByClientIdAndAbsenceFalse(1L)).thenReturn(7L);
        when(visitRepo.countByClientIdAndAbsenceTrue(1L)).thenReturn(2L);
        when(visitRepo.countLatenessByClientId(1L)).thenReturn(3L);
        when(visitRepo.sumTotalValueByClientId(1L)).thenReturn(new BigDecimal("1234.50"));

        ClientSummaryDTO result;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            result = clientService.getClientSummary(1L);
        }

        assertThat(result.getVisitsCount()).isEqualTo(7L);
        assertThat(result.getAbsencesCount()).isEqualTo(2L);
        assertThat(result.getLatenessCount()).isEqualTo(3L);
        assertThat(result.getTotalSpent()).isEqualByComparingTo("1234.50");
        verify(visitRepo).sumTotalValueByClientId(1L);
    }

    @Test
    void getClientSummary_returnsZeroTotalSpent_whenNoVisits() {
        when(clientRepo.existsById(1L)).thenReturn(true);
        when(visitRepo.sumTotalValueByClientId(1L)).thenReturn(null);

        ClientSummaryDTO result;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            result = clientService.getClientSummary(1L);
        }

        assertThat(result.getTotalSpent()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getClientSummary_hidesTotalSpent_andSkipsQuery_whenUserIsNotAdmin() {
        when(clientRepo.existsById(1L)).thenReturn(true);
        when(visitRepo.countByClientIdAndAbsenceFalse(1L)).thenReturn(7L);

        ClientSummaryDTO result;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(false);

            result = clientService.getClientSummary(1L);
        }

        assertThat(result.getTotalSpent()).isNull();
        assertThat(result.getVisitsCount()).isEqualTo(7L);
        verify(visitRepo, never()).sumTotalValueByClientId(any());
    }

    @Test
    void getClientSummary_returnsSameAggregates_regardlessOfRole() {
        List<NameQuantityProjection> services = List.of(nameQuantity("Manicure", 5L));
        List<NameQuantityProjection> products = List.of(nameQuantity("Krem", 4L));

        when(clientRepo.existsById(1L)).thenReturn(true);
        when(visitRepo.countByClientIdAndAbsenceFalse(1L)).thenReturn(7L);
        when(visitRepo.countByClientIdAndAbsenceTrue(1L)).thenReturn(2L);
        when(visitRepo.countLatenessByClientId(1L)).thenReturn(3L);
        when(visitRepo.sumTotalValueByClientId(1L)).thenReturn(new BigDecimal("1234.50"));
        when(visitRepo.findServiceQuantitiesByClientId(1L)).thenReturn(services);
        when(visitRepo.findProductQuantitiesByClientId(1L)).thenReturn(products);
        when(clientDebtRepo.findAllByClientIdAndPaymentStatus(1L, PaymentStatus.UNPAID))
                .thenReturn(List.of(debt("100.00", PaymentStatus.UNPAID)));
        when(voucherRepo.countByClientIdAndStatus(1L, VoucherStatus.ACTIVE)).thenReturn(2L);
        when(reviewRepo.existsByClientIdAndSource(1L, ReviewSource.GOOGLE)).thenReturn(true);
        when(reviewRepo.existsByClientIdAndSourceAndIsUsedFalse(1L, ReviewSource.GOOGLE)).thenReturn(true);

        ClientSummaryDTO adminResult;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            adminResult = clientService.getClientSummary(1L);
        }

        ClientSummaryDTO userResult;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(false);

            userResult = clientService.getClientSummary(1L);
        }

        assertThat(userResult.getVisitsCount()).isEqualTo(adminResult.getVisitsCount());
        assertThat(userResult.getAbsencesCount()).isEqualTo(adminResult.getAbsencesCount());
        assertThat(userResult.getLatenessCount()).isEqualTo(adminResult.getLatenessCount());
        assertThat(userResult.getTopService().getName()).isEqualTo(adminResult.getTopService().getName());
        assertThat(userResult.getTopProduct().getName()).isEqualTo(adminResult.getTopProduct().getName());
        assertThat(userResult.isHasDebts()).isEqualTo(adminResult.isHasDebts());
        assertThat(userResult.getDebtTotal()).isEqualByComparingTo(adminResult.getDebtTotal());
        assertThat(userResult.getActiveVouchersCount()).isEqualTo(adminResult.getActiveVouchersCount());
        assertThat(userResult.getGoogleReview().isPresent()).isEqualTo(adminResult.getGoogleReview().isPresent());
        assertThat(userResult.getGoogleReview().isRewardUsed()).isEqualTo(adminResult.getGoogleReview().isRewardUsed());
        assertThat(adminResult.getTotalSpent()).isEqualByComparingTo("1234.50");
        assertThat(userResult.getTotalSpent()).isNull();
    }

    @Test
    void getClientSummary_picksTopServiceAndProduct_withHighestQuantity() {
        List<NameQuantityProjection> services = List.of(
                nameQuantity("Pedicure", 2L),
                nameQuantity("Manicure", 5L),
                nameQuantity("Masaż", 3L));
        List<NameQuantityProjection> products = List.of(
                nameQuantity("Krem", 1L),
                nameQuantity("Peeling", 4L));

        when(clientRepo.existsById(1L)).thenReturn(true);
        when(visitRepo.findServiceQuantitiesByClientId(1L)).thenReturn(services);
        when(visitRepo.findProductQuantitiesByClientId(1L)).thenReturn(products);

        ClientSummaryDTO result = clientService.getClientSummary(1L);

        assertThat(result.getTopService().getName()).isEqualTo("Manicure");
        assertThat(result.getTopService().getQuantity()).isEqualTo(5L);
        assertThat(result.getTopProduct().getName()).isEqualTo("Peeling");
        assertThat(result.getTopProduct().getQuantity()).isEqualTo(4L);
    }

    @Test
    void getClientSummary_returnsNullTopItems_whenNothingSold() {
        when(clientRepo.existsById(1L)).thenReturn(true);
        when(visitRepo.findServiceQuantitiesByClientId(1L)).thenReturn(List.of());
        when(visitRepo.findProductQuantitiesByClientId(1L)).thenReturn(List.of());

        ClientSummaryDTO result = clientService.getClientSummary(1L);

        assertThat(result.getTopService()).isNull();
        assertThat(result.getTopProduct()).isNull();
    }

    @Test
    void getClientSummary_sumsUnpaidDebts_andFlagsHasDebts() {
        when(clientRepo.existsById(1L)).thenReturn(true);
        when(clientDebtRepo.findAllByClientIdAndPaymentStatus(1L, PaymentStatus.UNPAID))
                .thenReturn(List.of(debt("100.00", PaymentStatus.UNPAID), debt("49.50", PaymentStatus.UNPAID)));

        ClientSummaryDTO result = clientService.getClientSummary(1L);

        assertThat(result.isHasDebts()).isTrue();
        assertThat(result.getDebtTotal()).isEqualByComparingTo("149.50");
    }

    @Test
    void getClientSummary_returnsZeroDebtTotal_whenNoUnpaidDebts() {
        when(clientRepo.existsById(1L)).thenReturn(true);
        when(clientDebtRepo.findAllByClientIdAndPaymentStatus(1L, PaymentStatus.UNPAID))
                .thenReturn(List.of());

        ClientSummaryDTO result = clientService.getClientSummary(1L);

        assertThat(result.isHasDebts()).isFalse();
        assertThat(result.getDebtTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getClientSummary_countsOnlyActiveVouchers_withoutLoadingThem() {
        when(clientRepo.existsById(1L)).thenReturn(true);
        when(voucherRepo.countByClientIdAndStatus(1L, VoucherStatus.ACTIVE)).thenReturn(2L);

        ClientSummaryDTO result = clientService.getClientSummary(1L);

        assertThat(result.getActiveVouchersCount()).isEqualTo(2);
        verify(voucherRepo).countByClientIdAndStatus(1L, VoucherStatus.ACTIVE);
        verify(voucherRepo, never()).findAllByClientIdOrderByExpiryDateDesc(any());
    }

    @Test
    void getClientSummary_marksRewardUsed_whenEveryReviewOfSourceIsUsed() {
        when(clientRepo.existsById(1L)).thenReturn(true);
        when(reviewRepo.existsByClientIdAndSource(1L, ReviewSource.GOOGLE)).thenReturn(true);
        when(reviewRepo.existsByClientIdAndSourceAndIsUsedFalse(1L, ReviewSource.GOOGLE)).thenReturn(false);
        when(reviewRepo.existsByClientIdAndSource(1L, ReviewSource.BOOKSY)).thenReturn(true);
        when(reviewRepo.existsByClientIdAndSourceAndIsUsedFalse(1L, ReviewSource.BOOKSY)).thenReturn(false);

        ClientSummaryDTO result = clientService.getClientSummary(1L);

        assertThat(result.getGoogleReview().isPresent()).isTrue();
        assertThat(result.getGoogleReview().isRewardUsed()).isTrue();
        assertThat(result.getBooksyReview().isPresent()).isTrue();
        assertThat(result.getBooksyReview().isRewardUsed()).isTrue();
    }

    @Test
    void getClientSummary_marksRewardAvailable_whenAnyReviewOfSourceIsUnused() {
        when(clientRepo.existsById(1L)).thenReturn(true);
        when(reviewRepo.existsByClientIdAndSource(1L, ReviewSource.GOOGLE)).thenReturn(true);
        when(reviewRepo.existsByClientIdAndSourceAndIsUsedFalse(1L, ReviewSource.GOOGLE)).thenReturn(true);

        ClientSummaryDTO result = clientService.getClientSummary(1L);

        assertThat(result.getGoogleReview().isPresent()).isTrue();
        assertThat(result.getGoogleReview().isRewardUsed()).isFalse();
    }

    @Test
    void getClientSummary_marksReviewsAbsent_whenClientLeftNone() {
        when(clientRepo.existsById(1L)).thenReturn(true);
        when(reviewRepo.existsByClientIdAndSource(1L, ReviewSource.GOOGLE)).thenReturn(false);
        when(reviewRepo.existsByClientIdAndSource(1L, ReviewSource.BOOKSY)).thenReturn(false);

        ClientSummaryDTO result = clientService.getClientSummary(1L);

        assertThat(result.getGoogleReview().isPresent()).isFalse();
        assertThat(result.getGoogleReview().isRewardUsed()).isFalse();
        assertThat(result.getBooksyReview().isPresent()).isFalse();
        assertThat(result.getBooksyReview().isRewardUsed()).isFalse();
    }

    @Test
    void getSpendChart_throwsNotFound_whenClientDoesNotExist() {
        when(clientRepo.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> clientService.getSpendChart(99L, 2025))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(visitRepo);
    }

    @Test
    void getSpendChart_placesMonthlySumsAtMatchingIndexes_andZeroesTheRest() {
        List<CompanyRevenueProjection> rows = List.of(
                monthlySpend(1, "300.00", 2),
                monthlySpend(12, "150.00", 1));

        when(clientRepo.existsById(1L)).thenReturn(true);
        when(visitRepo.findMonthlySpendByClientId(1L, 2025)).thenReturn(rows);

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            ClientSpendChartDTO result = clientService.getSpendChart(1L, 2025);

            assertThat(result.getYear()).isEqualTo(2025);
            assertThat(result.getMonths()).hasSize(12);
            assertThat(result.getMonths().getFirst()).isEqualByComparingTo("300.00");
            assertThat(result.getMonths().get(11)).isEqualByComparingTo("150.00");
            assertThat(result.getMonths().subList(1, 11))
                    .allSatisfy(value -> assertThat(value).isEqualByComparingTo(BigDecimal.ZERO));
        }
    }

    @Test
    void getSpendChart_hidesMonthlyRevenue_butKeepsVisitCounts_whenUserIsNotCompany() {
        List<CompanyRevenueProjection> rows = List.of(monthlySpend(3, "300.00", 2));

        when(clientRepo.existsById(1L)).thenReturn(true);
        when(visitRepo.findMonthlySpendByClientId(1L, 2025)).thenReturn(rows);

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(false);

            ClientSpendChartDTO result = clientService.getSpendChart(1L, 2025);

            assertThat(result.getMonths()).isNull();
            assertThat(result.getVisitCounts()).containsExactly(0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0);
            assertThat(result.getYear()).isEqualTo(2025);
        }
    }

    @Test
    void getSpendChart_placesVisitCountsAtMatchingIndexes_andZeroesTheRest() {
        List<CompanyRevenueProjection> rows = List.of(
                monthlySpend(1, "300.00", 2),
                monthlySpend(12, "150.00", 1));

        when(clientRepo.existsById(1L)).thenReturn(true);
        when(visitRepo.findMonthlySpendByClientId(1L, 2025)).thenReturn(rows);

        ClientSpendChartDTO result = clientService.getSpendChart(1L, 2025);

        assertThat(result.getVisitCounts()).containsExactly(2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
    }

    @Test
    void getSpendChart_fallsBackToZeroVisitCount_whenProjectionReturnsNull() {
        List<CompanyRevenueProjection> rows = List.of(monthlySpend(5, "300.00", null));

        when(clientRepo.existsById(1L)).thenReturn(true);
        when(visitRepo.findMonthlySpendByClientId(1L, 2025)).thenReturn(rows);

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).thenReturn(true);

            ClientSpendChartDTO result = clientService.getSpendChart(1L, 2025);

            assertThat(result.getVisitCounts()).hasSize(12).containsOnly(0);
            assertThat(result.getMonths().get(4)).isEqualByComparingTo("300.00");
        }
    }

    @Test
    void getSpendChart_usesCurrentYear_whenYearNotGiven() {
        int currentYear = LocalDate.now().getYear();
        when(clientRepo.existsById(1L)).thenReturn(true);
        when(visitRepo.findMonthlySpendByClientId(1L, currentYear)).thenReturn(List.of());

        ClientSpendChartDTO result = clientService.getSpendChart(1L, null);

        assertThat(result.getYear()).isEqualTo(currentYear);
        verify(visitRepo).findMonthlySpendByClientId(1L, currentYear);
    }

    @Test
    void getSpendChart_derivesYearRange_fromFirstAndLastVisit() {
        when(clientRepo.existsById(1L)).thenReturn(true);
        when(visitRepo.findFirstVisitDateByClientId(1L)).thenReturn(LocalDate.of(2021, 5, 10));
        when(visitRepo.findLastVisitDateByClientId(1L)).thenReturn(LocalDate.of(2026, 2, 3));

        ClientSpendChartDTO result = clientService.getSpendChart(1L, 2025);

        assertThat(result.getFirstYear()).isEqualTo(2021);
        assertThat(result.getLastYear()).isEqualTo(2026);
    }

    @Test
    void getSpendChart_fallsBackToRequestedYear_whenClientHasNoVisits() {
        when(clientRepo.existsById(1L)).thenReturn(true);
        when(visitRepo.findFirstVisitDateByClientId(1L)).thenReturn(null);
        when(visitRepo.findLastVisitDateByClientId(1L)).thenReturn(null);

        ClientSpendChartDTO result = clientService.getSpendChart(1L, 2023);

        assertThat(result.getFirstYear()).isEqualTo(2023);
        assertThat(result.getLastYear()).isEqualTo(2023);
    }

    private Client clientWithAddress() {
        return Client.builder()
                .id(1L).firstName("Jan").lastName("Kowalski")
                .address("Kwiatowa 5/2").postalCode("00-001").city("Warszawa")
                .isDeleted(false).build();
    }

    @Test
    void getClientById_returnsAddressFields_whenUserIsAdmin() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(clientWithAddress()));

        ClientDTO result;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(true);

            result = clientService.getClientById(1L);
        }

        assertThat(result.getAddress()).isEqualTo("Kwiatowa 5/2");
        assertThat(result.getPostalCode()).isEqualTo("00-001");
        assertThat(result.getCity()).isEqualTo("Warszawa");
    }

    @Test
    void getClientById_hidesAddressFields_whenUserIsNotAdmin() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(clientWithAddress()));

        ClientDTO result;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(false);

            result = clientService.getClientById(1L);
        }

        assertThat(result.getAddress()).isNull();
        assertThat(result.getPostalCode()).isNull();
        assertThat(result.getCity()).isNull();
        assertThat(result.getFirstName()).isEqualTo("Jan");
    }

    @Test
    void updateClient_keepsAddressFromDatabase_whenUserIsNotAdmin() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(clientWithAddress()));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClientDTO payload = new ClientDTO();
        payload.setFirstName("Jan");
        payload.setLastName("Nowak");
        payload.setAddress(null);
        payload.setPostalCode(null);
        payload.setCity(null);

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(false);

            clientService.updateClient(1L, payload);
        }

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepo).save(captor.capture());

        Client saved = captor.getValue();
        assertThat(saved.getAddress()).isEqualTo("Kwiatowa 5/2");
        assertThat(saved.getPostalCode()).isEqualTo("00-001");
        assertThat(saved.getCity()).isEqualTo("Warszawa");
        assertThat(saved.getLastName()).isEqualTo("Nowak");
    }

    @Test
    void updateClient_ignoresSubmittedAddress_andKeepsStoredOne_whenUserIsNotMaster() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(clientWithAddress()));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClientDTO payload = new ClientDTO();
        payload.setFirstName("Jan");
        payload.setLastName("Kowalski");
        payload.setAddress("Polna 1");
        payload.setPostalCode("30-100");
        payload.setCity("Krakow");

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(false);

            clientService.updateClient(1L, payload);
        }

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepo).save(captor.capture());

        Client saved = captor.getValue();
        assertThat(saved.getAddress()).isEqualTo("Kwiatowa 5/2");
        assertThat(saved.getPostalCode()).isEqualTo("00-001");
        assertThat(saved.getCity()).isEqualTo("Warszawa");
    }

    @Test
    void updateClient_savesAddressFields_whenUserIsAdmin() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(clientWithAddress()));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClientDTO payload = new ClientDTO();
        payload.setFirstName("Jan");
        payload.setLastName("Kowalski");
        payload.setAddress("Polna 1");
        payload.setPostalCode("30-100");
        payload.setCity("Krakow");

        ClientDTO result;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(true);

            result = clientService.updateClient(1L, payload);
        }

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepo).save(captor.capture());

        Client saved = captor.getValue();
        assertThat(saved.getAddress()).isEqualTo("Polna 1");
        assertThat(saved.getPostalCode()).isEqualTo("30-100");
        assertThat(saved.getCity()).isEqualTo("Krakow");
        assertThat(result.getAddress()).isEqualTo("Polna 1");
    }

    @Test
    void updateClient_clearsAddressFields_whenAdminSendsNulls() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(clientWithAddress()));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClientDTO payload = new ClientDTO();
        payload.setFirstName("Jan");
        payload.setLastName("Kowalski");
        payload.setAddress(null);
        payload.setPostalCode(null);
        payload.setCity(null);

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(true);

            clientService.updateClient(1L, payload);
        }

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepo).save(captor.capture());

        Client saved = captor.getValue();
        assertThat(saved.getAddress()).isNull();
        assertThat(saved.getPostalCode()).isNull();
        assertThat(saved.getCity()).isNull();
    }

    @Test
    void createClient_ignoresAddressFields_whenUserIsNotAdmin() {
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClientDTO payload = new ClientDTO();
        payload.setFirstName("Anna");
        payload.setLastName("Nowak");
        payload.setAddress("Polna 1");
        payload.setPostalCode("30-100");
        payload.setCity("Krakow");

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(false);
            sessionUtils.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            clientService.createClient(payload);
        }

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepo).save(captor.capture());

        Client saved = captor.getValue();
        assertThat(saved.getAddress()).isNull();
        assertThat(saved.getPostalCode()).isNull();
        assertThat(saved.getCity()).isNull();
    }

    private Discount discountEntity(Long id, String name, int percentage) {
        return Discount.builder().id(id).name(name).percentageValue(percentage).build();
    }

    private DiscountDTO discountDto(Long id, String name, int percentage) {
        return new DiscountDTO(discountEntity(id, name, percentage));
    }

    private Client clientWithDiscount(Discount discount) {
        return Client.builder()
                .id(1L).firstName("Jan").lastName("Kowalski")
                .discount(discount).isDeleted(false).createdByUserId(5L).build();
    }

    private ClientDTO clientPayload(DiscountDTO discount) {
        ClientDTO payload = new ClientDTO();
        payload.setFirstName("Jan");
        payload.setLastName("Kowalski");
        payload.setDiscount(discount);
        return payload;
    }

    private Client captureSavedClient() {
        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepo).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void createClient_ignoresDiscount_whenUserIsNotMaster() {
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClientDTO result;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(false);
            sessionUtils.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            result = clientService.createClient(clientPayload(discountDto(7L, "VIP20", 20)));
        }

        Client saved = captureSavedClient();
        assertThat(saved.getDiscount()).isNull();
        assertThat(saved.getFirstName()).isEqualTo("Jan");
        assertThat(result.getDiscount()).isNull();
    }

    @Test
    void createClient_savesDiscount_whenUserIsMaster() {
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClientDTO result;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(true);
            sessionUtils.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            result = clientService.createClient(clientPayload(discountDto(7L, "VIP20", 20)));
        }

        Client saved = captureSavedClient();
        assertThat(saved.getDiscount()).isNotNull();
        assertThat(saved.getDiscount().getId()).isEqualTo(7L);
        assertThat(saved.getDiscount().getPercentageValue()).isEqualTo(20);
        assertThat(result.getDiscount().getId()).isEqualTo(7L);
    }

    @Test
    void updateClient_keepsStoredDiscount_whenUserIsNotMasterAndSendsOtherDiscount() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(clientWithDiscount(discountEntity(3L, "VIP10", 10))));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClientDTO result;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(false);

            result = clientService.updateClient(1L, clientPayload(discountDto(7L, "VIP20", 20)));
        }

        Client saved = captureSavedClient();
        assertThat(saved.getDiscount().getId()).isEqualTo(3L);
        assertThat(saved.getDiscount().getPercentageValue()).isEqualTo(10);
        assertThat(result.getDiscount().getId()).isEqualTo(3L);
        assertThat(result.getDiscount().getName()).isEqualTo("VIP10");
    }

    @Test
    void updateClient_keepsStoredDiscount_whenUserIsNotMasterAndSendsNull() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(clientWithDiscount(discountEntity(3L, "VIP10", 10))));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(false);

            clientService.updateClient(1L, clientPayload(null));
        }

        Client saved = captureSavedClient();
        assertThat(saved.getDiscount()).isNotNull();
        assertThat(saved.getDiscount().getId()).isEqualTo(3L);
    }

    @Test
    void updateClient_doesNotAssignDiscount_whenUserIsNotMasterAndClientHasNone() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(clientWithDiscount(null)));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(false);

            clientService.updateClient(1L, clientPayload(discountDto(7L, "VIP20", 20)));
        }

        assertThat(captureSavedClient().getDiscount()).isNull();
    }

    @Test
    void updateClient_savesSubmittedDiscount_whenUserIsMaster() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(clientWithDiscount(discountEntity(3L, "VIP10", 10))));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClientDTO result;
        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(true);

            result = clientService.updateClient(1L, clientPayload(discountDto(7L, "VIP20", 20)));
        }

        Client saved = captureSavedClient();
        assertThat(saved.getDiscount().getId()).isEqualTo(7L);
        assertThat(saved.getDiscount().getPercentageValue()).isEqualTo(20);
        assertThat(result.getDiscount().getId()).isEqualTo(7L);
    }

    @Test
    void updateClient_clearsDiscount_whenUserIsMasterAndSendsNull() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(clientWithDiscount(discountEntity(3L, "VIP10", 10))));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(true);

            clientService.updateClient(1L, clientPayload(null));
        }

        assertThat(captureSavedClient().getDiscount()).isNull();
    }

    private ClientMatchProjection match(Long id, String phoneNumber, boolean deleted, String reason) {
        ClientMatchProjection projection = mock(ClientMatchProjection.class);
        lenient().when(projection.getId()).thenReturn(id);
        lenient().when(projection.getFirstName()).thenReturn("Jan");
        lenient().when(projection.getLastName()).thenReturn("Kowalski");
        lenient().when(projection.getPhoneNumber()).thenReturn(phoneNumber);
        lenient().when(projection.getIsDeleted()).thenReturn(deleted);
        lenient().when(projection.getMatchReason()).thenReturn(reason);
        return projection;
    }

    private Client fullClient(boolean deleted) {
        return Client.builder()
                .id(1L).firstName("Jan").lastName("Kowalski").phoneNumber("600100200")
                .birthDate(LocalDate.of(1980, 1, 1))
                .email("jan@example.com").address("Polna 1").postalCode("30-100").city("Krakow")
                .discount(Discount.builder().id(3L).name("VIP10").percentageValue(10).build())
                .boostClient(true).redFlag(false).signedRegulations(true)
                .isDeleted(deleted).createdByUserId(5L)
                .build();
    }

    private ClientCardDataDTO cardData(Boolean redFlag, Boolean signedRegulations) {
        return new ClientCardDataDTO("Janusz", "Kowalczyk", LocalDate.of(1981, 2, 2), "511222333", redFlag, signedRegulations, null);
    }

    @Test
    void createClient_throwsDuplicate_whenExactMatchExists() {
        ClientMatchProjection existing = match(9L, null, true, "EXACT");
        when(clientRepo.findExactMatch("Jan", "Kowalski", null, null, false)).thenReturn(Optional.of(existing));

        ClientDTO payload = new ClientDTO();
        payload.setFirstName("Jan");
        payload.setLastName("Kowalski");

        assertThatThrownBy(() -> clientService.createClient(payload))
                .isInstanceOfSatisfying(DuplicateClientException.class, ex -> {
                    assertThat(ex.getClientId()).isEqualTo(9L);
                    assertThat(ex.isDeleted()).isTrue();
                    assertThat(ex.isWithoutPhone()).isTrue();
                });
        verify(clientRepo, never()).save(any());
    }

    @Test
    void updateClient_throwsDuplicate_excludingEditedClient() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(fullClient(false)));
        ClientMatchProjection match2 = match(2L, "600100200", false, "EXACT");
        when(clientRepo.findExactMatch("Jan", "Kowalski", "600100200", 1L, false))
                .thenReturn(Optional.of(match2));

        ClientDTO payload = new ClientDTO();
        payload.setFirstName("Jan");
        payload.setLastName("Kowalski");
        payload.setPhoneNumber("600100200");

        assertThatThrownBy(() -> clientService.updateClient(1L, payload))
                .isInstanceOfSatisfying(DuplicateClientException.class, ex -> {
                    assertThat(ex.getClientId()).isEqualTo(2L);
                    assertThat(ex.isWithoutPhone()).isFalse();
                });
        verify(clientRepo, never()).save(any());
    }

    @Test
    void updateClient_throwsConflict_whenClientDeleted() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(fullClient(true)));

        ClientDTO payload = new ClientDTO();
        payload.setFirstName("Jan");
        payload.setLastName("Nowak");

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(true);

            assertThatThrownBy(() -> clientService.updateClient(1L, payload))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Client is deleted. Restore the client before editing.");
        }

        verify(clientRepo, never()).save(any());
        verify(clientRepo, never()).findExactMatch(any(), any(), any(), any(), anyBoolean());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void updateClient_keepsDeletedFlagFromEntity_whenDtoSendsDeleted() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(fullClient(false)));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClientDTO payload = new ClientDTO();
        payload.setFirstName("Jan");
        payload.setLastName("Kowalski");
        payload.setIsDeleted(true);

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(true);

            clientService.updateClient(1L, payload);
        }

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepo).save(captor.capture());
        assertThat(captor.getValue().getIsDeleted()).isFalse();
    }

    @Test
    void updateFromCard_changesOnlyCardFields() {
        Client client = fullClient(false);
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(true);

            clientService.updateFromCard(1L, cardData(true, false));
        }

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepo).save(captor.capture());
        Client saved = captor.getValue();
        assertThat(saved.getFirstName()).isEqualTo("Janusz");
        assertThat(saved.getLastName()).isEqualTo("Kowalczyk");
        assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(1981, 2, 2));
        assertThat(saved.getPhoneNumber()).isEqualTo("511222333");
        assertThat(saved.getRedFlag()).isTrue();
        assertThat(saved.getSignedRegulations()).isTrue();
        assertThat(saved.getEmail()).isEqualTo("jan@example.com");
        assertThat(saved.getAddress()).isEqualTo("Polna 1");
        assertThat(saved.getPostalCode()).isEqualTo("30-100");
        assertThat(saved.getCity()).isEqualTo("Krakow");
        assertThat(saved.getDiscount().getId()).isEqualTo(3L);
        assertThat(saved.getBoostClient()).isTrue();
        assertThat(saved.getIsDeleted()).isFalse();
        assertThat(saved.getCreatedByUserId()).isEqualTo(5L);
        verify(clientRepo).findExactMatch("Janusz", "Kowalczyk", "511222333", 1L, false);
        verify(auditLogService).logUpdate(eq("Client"), eq(1L), eq("JanKowalski"), any(ClientDTO.class), any(ClientDTO.class));
    }

    @Test
    void updateFromCard_signsRegulations_whenTrueSent() {
        Client client = fullClient(false);
        client.setSignedRegulations(false);
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<SessionUtils> ignored = mockStatic(SessionUtils.class)) {
            ClientDTO result = clientService.updateFromCard(1L, cardData(null, true));

            assertThat(result.getSignedRegulations()).isTrue();
            assertThat(result.getRedFlag()).isFalse();
            assertThat(result.getAddress()).isNull();
        }
    }

    @Test
    void updateFromCard_throwsConflict_whenClientDeleted() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(fullClient(true)));

        assertThatThrownBy(() -> clientService.updateFromCard(1L, cardData(false, false)))
                .isInstanceOf(ConflictException.class);
        verify(clientRepo, never()).save(any());
    }

    @Test
    void updateFromCard_setsRemarks_whenClientHadNone() {
        Client client = fullClient(false);
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<SessionUtils> ignored = mockStatic(SessionUtils.class)) {
            clientService.updateFromCard(1L, cardDataWithRemarks("Rocznica ślubu 12.05"));
        }

        assertThat(savedClient().getRemarks()).isEqualTo("Rocznica ślubu 12.05");
    }

    @Test
    void updateFromCard_clearsRemarks_whenBlankSent() {
        assertRemarksSavedAs("x", "   ", null);
    }

    @Test
    void updateFromCard_clearsRemarks_whenEmptySent() {
        assertRemarksSavedAs("x", "", null);
    }

    @Test
    void updateFromCard_clearsRemarks_whenNullSent() {
        assertRemarksSavedAs("x", null, null);
    }

    @Test
    void updateFromCard_trimsRemarks() {
        assertRemarksSavedAs(null, "  abc  ", "abc");
    }

    @Test
    void updateFromCard_doesNotTouchRemarks_whenClientDeleted() {
        Client client = fullClient(true);
        client.setRemarks("x");
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> clientService.updateFromCard(1L, cardDataWithRemarks("y")))
                .isInstanceOf(ConflictException.class);
        assertThat(client.getRemarks()).isEqualTo("x");
        verify(clientRepo, never()).save(any());
    }

    @Test
    void updateFromCard_auditLogSnapshotsDoNotContainRemarks() throws Exception {
        Client client = fullClient(false);
        client.setRemarks("stara uwaga");
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<SessionUtils> ignored = mockStatic(SessionUtils.class)) {
            clientService.updateFromCard(1L, cardDataWithRemarks("nowa uwaga"));
        }

        ArgumentCaptor<ClientDTO> oldCaptor = ArgumentCaptor.forClass(ClientDTO.class);
        ArgumentCaptor<ClientDTO> newCaptor = ArgumentCaptor.forClass(ClientDTO.class);
        verify(auditLogService).logUpdate(eq("Client"), eq(1L), any(), oldCaptor.capture(), newCaptor.capture());
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        assertThat(mapper.writeValueAsString(oldCaptor.getValue())).doesNotContain("stara uwaga").doesNotContain("remarks");
        assertThat(mapper.writeValueAsString(newCaptor.getValue())).doesNotContain("nowa uwaga").doesNotContain("remarks");
    }

    @Test
    void clientEntityJson_usedByAuditLogCreateAndDelete_doesNotContainRemarks() throws Exception {
        Client client = fullClient(false);
        client.setDiscount(null);
        client.setRemarks("Rocznica ślubu 12.05");

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(client);

        assertThat(json).doesNotContain("Rocznica").doesNotContain("remarks");
    }

    @Test
    void updateClient_keepsRemarksFromDatabase() {
        Client client = fullClient(false);
        client.setRemarks("x");
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClientDTO payload = new ClientDTO();
        payload.setFirstName("Jan");
        payload.setLastName("Kowalski");

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(true);

            clientService.updateClient(1L, payload);
        }

        assertThat(savedClient().getRemarks()).isEqualTo("x");
    }

    @Test
    void updateClient_keepsRemarksFromDatabase_whenUserIsNotMaster() {
        Client client = fullClient(false);
        client.setRemarks("x");
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ClientDTO payload = new ClientDTO();
        payload.setFirstName("Jan");
        payload.setLastName("Kowalski");

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(false);

            clientService.updateClient(1L, payload);
        }

        assertThat(savedClient().getRemarks()).isEqualTo("x");
    }

    private void assertRemarksSavedAs(String stored, String sent, String expected) {
        Client client = fullClient(false);
        client.setRemarks(stored);
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<SessionUtils> ignored = mockStatic(SessionUtils.class)) {
            clientService.updateFromCard(1L, cardDataWithRemarks(sent));
        }

        assertThat(savedClient().getRemarks()).isEqualTo(expected);
    }

    private Client savedClient() {
        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepo).save(captor.capture());
        return captor.getValue();
    }

    private ClientCardDataDTO cardDataWithRemarks(String remarks) {
        ClientCardDataDTO data = cardData(false, false);
        data.setRemarks(remarks);
        return data;
    }

    @Test
    void updateFromCard_throwsDuplicate_whenIdenticalClientExists() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(fullClient(false)));
        ClientMatchProjection match4 = match(4L, "511222333", false, "EXACT");
        when(clientRepo.findExactMatch("Janusz", "Kowalczyk", "511222333", 1L, false))
                .thenReturn(Optional.of(match4));

        assertThatThrownBy(() -> clientService.updateFromCard(1L, cardData(false, false)))
                .isInstanceOf(DuplicateClientException.class);
        verify(clientRepo, never()).save(any());
    }

    @Test
    void restoreClient_admin_restoresWithoutIdentityCheck() {
        Client client = fullClient(true);
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(true);

            ClientDTO result = clientService.restoreClient(1L, null);

            assertThat(result.getIsDeleted()).isFalse();
        }

        verify(clientRepo, never()).matchesIdentity(any(), any(), any(), any());
        verify(clientRepo).findExactMatch("Jan", "Kowalski", "600100200", 1L, true);
        verify(auditLogService).logUpdate(eq("Client"), eq(1L), any(), any(ClientDTO.class), any(ClientDTO.class));
    }

    @Test
    void restoreClient_user_restores_whenIdentityMatches() {
        Client client = fullClient(true);
        ClientIdentityDTO identity = new ClientIdentityDTO("jan", "kowalski", "+48 600 100 200");
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(clientRepo.matchesIdentity(1L, "jan", "kowalski", "+48 600 100 200")).thenReturn(true);
        when(clientRepo.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(false);

            clientService.restoreClient(1L, identity);
        }

        assertThat(client.getIsDeleted()).isFalse();
    }

    @Test
    void restoreClient_user_throwsAccessDenied_whenIdentityDiffers() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(fullClient(true)));
        when(clientRepo.matchesIdentity(eq(1L), any(), any(), any())).thenReturn(false);

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(false);

            assertThatThrownBy(() -> clientService.restoreClient(1L, new ClientIdentityDTO("Jan", "Nowak", "")))
                    .isInstanceOf(AccessDeniedException.class);
        }

        verify(clientRepo, never()).save(any());
    }

    @Test
    void restoreClient_throwsConflict_whenClientNotDeleted() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(fullClient(false)));

        assertThatThrownBy(() -> clientService.restoreClient(1L, null))
                .isInstanceOf(ConflictException.class);
        verify(clientRepo, never()).save(any());
    }

    @Test
    void restoreClient_throwsNotFound_whenClientMissing() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.restoreClient(1L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void restoreClient_throwsDuplicate_whenActiveIdenticalClientExists() {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(fullClient(true)));
        ClientMatchProjection match8 = match(8L, "600100200", false, "EXACT");
        when(clientRepo.findExactMatch("Jan", "Kowalski", "600100200", 1L, true))
                .thenReturn(Optional.of(match8));

        try (MockedStatic<SessionUtils> sessionUtils = mockStatic(SessionUtils.class)) {
            sessionUtils.when(() -> SessionUtils.hasRole(AppRoleHierarchy.MASTER)).thenReturn(true);

            assertThatThrownBy(() -> clientService.restoreClient(1L, null))
                    .isInstanceOfSatisfying(DuplicateClientException.class, ex -> {
                        assertThat(ex.getClientId()).isEqualTo(8L);
                        assertThat(ex.isDeleted()).isFalse();
                    });
        }

        verify(clientRepo, never()).save(any());
    }

    @Test
    void deleteClientById_softDeletes_whenCardHasProtectedContent() {
        Client client = Client.builder().id(1L).firstName("Jan").lastName("Kowalski").isDeleted(false).build();
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(clientCardContentChecker.hasProtectedContent(1L)).thenReturn(true);
        when(clientRepo.save(any())).thenReturn(client);

        clientService.deleteClientById(1L);

        assertThat(client.getIsDeleted()).isTrue();
        verify(clientRepo).save(client);
        verify(clientRepo, never()).deleteById(any());
    }

    @Test
    void checkDuplicates_mapsExactAndSimilarMatches() {
        ClientMatchProjection match9 = match(9L, "600100200", true, "EXACT");
        when(clientRepo.findExactMatch("Jan", "Kowalski", "600100200", 3L, false))
                .thenReturn(Optional.of(match9));
        ClientMatchProjection match10 = match(10L, "511222333", false, "SAME_NAME");
        when(clientRepo.findSimilar("Jan", "Kowalski", "600100200", 3L))
                .thenReturn(List.of(match10));

        ClientDuplicateCheckDTO result = clientService.checkDuplicates("Jan", "Kowalski", "600100200", 3L);

        assertThat(result.getExactMatch().getId()).isEqualTo(9L);
        assertThat(result.getExactMatch().getIsDeleted()).isTrue();
        assertThat(result.getExactMatch().getMatchReason()).isEqualTo(ClientMatchReason.EXACT);
        assertThat(result.getSimilar()).singleElement()
                .satisfies(similar -> assertThat(similar.getMatchReason()).isEqualTo(ClientMatchReason.SAME_NAME));
    }
}

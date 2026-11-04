package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.*;
import com.clinic.clinicmanager.DTO.request.VisitFilterDTO;
import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.InsufficientPaymentException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.*;
import com.clinic.clinicmanager.repo.*;
import com.clinic.clinicmanager.repo.projection.ClientCardCreatedAtProjection;
import com.clinic.clinicmanager.repo.projection.TherapyEntryCardDateProjection;
import com.clinic.clinicmanager.repo.projection.VisitInvoiceIdProjection;
import com.clinic.clinicmanager.service.impl.*;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VisitServiceImplTest {

    @Mock VisitRepo visitRepo;
    @Mock ClientRepo clientRepo;
    @Mock EmployeeRepo employeeRepo;
    @Mock AppSettingsRepo appSettingsRepo;
    @Mock BaseServiceRepo baseServiceRepo;
    @Mock BaseServiceVariantRepo baseServiceVariantRepo;
    @Mock AuditLogService auditLogService;
    @Mock OwnershipService ownershipService;
    @Mock VisitPricingCalculator visitPricingCalculator;
    @Mock VisitDiscountService discountService;
    @Mock VisitDebtService debtService;
    @Mock VisitSaleService saleService;
    @Mock VisitPaymentService paymentService;
    @Mock NamedInvoiceRepo namedInvoiceRepo;
    @Mock ClientCardRepo clientCardRepo;
    @Mock TherapyEntryRepo therapyEntryRepo;

    @InjectMocks
    VisitServiceImpl visitService;

    private Client client;
    private Employee employee;
    private AppSettings appSettings;
    private Visit savedVisit;
    private BaseService baseService;

    @BeforeEach
    void setUp() {
        client = Client.builder()
                .id(1L).firstName("Anna").lastName("Kowalska").boostClient(false).build();

        employee = Employee.builder()
                .id(1L).name("Jan").lastName("Nowak").build();

        appSettings = AppSettings.builder().build();

        baseService = BaseService.builder()
                .id(1L).name("Konsultacja").price(BigDecimal.valueOf(108.0)).duration(30).build();

        savedVisit = Visit.builder()
                .id(1L).client(client).employee(employee)
                .date(LocalDate.now())
                .absence(false).isBoost(false).isVip(false).receipt(true)
                .paymentStatus(PaymentStatus.PAID)
                .totalValue(BigDecimal.ZERO).totalNet(BigDecimal.ZERO).totalVat(BigDecimal.ZERO)
                .createdByUserId(1L)
                .payments(new ArrayList<>())
                .serviceDiscounts(new ArrayList<>())
                .debtRedemptions(new ArrayList<>())
                .items(new ArrayList<>())
                .build();

        lenient().when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(baseService));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", null, List.of(new SimpleGrantedAuthority(role))));
    }

    private VisitDTO buildMinimalVisitDTO() {
        ClientDTO clientDTO = new ClientDTO();
        clientDTO.setId(1L);

        EmployeeSummaryDTO employeeDTO = new EmployeeSummaryDTO();
        employeeDTO.setId(1L);

        BaseServiceCategoryDTO categoryDTO = new BaseServiceCategoryDTO();
        categoryDTO.setId(1L);

        BaseServiceDTO serviceDTO = new BaseServiceDTO();
        serviceDTO.setId(1L);
        serviceDTO.setDuration(60);
        serviceDTO.setPrice(BigDecimal.valueOf(100.0));
        serviceDTO.setCategory(categoryDTO);

        VisitItemDTO itemDTO = new VisitItemDTO();
        itemDTO.setService(serviceDTO);
        itemDTO.setBoostItem(false);

        VisitDTO dto = new VisitDTO();
        dto.setClient(clientDTO);
        dto.setEmployee(employeeDTO);
        dto.setDate(LocalDate.now());
        dto.setAbsence(false);
        dto.setIsBoost(false);
        dto.setIsVip(false);
        dto.setReceipt(true);
        dto.setServiceDiscounts(new ArrayList<>());
        dto.setItems(List.of(itemDTO));
        dto.setDebtRedemptions(new ArrayList<>());
        dto.setPayments(new ArrayList<>());
        return dto;
    }

    @Test
    void createVisit_shouldThrowCreationException_whenDateIsInFuture() {
        VisitDTO inputDTO = buildMinimalVisitDTO();
        inputDTO.setDate(LocalDate.now().plusDays(1));

        assertThrows(CreationException.class,
                () -> visitService.createVisit(inputDTO));

        verify(visitRepo, never()).save(any(Visit.class));
    }

    @Test
    void createVisit_shouldThrowResourceNotFoundException_whenClientNotFound() {
        VisitDTO inputDTO = buildMinimalVisitDTO();

        when(clientRepo.findOneById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> visitService.createVisit(inputDTO));

        verify(visitRepo, never()).save(any());
    }

    @Test
    void createVisit_shouldThrowConflictException_whenClientIsDeleted() {
        VisitDTO inputDTO = buildMinimalVisitDTO();
        client.softDelete();

        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> visitService.createVisit(inputDTO));

        assertEquals("Client is deleted. Restore the client before adding a visit.", ex.getMessage());
        verify(visitRepo, never()).save(any());
        verifyNoInteractions(discountService, debtService, saleService, paymentService, auditLogService);
    }

    @Test
    void createVisit_shouldThrowResourceNotFoundException_whenEmployeeNotFound() {
        VisitDTO inputDTO = buildMinimalVisitDTO();

        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(appSettingsRepo.getSettings()).thenReturn(appSettings);
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> visitService.createVisit(inputDTO));

        verify(visitRepo, never()).save(any(Visit.class));
    }

    @Test
    void createVisit_shouldThrowResourceNotFoundException_whenBaseServiceNotFound() {
        VisitDTO inputDTO = buildMinimalVisitDTO();

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
            when(appSettingsRepo.getSettings()).thenReturn(appSettings);
            when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
            when(visitRepo.save(any())).thenReturn(savedVisit);
            when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class,
                    () -> visitService.createVisit(inputDTO));
        }
    }

    @Test
    void createVisit_shouldThrowIllegalStateException_whenVariantNotBelongsToService() {
        BaseServiceVariantDTO variantDTO = new BaseServiceVariantDTO();
        variantDTO.setId(1L);

        VisitItemDTO itemDTO = new VisitItemDTO();
        BaseServiceDTO serviceDTO = new BaseServiceDTO();
        serviceDTO.setId(1L);
        itemDTO.setService(serviceDTO);
        itemDTO.setServiceVariant(variantDTO);
        itemDTO.setBoostItem(false);

        VisitDTO inputDTO = buildMinimalVisitDTO();
        inputDTO.setItems(List.of(itemDTO));

        BaseServiceVariant variant = BaseServiceVariant.builder()
                .id(1L).name("Wariant").price(BigDecimal.valueOf(50.0)).duration(15).build();

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
            when(appSettingsRepo.getSettings()).thenReturn(appSettings);
            when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
            when(visitRepo.save(any())).thenReturn(savedVisit);
            when(baseServiceVariantRepo.findOneById(1L)).thenReturn(Optional.of(variant));
            when(baseServiceRepo.serviceHasVariant(1L, 1L)).thenReturn(false);

            assertThrows(IllegalStateException.class,
                    () -> visitService.createVisit(inputDTO));
        }
    }

    @Test
    void createVisit_shouldThrowIllegalStateException_whenProductOutOfStock() {
        Product product = Product.builder()
                .id(2L).name("Produkt")
                .brand(Brand.builder().id(1L).name("BrandName").build())
                .category(ProductCategory.builder().id(1L).name("Produkty").color("0,0,0").build())
                .vatRate(VatRate.VAT_23)
                .sellingPrice(BigDecimal.valueOf(50.0))
                .supply(0)
                .build();

        SaleItemDTO saleItemDTO = new SaleItemDTO();
        saleItemDTO.setProduct(new ProductDTO(product));

        SaleDTO saleDTO = new SaleDTO();
        saleDTO.setItems(List.of(saleItemDTO));

        VisitDTO inputDTO = buildMinimalVisitDTO();
        inputDTO.setSale(saleDTO);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
            when(appSettingsRepo.getSettings()).thenReturn(appSettings);
            when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
            when(visitRepo.save(any())).thenReturn(savedVisit);
            doThrow(new IllegalStateException("Product out of stock! ProductId: 2"))
                    .when(saleService).processSale(any(), any(), anyBoolean());

            assertThrows(IllegalStateException.class,
                    () -> visitService.createVisit(inputDTO));
        }
    }

    @Test
    void createVisit_shouldThrowIllegalStateException_whenSameVoucherUsedTwice() {
        VoucherDTO voucherDTO = new VoucherDTO();
        voucherDTO.setId(1L);

        PaymentDTO payment1 = new PaymentDTO();
        payment1.setVoucher(voucherDTO);
        PaymentDTO payment2 = new PaymentDTO();
        payment2.setVoucher(voucherDTO);

        VisitDTO inputDTO = buildMinimalVisitDTO();
        inputDTO.setPayments(List.of(payment1, payment2));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
            when(appSettingsRepo.getSettings()).thenReturn(appSettings);
            when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
            when(visitRepo.save(any())).thenReturn(savedVisit);
            doThrow(new IllegalStateException("Using same Voucher twice as Payment method is forbidden!"))
                    .when(paymentService).processPayments(any());

            assertThrows(IllegalStateException.class,
                    () -> visitService.createVisit(inputDTO));
        }
    }

    @Test
    void createVisit_shouldReturnVisitDTO_whenVisitIsValid() {
        VisitDTO inputDTO = buildMinimalVisitDTO();

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
            when(appSettingsRepo.getSettings()).thenReturn(appSettings);
            when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
            when(visitRepo.save(any())).thenReturn(savedVisit);

            VisitDTO result = visitService.createVisit(inputDTO);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            verify(visitRepo, times(2)).save(any());
        }
    }

    private SaleDTO saleWithItem(double price) {
        SaleItemDTO item = new SaleItemDTO();
        item.setPrice(BigDecimal.valueOf(price));
        SaleDTO sale = new SaleDTO();
        sale.setItems(List.of(item));
        return sale;
    }

    private DebtRedemptionDTO debtRedemption(double value) {
        ClientDebtDTO debt = new ClientDebtDTO();
        debt.setValue(BigDecimal.valueOf(value));
        DebtRedemptionDTO redemption = new DebtRedemptionDTO();
        redemption.setDebtSource(debt);
        return redemption;
    }

    private PaymentDTO payment(double amount) {
        PaymentDTO payment = new PaymentDTO();
        payment.setAmount(BigDecimal.valueOf(amount));
        return payment;
    }

    @Test
    void createVisit_shouldThrowInsufficientPaymentException_whenPaymentsLowerThanSaleAndDebts() {
        VisitDTO inputDTO = buildMinimalVisitDTO();
        inputDTO.setSale(saleWithItem(100.0));
        inputDTO.setDebtRedemptions(List.of(debtRedemption(50.0)));
        inputDTO.setPayments(List.of(payment(100.0))); // 100 < 100 + 50

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
            when(appSettingsRepo.getSettings()).thenReturn(appSettings);
            when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
            when(visitRepo.save(any())).thenReturn(savedVisit);

            assertThrows(InsufficientPaymentException.class,
                    () -> visitService.createVisit(inputDTO));
            verify(visitRepo, times(1)).save(any());
        }
    }

    @Test
    void createVisit_shouldSucceed_whenPaymentsEqualSaleAndDebts() {
        VisitDTO inputDTO = buildMinimalVisitDTO();
        inputDTO.setSale(saleWithItem(100.0));
        inputDTO.setDebtRedemptions(List.of(debtRedemption(50.0)));
        inputDTO.setPayments(List.of(payment(150.0))); // 150 == 100 + 50

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
            when(appSettingsRepo.getSettings()).thenReturn(appSettings);
            when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
            when(visitRepo.save(any())).thenReturn(savedVisit);

            VisitDTO result = visitService.createVisit(inputDTO);

            assertNotNull(result);
            verify(visitRepo, times(2)).save(any());
        }
    }

    @Test
    void createVisit_shouldSucceed_whenOnlyServiceIsUnderpaid() {
        // Services are NOT counted - only sale items and redeemed debts must be covered.
        VisitDTO inputDTO = buildMinimalVisitDTO(); // one service item, no sale, no debts
        inputDTO.setPayments(List.of(payment(10.0))); // below service price, still valid

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
            when(appSettingsRepo.getSettings()).thenReturn(appSettings);
            when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
            when(visitRepo.save(any())).thenReturn(savedVisit);

            VisitDTO result = visitService.createVisit(inputDTO);

            assertNotNull(result);
            verify(visitRepo, times(2)).save(any());
        }
    }

    @Test
    void deleteVisitById_shouldThrowResourceNotFoundException_whenVisitNotFound() {
        when(visitRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> visitService.deleteVisitById(99L));

        verify(visitRepo, never()).deleteById(any());
        verify(namedInvoiceRepo, never()).existsBySourceVisitId(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void deleteVisitById_shouldDeleteVisit_whenVisitIsValid() {
        Visit visitToDelete = Visit.builder()
                .id(1L).client(client).employee(employee)
                .date(LocalDate.now())
                .absence(false).isBoost(false).isVip(false)
                .paymentStatus(PaymentStatus.PAID)
                .totalValue(BigDecimal.ZERO).totalNet(BigDecimal.ZERO).totalVat(BigDecimal.ZERO)
                .createdByUserId(1L)
                .payments(new ArrayList<>())
                .serviceDiscounts(new ArrayList<>())
                .debtRedemptions(new ArrayList<>())
                .items(new ArrayList<>())
                .build();

        when(visitRepo.findOneById(1L)).thenReturn(Optional.of(visitToDelete));
        when(namedInvoiceRepo.existsBySourceVisitId(1L)).thenReturn(false);

        visitService.deleteVisitById(1L);

        verify(saleService).undoSale(visitToDelete);
        verify(paymentService).removePayments(visitToDelete);
        verify(visitRepo).deleteById(1L);
        verify(auditLogService).logDelete(eq("Visit"), eq(1L), any(), any());
    }

    @Test
    void deleteVisitById_shouldThrowConflictException_whenNamedInvoiceExists() {
        Visit visitWithInvoice = Visit.builder()
                .id(1L).client(client).employee(employee)
                .date(LocalDate.now())
                .absence(false).isBoost(false).isVip(false)
                .paymentStatus(PaymentStatus.PAID)
                .totalValue(BigDecimal.ZERO).totalNet(BigDecimal.ZERO).totalVat(BigDecimal.ZERO)
                .createdByUserId(1L)
                .payments(new ArrayList<>())
                .serviceDiscounts(new ArrayList<>())
                .debtRedemptions(new ArrayList<>())
                .items(new ArrayList<>())
                .build();

        when(visitRepo.findOneById(1L)).thenReturn(Optional.of(visitWithInvoice));
        when(namedInvoiceRepo.existsBySourceVisitId(1L)).thenReturn(true);

        ConflictException ex = assertThrows(ConflictException.class,
                () -> visitService.deleteVisitById(1L));

        assertEquals("Nie można usunąć wizyty z wystawioną fakturą imienną. Najpierw usuń fakturę.", ex.getMessage());
        verify(ownershipService).checkOwnershipOrAdmin(1L);
        verify(visitRepo, never()).deleteById(any());
        verify(clientRepo, never()).save(any());
        verifyNoInteractions(saleService, paymentService, debtService, discountService, auditLogService);
    }

    @Test
    void deleteVisitById_shouldNotCheckNamedInvoice_whenOwnershipCheckFails() {
        when(visitRepo.findOneById(1L)).thenReturn(Optional.of(savedVisit));
        doThrow(new AccessDeniedException("Brak dostępu"))
                .when(ownershipService).checkOwnershipOrAdmin(any());

        assertThrows(AccessDeniedException.class,
                () -> visitService.deleteVisitById(1L));

        verify(namedInvoiceRepo, never()).existsBySourceVisitId(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void deleteVisitById_shouldRethrowAccessDeniedException_whenOwnershipCheckFails() {
        when(visitRepo.findOneById(1L)).thenReturn(Optional.of(savedVisit));
        doThrow(new AccessDeniedException("Brak dostępu"))
                .when(ownershipService).checkOwnershipOrAdmin(any());

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> visitService.deleteVisitById(1L));
        assertEquals("Brak dostępu", ex.getMessage());

        verify(visitRepo, never()).deleteById(any());
    }

    @Test
    void deleteVisitById_shouldThrowIllegalStateException_whenSaleVoucherAlreadyUsed() {
        Voucher usedVoucher = Voucher.builder()
                .id(1L).status(VoucherStatus.USED)
                .expiryDate(LocalDate.now().plusMonths(1))
                .client(client).build();

        SaleItem saleItem = SaleItem.builder()
                .id(1L).voucher(usedVoucher).build();

        Sale sale = Sale.builder()
                .id(1L).items(new ArrayList<>(List.of(saleItem))).build();

        Visit visitWithUsedVoucher = Visit.builder()
                .id(1L).client(client).employee(employee)
                .date(LocalDate.now())
                .absence(false).isBoost(false).isVip(false)
                .totalValue(BigDecimal.ZERO).paymentStatus(PaymentStatus.PAID)
                .sale(sale)
                .payments(new ArrayList<>())
                .serviceDiscounts(new ArrayList<>())
                .debtRedemptions(new ArrayList<>())
                .items(new ArrayList<>())
                .createdByUserId(1L)
                .build();

        when(visitRepo.findOneById(1L)).thenReturn(Optional.of(visitWithUsedVoucher));
        doThrow(new IllegalStateException("Voucher from this Visit was used as Payment method"))
                .when(saleService).undoSale(any());

        assertThrows(IllegalStateException.class,
                () -> visitService.deleteVisitById(1L));

        verify(visitRepo, never()).deleteById(any());
    }

    @Test
    void deleteVisitById_shouldThrowIllegalStateException_whenDebtAlreadyRedeemed() {
        Visit visitWithAbsence = Visit.builder()
                .id(1L).client(client).employee(employee)
                .date(LocalDate.now())
                .absence(true).isBoost(false).isVip(false)
                .totalValue(BigDecimal.valueOf(50.0)).paymentStatus(PaymentStatus.UNPAID)
                .createdByUserId(1L)
                .payments(new ArrayList<>())
                .serviceDiscounts(new ArrayList<>())
                .debtRedemptions(new ArrayList<>())
                .items(new ArrayList<>())
                .build();

        when(visitRepo.findOneById(1L)).thenReturn(Optional.of(visitWithAbsence));
        doThrow(new IllegalStateException("debt already redeemed")).when(debtService).checkIfDebtIsPaid(any());

        assertThrows(IllegalStateException.class,
                () -> visitService.deleteVisitById(1L));

        verify(visitRepo, never()).deleteById(any());
    }

    @Test
    void deleteVisitById_shouldThrowResourceNotFoundException_whenClientDebtNotFoundForAbsenceFee() {
        Visit visitWithAbsence = Visit.builder()
                .id(1L).client(client).employee(employee)
                .date(LocalDate.now())
                .absence(true).isBoost(false).isVip(false)
                .totalValue(BigDecimal.valueOf(50.0)).paymentStatus(PaymentStatus.UNPAID)
                .createdByUserId(1L)
                .payments(new ArrayList<>())
                .serviceDiscounts(new ArrayList<>())
                .debtRedemptions(new ArrayList<>())
                .items(new ArrayList<>())
                .build();

        when(visitRepo.findOneById(1L)).thenReturn(Optional.of(visitWithAbsence));
        doThrow(new ResourceNotFoundException("debt not found")).when(debtService).undoAbsenceFeeOrPartialPaymentFee(any());

        assertThrows(ResourceNotFoundException.class,
                () -> visitService.deleteVisitById(1L));

        verify(visitRepo, never()).deleteById(any());
    }

    @Test
    void getVisitsByClientId_shouldThrowResourceNotFoundException_whenClientNotFound() {
        when(clientRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> visitService.getVisitsByClientId(99L, PageRequest.of(0, 30)));

        verify(visitRepo, never()).findAllByClientId(any(), any());
    }

    @Test
    void getVisitsByClientId_shouldMapVisitsToDTOs_andForwardPageableToRepo() {
        Pageable pageable = PageRequest.of(1, 15);
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(visitRepo.findAllByClientId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(savedVisit), pageable, 40));

        Page<VisitDTO> result = visitService.getVisitsByClientId(1L, pageable);

        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getContent().getFirst().getId());
        assertEquals(40, result.getTotalElements());
        assertFalse(result.isLast());
        verify(visitRepo).findAllByClientId(1L, pageable);
    }

    @Test
    void getVisitsByClientId_shouldReturnEmptyPage_whenClientHasNoVisits() {
        Pageable pageable = PageRequest.of(0, 30);
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(visitRepo.findAllByClientId(1L, pageable)).thenReturn(Page.empty(pageable));

        Page<VisitDTO> result = visitService.getVisitsByClientId(1L, pageable);

        assertTrue(result.isEmpty());
    }

    private VisitInvoiceIdProjection visitInvoiceId(Long visitId, Long invoiceId) {
        VisitInvoiceIdProjection projection = mock(VisitInvoiceIdProjection.class);
        lenient().when(projection.getVisitId()).thenReturn(visitId);
        lenient().when(projection.getInvoiceId()).thenReturn(invoiceId);
        return projection;
    }

    private Visit visitWithId(Long id) {
        return Visit.builder()
                .id(id).client(client).employee(employee)
                .date(LocalDate.now())
                .absence(false).isBoost(false).isVip(false).receipt(true)
                .paymentStatus(PaymentStatus.PAID)
                .totalValue(BigDecimal.ZERO).totalNet(BigDecimal.ZERO).totalVat(BigDecimal.ZERO)
                .createdByUserId(1L)
                .payments(new ArrayList<>())
                .serviceDiscounts(new ArrayList<>())
                .debtRedemptions(new ArrayList<>())
                .items(new ArrayList<>())
                .build();
    }

    @Test
    void getVisitById_shouldSetNamedInvoiceId_whenInvoiceExists() {
        when(visitRepo.findOneById(1L)).thenReturn(Optional.of(savedVisit));
        List<VisitInvoiceIdProjection> rows = List.of(visitInvoiceId(1L, 77L));
        when(namedInvoiceRepo.findInvoiceIdsByVisitIds(List.of(1L))).thenReturn(rows);

        VisitDTO result = visitService.getVisitById(1L);

        assertEquals(77L, result.getNamedInvoiceId());
    }

    @Test
    void getVisitById_shouldLeaveNamedInvoiceIdNull_whenNoInvoiceExists() {
        when(visitRepo.findOneById(1L)).thenReturn(Optional.of(savedVisit));
        when(namedInvoiceRepo.findInvoiceIdsByVisitIds(List.of(1L))).thenReturn(List.of());

        VisitDTO result = visitService.getVisitById(1L);

        assertNull(result.getNamedInvoiceId());
    }

    @Test
    void getVisitsByClientId_shouldResolveNamedInvoiceIds_withSingleRepoCall() {
        Pageable pageable = PageRequest.of(0, 30);
        List<Visit> visits = List.of(visitWithId(1L), visitWithId(2L), visitWithId(3L));

        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(visitRepo.findAllByClientId(1L, pageable))
                .thenReturn(new PageImpl<>(visits, pageable, 3));
        List<VisitInvoiceIdProjection> rows = List.of(visitInvoiceId(1L, 10L), visitInvoiceId(3L, 30L));
        when(namedInvoiceRepo.findInvoiceIdsByVisitIds(List.of(1L, 2L, 3L))).thenReturn(rows);

        Page<VisitDTO> result = visitService.getVisitsByClientId(1L, pageable);

        assertEquals(10L, result.getContent().get(0).getNamedInvoiceId());
        assertNull(result.getContent().get(1).getNamedInvoiceId());
        assertEquals(30L, result.getContent().get(2).getNamedInvoiceId());
        verify(namedInvoiceRepo, times(1)).findInvoiceIdsByVisitIds(any());
    }

    @Test
    void getVisits_shouldResolveNamedInvoiceIds_withSingleRepoCall() {
        VisitFilterDTO filter = new VisitFilterDTO();
        filter.setYear(LocalDate.now().getYear());
        filter.setMonth(LocalDate.now().getMonthValue());

        List<Visit> visits = List.of(visitWithId(1L), visitWithId(2L));

        when(visitRepo.findAllWithFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(visits, PageRequest.of(0, 30), 2));
        List<VisitInvoiceIdProjection> rows = List.of(visitInvoiceId(2L, 99L));
        when(namedInvoiceRepo.findInvoiceIdsByVisitIds(List.of(1L, 2L))).thenReturn(rows);

        Page<VisitDTO> result = visitService.getVisits(filter, 0, 30);

        assertNull(result.getContent().get(0).getNamedInvoiceId());
        assertEquals(99L, result.getContent().get(1).getNamedInvoiceId());
        verify(namedInvoiceRepo, times(1)).findInvoiceIdsByVisitIds(any());
    }

    @Test
    void getVisitById_shouldReturnNewestInvoiceId_whenVisitHasMultipleInvoices() {
        when(visitRepo.findOneById(1L)).thenReturn(Optional.of(savedVisit));
        List<VisitInvoiceIdProjection> rows =
                List.of(visitInvoiceId(1L, 12L), visitInvoiceId(1L, 45L), visitInvoiceId(1L, 8L));
        when(namedInvoiceRepo.findInvoiceIdsByVisitIds(List.of(1L))).thenReturn(rows);

        VisitDTO result = visitService.getVisitById(1L);

        assertEquals(45L, result.getNamedInvoiceId());
    }

    @Test
    void getVisits_shouldSkipInvoiceLookup_whenPageIsEmpty() {
        VisitFilterDTO filter = new VisitFilterDTO();
        filter.setYear(LocalDate.now().getYear());
        filter.setMonth(LocalDate.now().getMonthValue());

        when(visitRepo.findAllWithFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 30)));

        Page<VisitDTO> result = visitService.getVisits(filter, 0, 30);

        assertTrue(result.isEmpty());
        verify(namedInvoiceRepo, never()).findInvoiceIdsByVisitIds(any());
    }

    @Test
    void createVisit_shouldThrowAccessDeniedException_whenUserMarksVisitWithoutReceipt() {
        authenticateAs(AppRoleHierarchy.USER);
        VisitDTO inputDTO = buildMinimalVisitDTO();
        inputDTO.setReceipt(false);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> visitService.createVisit(inputDTO));

        assertEquals("Brak uprawnień.", ex.getMessage());

        verify(visitRepo, never()).save(any());
        verifyNoInteractions(clientRepo, discountService, debtService, saleService, paymentService, auditLogService);
    }

    @Test
    void getVisitPreview_shouldThrowAccessDeniedException_whenUserMarksVisitWithoutReceipt() {
        authenticateAs(AppRoleHierarchy.USER);
        VisitDTO inputDTO = buildMinimalVisitDTO();
        inputDTO.setReceipt(false);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> visitService.getVisitPreview(inputDTO));

        assertEquals("Brak uprawnień.", ex.getMessage());

        verifyNoInteractions(clientRepo, discountService, debtService, saleService, visitPricingCalculator);
    }

    private List<Visit> createVisitAndCaptureSaves(VisitDTO inputDTO) {
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(appSettingsRepo.getSettings()).thenReturn(appSettings);
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(visitRepo.save(any())).thenReturn(savedVisit);

        visitService.createVisit(inputDTO);

        ArgumentCaptor<Visit> captor = ArgumentCaptor.forClass(Visit.class);
        verify(visitRepo, times(2)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void createVisit_shouldSaveWithReceipt_whenUserSendsNullReceipt() {
        authenticateAs(AppRoleHierarchy.USER);
        VisitDTO inputDTO = buildMinimalVisitDTO();
        inputDTO.setReceipt(null);

        List<Visit> saved = createVisitAndCaptureSaves(inputDTO);

        saved.forEach(visit -> assertTrue(visit.getReceipt()));
    }

    @Test
    void createVisit_shouldSaveWithReceipt_whenUserSendsTrueReceipt() {
        authenticateAs(AppRoleHierarchy.USER);
        VisitDTO inputDTO = buildMinimalVisitDTO();
        inputDTO.setReceipt(true);

        List<Visit> saved = createVisitAndCaptureSaves(inputDTO);

        saved.forEach(visit -> assertTrue(visit.getReceipt()));
    }

    @Test
    void createVisit_shouldSaveWithoutReceipt_whenMasterSendsFalseReceipt() {
        authenticateAs(AppRoleHierarchy.MASTER);
        VisitDTO inputDTO = buildMinimalVisitDTO();
        inputDTO.setReceipt(false);

        List<Visit> saved = createVisitAndCaptureSaves(inputDTO);

        saved.forEach(visit -> assertFalse(visit.getReceipt()));
    }

    @Test
    void createVisit_shouldSaveWithoutReceipt_whenAdminSendsFalseReceipt() {
        authenticateAs(AppRoleHierarchy.ADMIN);
        VisitDTO inputDTO = buildMinimalVisitDTO();
        inputDTO.setReceipt(false);

        List<Visit> saved = createVisitAndCaptureSaves(inputDTO);

        saved.forEach(visit -> assertFalse(visit.getReceipt()));
    }

    @Test
    void createVisit_shouldSaveWithoutReceipt_whenCompanySendsFalseReceipt() {
        authenticateAs(AppRoleHierarchy.COMPANY);
        VisitDTO inputDTO = buildMinimalVisitDTO();
        inputDTO.setReceipt(false);

        List<Visit> saved = createVisitAndCaptureSaves(inputDTO);

        saved.forEach(visit -> assertFalse(visit.getReceipt()));
    }

    @Test
    void getVisitPreview_shouldAllow_whenUserSendsTrueReceipt() {
        authenticateAs(AppRoleHierarchy.USER);
        VisitDTO inputDTO = buildMinimalVisitDTO();
        inputDTO.setReceipt(true);
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(appSettingsRepo.getSettings()).thenReturn(appSettings);

        VisitDTO result = visitService.getVisitPreview(inputDTO);

        assertTrue(result.getReceipt());
    }

    @Test
    void getVisitPreview_shouldKeepReceipt_whenMasterSendsFalseReceipt() {
        authenticateAs(AppRoleHierarchy.MASTER);
        VisitDTO inputDTO = buildMinimalVisitDTO();
        inputDTO.setReceipt(false);
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(appSettingsRepo.getSettings()).thenReturn(appSettings);

        VisitDTO result = visitService.getVisitPreview(inputDTO);

        assertFalse(result.getReceipt());
    }

    private LocalDate[] captureRepoDateRange(VisitFilterDTO filter) {
        when(visitRepo.findAllWithFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 30)));

        visitService.getVisits(filter, 0, 30);

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(visitRepo).findAllWithFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                fromCaptor.capture(), toCaptor.capture(), any(), any(), any(), pageableCaptor.capture());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(30, pageableCaptor.getValue().getPageSize());
        return new LocalDate[]{fromCaptor.getValue(), toCaptor.getValue()};
    }

    private VisitFilterDTO rangeFilter(LocalDate dateFrom, LocalDate dateTo) {
        VisitFilterDTO filter = new VisitFilterDTO();
        filter.setDateFrom(dateFrom);
        filter.setDateTo(dateTo);
        return filter;
    }

    @Test
    void getVisits_shouldPassSingleDay_whenDateRangeIsOneDay() {
        LocalDate day = LocalDate.of(2026, 10, 15);

        LocalDate[] range = captureRepoDateRange(rangeFilter(day, day));

        assertEquals(day, range[0]);
        assertEquals(day, range[1]);
    }

    @Test
    void getVisits_shouldPassExactBounds_whenDateRangeIsWeek() {
        LocalDate[] range = captureRepoDateRange(
                rangeFilter(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18)));

        assertEquals(LocalDate.of(2026, 10, 12), range[0]);
        assertEquals(LocalDate.of(2026, 10, 18), range[1]);
    }

    @Test
    void getVisits_shouldPreferDateRange_overYearAndMonth() {
        VisitFilterDTO filter = rangeFilter(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 18));
        filter.setYear(2026);
        filter.setMonth(2);

        LocalDate[] range = captureRepoDateRange(filter);

        assertEquals(LocalDate.of(2026, 10, 12), range[0]);
        assertEquals(LocalDate.of(2026, 10, 18), range[1]);
    }

    @Test
    void getVisits_shouldUseWholeMonth_whenNoDateRange() {
        VisitFilterDTO filter = new VisitFilterDTO();
        filter.setYear(2026);
        filter.setMonth(2);

        LocalDate[] range = captureRepoDateRange(filter);

        assertEquals(LocalDate.of(2026, 2, 1), range[0]);
        assertEquals(LocalDate.of(2026, 2, 28), range[1]);
    }

    @Test
    void getVisits_shouldAcceptRangeOf31Days() {
        LocalDate[] range = captureRepoDateRange(
                rangeFilter(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)));

        assertEquals(LocalDate.of(2026, 10, 1), range[0]);
        assertEquals(LocalDate.of(2026, 10, 31), range[1]);
    }

    @Test
    void getVisits_shouldThrowValidationException_whenOnlyDateFromGiven() {
        VisitFilterDTO filter = rangeFilter(LocalDate.of(2026, 10, 15), null);
        filter.setYear(2026);
        filter.setMonth(10);

        assertThrows(ValidationException.class, () -> visitService.getVisits(filter, 0, 30));

        verifyNoInteractions(visitRepo);
    }

    @Test
    void getVisits_shouldThrowValidationException_whenOnlyDateToGiven() {
        VisitFilterDTO filter = rangeFilter(null, LocalDate.of(2026, 10, 15));

        assertThrows(ValidationException.class, () -> visitService.getVisits(filter, 0, 30));

        verifyNoInteractions(visitRepo);
    }

    @Test
    void getVisits_shouldThrowValidationException_whenDateFromAfterDateTo() {
        VisitFilterDTO filter = rangeFilter(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 12));

        assertThrows(ValidationException.class, () -> visitService.getVisits(filter, 0, 30));

        verifyNoInteractions(visitRepo);
    }

    @Test
    void getVisits_shouldThrowValidationException_whenRangeIs32Days() {
        VisitFilterDTO filter = rangeFilter(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1));

        assertThrows(ValidationException.class, () -> visitService.getVisits(filter, 0, 30));

        verifyNoInteractions(visitRepo);
    }

    private static final LocalDate VISIT_DAY = LocalDate.of(2026, 10, 15);

    private ClientCardCreatedAtProjection cardRow(Long cardId, Long clientId, LocalDateTime createdAt) {
        ClientCardCreatedAtProjection projection = mock(ClientCardCreatedAtProjection.class);
        lenient().when(projection.getId()).thenReturn(cardId);
        lenient().when(projection.getClientId()).thenReturn(clientId);
        lenient().when(projection.getCreatedAt()).thenReturn(createdAt);
        return projection;
    }

    private TherapyEntryCardDateProjection entryRow(Long cardId, LocalDate entryDate) {
        TherapyEntryCardDateProjection projection = mock(TherapyEntryCardDateProjection.class);
        lenient().when(projection.getCardId()).thenReturn(cardId);
        lenient().when(projection.getEntryDate()).thenReturn(entryDate);
        return projection;
    }

    private void stubCards(ClientCardCreatedAtProjection... rows) {
        when(clientCardRepo.findCreatedAtByClientIdIn(any())).thenReturn(List.of(rows));
    }

    private void stubEntries(TherapyEntryCardDateProjection... rows) {
        when(therapyEntryRepo.findValidEntryDatesByCardIdInAndDateBetween(any(), any(), any()))
                .thenReturn(List.of(rows));
    }

    private Visit visitOn(Long id, Client visitClient, LocalDate date, boolean absence) {
        Visit visit = visitWithId(id);
        visit.setClient(visitClient);
        visit.setDate(date);
        visit.setAbsence(absence);
        return visit;
    }

    private List<VisitDTO> searchVisits(List<Visit> visits) {
        when(visitRepo.findAllWithFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(visits, PageRequest.of(0, 30), visits.size()));
        return visitService.getVisits(rangeFilter(VISIT_DAY.minusDays(10), VISIT_DAY.plusDays(10)), 0, 30)
                .getContent();
    }

    @Test
    void getVisits_shouldMarkMissingTherapyEntry_whenCardExistsAndNoEntry() {
        stubCards(cardRow(10L, 1L, VISIT_DAY.minusDays(30).atTime(9, 0)));
        stubEntries();

        List<VisitDTO> result = searchVisits(List.of(visitOn(1L, client, VISIT_DAY, false)));

        assertEquals(Boolean.TRUE, result.get(0).getMissingTherapyEntry());
    }

    @Test
    void getVisits_shouldNotMarkMissingTherapyEntry_whenValidEntryExistsThatDay() {
        stubCards(cardRow(10L, 1L, VISIT_DAY.minusDays(30).atTime(9, 0)));
        stubEntries(entryRow(10L, VISIT_DAY));

        List<VisitDTO> result = searchVisits(List.of(visitOn(1L, client, VISIT_DAY, false)));

        assertEquals(Boolean.FALSE, result.get(0).getMissingTherapyEntry());
    }

    @Test
    void getVisits_shouldMarkMissingTherapyEntry_whenEntryThatDayIsOnlyInvalidated() {
        stubCards(cardRow(10L, 1L, VISIT_DAY.minusDays(30).atTime(9, 0)));
        stubEntries(entryRow(10L, VISIT_DAY.minusDays(1)));

        List<VisitDTO> result = searchVisits(List.of(visitOn(1L, client, VISIT_DAY, false)));

        assertEquals(Boolean.TRUE, result.get(0).getMissingTherapyEntry());
    }

    @Test
    void getVisits_shouldNotMarkMissingTherapyEntry_whenClientHasNoCard() {
        stubCards();

        List<VisitDTO> result = searchVisits(List.of(visitOn(1L, client, VISIT_DAY, false)));

        assertEquals(Boolean.FALSE, result.get(0).getMissingTherapyEntry());
        verifyNoInteractions(therapyEntryRepo);
    }

    @Test
    void getVisits_shouldNotMarkMissingTherapyEntry_whenVisitIsDayBeforeCardCreation() {
        stubCards(cardRow(10L, 1L, VISIT_DAY.plusDays(1).atTime(0, 5)));

        List<VisitDTO> result = searchVisits(List.of(visitOn(1L, client, VISIT_DAY, false)));

        assertEquals(Boolean.FALSE, result.get(0).getMissingTherapyEntry());
        verifyNoInteractions(therapyEntryRepo);
    }

    @Test
    void getVisits_shouldMarkMissingTherapyEntry_whenVisitIsOnCardCreationDayWithoutEntry() {
        stubCards(cardRow(10L, 1L, VISIT_DAY.atTime(18, 30)));
        stubEntries();

        List<VisitDTO> result = searchVisits(List.of(visitOn(1L, client, VISIT_DAY, false)));

        assertEquals(Boolean.TRUE, result.get(0).getMissingTherapyEntry());
    }

    @Test
    void getVisits_shouldNotMarkMissingTherapyEntry_whenVisitIsAbsence() {
        List<VisitDTO> result = searchVisits(List.of(visitOn(1L, client, VISIT_DAY, true)));

        assertEquals(Boolean.FALSE, result.get(0).getMissingTherapyEntry());
        verifyNoInteractions(clientCardRepo, therapyEntryRepo);
    }

    @Test
    void getVisits_shouldNotMarkAnyVisit_whenOneValidEntryCoversSameDay() {
        stubCards(cardRow(10L, 1L, VISIT_DAY.minusDays(30).atTime(9, 0)));
        stubEntries(entryRow(10L, VISIT_DAY));

        List<VisitDTO> result = searchVisits(List.of(
                visitOn(1L, client, VISIT_DAY, false),
                visitOn(2L, client, VISIT_DAY, false)));

        assertEquals(Boolean.FALSE, result.get(0).getMissingTherapyEntry());
        assertEquals(Boolean.FALSE, result.get(1).getMissingTherapyEntry());
    }

    @Test
    void getVisits_shouldSkipTherapyLookups_whenPageIsEmpty() {
        when(visitRepo.findAllWithFilters(any(), any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 30)));

        Page<VisitDTO> result = visitService.getVisits(rangeFilter(VISIT_DAY, VISIT_DAY), 0, 30);

        assertTrue(result.isEmpty());
        verifyNoInteractions(clientCardRepo, therapyEntryRepo);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getVisits_shouldResolveMissingTherapyEntries_withOneQueryPerRepo_forWholePage() {
        Client second = Client.builder().id(2L).firstName("Ola").lastName("Nowak").boostClient(false).build();
        Client third = Client.builder().id(3L).firstName("Ewa").lastName("Lis").boostClient(false).build();
        stubCards(
                cardRow(10L, 1L, VISIT_DAY.minusDays(30).atTime(9, 0)),
                cardRow(20L, 2L, VISIT_DAY.minusDays(30).atTime(9, 0)));
        stubEntries(entryRow(10L, VISIT_DAY.minusDays(3)), entryRow(20L, VISIT_DAY));

        List<VisitDTO> result = searchVisits(List.of(
                visitOn(1L, client, VISIT_DAY, false),
                visitOn(2L, client, VISIT_DAY.minusDays(3), false),
                visitOn(3L, second, VISIT_DAY, false),
                visitOn(4L, second, VISIT_DAY.plusDays(2), false),
                visitOn(5L, third, VISIT_DAY, false),
                visitOn(6L, client, VISIT_DAY.minusDays(5), true)));

        assertEquals(List.of(true, false, false, true, false, false),
                result.stream().map(VisitDTO::getMissingTherapyEntry).toList());

        ArgumentCaptor<Collection<Long>> clientIdsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(clientCardRepo, times(1)).findCreatedAtByClientIdIn(clientIdsCaptor.capture());
        assertEquals(Set.of(1L, 2L, 3L), new HashSet<>(clientIdsCaptor.getValue()));

        ArgumentCaptor<Collection<Long>> cardIdsCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(therapyEntryRepo, times(1)).findValidEntryDatesByCardIdInAndDateBetween(
                cardIdsCaptor.capture(), eq(VISIT_DAY.minusDays(3)), eq(VISIT_DAY.plusDays(2)));
        assertEquals(Set.of(10L, 20L), new HashSet<>(cardIdsCaptor.getValue()));
    }

    @Test
    void getVisitsByClientId_shouldLeaveMissingTherapyEntryNull() {
        Pageable pageable = PageRequest.of(0, 30);
        when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
        when(visitRepo.findAllByClientId(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(visitWithId(1L)), pageable, 1));

        Page<VisitDTO> result = visitService.getVisitsByClientId(1L, pageable);

        assertNull(result.getContent().get(0).getMissingTherapyEntry());
        verifyNoInteractions(clientCardRepo, therapyEntryRepo);
    }

    @Test
    void getVisitById_shouldLeaveMissingTherapyEntryNull() {
        when(visitRepo.findOneById(1L)).thenReturn(Optional.of(savedVisit));

        VisitDTO result = visitService.getVisitById(1L);

        assertNull(result.getMissingTherapyEntry());
        verifyNoInteractions(clientCardRepo, therapyEntryRepo);
    }
}

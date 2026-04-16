package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.*;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.*;
import com.clinic.clinicmanager.model.constants.*;
import com.clinic.clinicmanager.repo.*;
import com.clinic.clinicmanager.service.impl.OwnershipService;
import com.clinic.clinicmanager.service.impl.VisitServiceImpl;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class VisitServiceImplTest {

    @Mock VisitRepo visitRepo;
    @Mock ClientRepo clientRepo;
    @Mock EmployeeRepo employeeRepo;
    @Mock ReviewRepo reviewRepo;
    @Mock AppSettingsRepo appSettingsRepo;
    @Mock DiscountRepo discountRepo;
    @Mock ClientDebtRepo clientDebtRepo;
    @Mock DebtRedemptionRepo debtRedemptionRepo;
    @Mock VoucherRepo voucherRepo;
    @Mock PaymentRepo paymentRepo;
    @Mock ProductRepo productRepo;
    @Mock BaseServiceRepo baseServiceRepo;
    @Mock BaseServiceVariantRepo baseServiceVariantRepo;
    @Mock SaleItemRepo saleItemRepo;
    @Mock AuditLogService auditLogService;
    @Mock OwnershipService ownershipService;

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
                .id(1L).name("Konsultacja").price(108.0).duration(30).build();

        savedVisit = Visit.builder()
                .id(1L).client(client).employee(employee)
                .date(LocalDate.now())
                .absence(false).isBoost(false).isVip(false).receipt(true)
                .paymentStatus(PaymentStatus.PAID)
                .totalValue(0.0).totalNet(0.0).totalVat(0.0)
                .createdByUserId(1L)
                .payments(new ArrayList<>())
                .serviceDiscounts(new ArrayList<>())
                .debtRedemptions(new ArrayList<>())
                .items(new ArrayList<>())
                .build();

        lenient().when(baseServiceRepo.findOneById(1L)).thenReturn(Optional.of(baseService));
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
        serviceDTO.setPrice(100.0);
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
                .id(1L).name("Wariant").price(50.0).duration(15).build();

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
                .sellingPrice(50.0)
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
            when(productRepo.findOneById(2L)).thenReturn(Optional.of(product));

            assertThrows(IllegalStateException.class,
                    () -> visitService.createVisit(inputDTO));

            verify(productRepo, never()).save(any(Product.class));
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

        Voucher voucher = Voucher.builder()
                .id(1L).status(VoucherStatus.ACTIVE)
                .expiryDate(LocalDate.now().plusMonths(1))
                .value(50.0).build();

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(1L);

            when(clientRepo.findOneById(1L)).thenReturn(Optional.of(client));
            when(appSettingsRepo.getSettings()).thenReturn(appSettings);
            when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
            when(visitRepo.save(any())).thenReturn(savedVisit);
            when(voucherRepo.findOneById(1L)).thenReturn(Optional.of(voucher));

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
            verify(visitRepo, times(3)).save(any());
        }
    }

    @Test
    void deleteVisitById_shouldThrowResourceNotFoundException_whenVisitNotFound() {
        when(visitRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> visitService.deleteVisitById(99L));

        verify(visitRepo, never()).deleteById(any());
    }

    @Test
    void deleteVisitById_shouldDeleteVisit_whenVisitIsValid() {
        Visit visitToDelete = Visit.builder()
                .id(1L).client(client).employee(employee)
                .date(LocalDate.now())
                .absence(false).isBoost(false).isVip(false)
                .paymentStatus(PaymentStatus.PAID)
                .totalValue(0.0).totalNet(0.0).totalVat(0.0)
                .createdByUserId(1L)
                .payments(new ArrayList<>())
                .serviceDiscounts(new ArrayList<>())
                .debtRedemptions(new ArrayList<>())
                .items(new ArrayList<>())
                .build();

        when(visitRepo.findOneById(1L)).thenReturn(Optional.of(visitToDelete));

        visitService.deleteVisitById(1L);

        verify(visitRepo).deleteById(1L);
        verify(auditLogService).logDelete(any(), any(), any(), any());
    }

    @Test
    void deleteVisitById_shouldThrowDeletionException_whenOwnershipCheckFails() {
        when(visitRepo.findOneById(1L)).thenReturn(Optional.of(savedVisit));
        doThrow(new AccessDeniedException("Brak dostępu"))
                .when(ownershipService).checkOwnershipOrAdmin(any());

        assertThrows(DeletionException.class,
                () -> visitService.deleteVisitById(1L));

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
                .totalValue(0.0).paymentStatus(PaymentStatus.PAID)
                .sale(sale)
                .payments(new ArrayList<>())
                .serviceDiscounts(new ArrayList<>())
                .debtRedemptions(new ArrayList<>())
                .items(new ArrayList<>())
                .createdByUserId(1L)
                .build();

        when(visitRepo.findOneById(1L)).thenReturn(Optional.of(visitWithUsedVoucher));
        when(voucherRepo.findOneById(1L)).thenReturn(Optional.of(usedVoucher));
        when(visitRepo.findByVoucherId(1L)).thenReturn(Optional.of(visitWithUsedVoucher));

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
                .totalValue(50.0).paymentStatus(PaymentStatus.UNPAID)
                .createdByUserId(1L)
                .payments(new ArrayList<>())
                .serviceDiscounts(new ArrayList<>())
                .debtRedemptions(new ArrayList<>())
                .items(new ArrayList<>())
                .build();

        Visit paidByVisit = Visit.builder().id(2L).build();

        ClientDebt debt = ClientDebt.builder()
                .id(1L).type(DebtType.ABSENCE_FEE).value(50.0).build();

        when(visitRepo.findOneById(1L)).thenReturn(Optional.of(visitWithAbsence));
        when(visitRepo.findByDebtSourceVisitId(1L)).thenReturn(Optional.of(paidByVisit));
        when(clientDebtRepo.findOneBySourceVisitId(1L)).thenReturn(Optional.of(debt));

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
                .totalValue(50.0).paymentStatus(PaymentStatus.UNPAID)
                .createdByUserId(1L)
                .payments(new ArrayList<>())
                .serviceDiscounts(new ArrayList<>())
                .debtRedemptions(new ArrayList<>())
                .items(new ArrayList<>())
                .build();

        when(visitRepo.findOneById(1L)).thenReturn(Optional.of(visitWithAbsence));
        when(visitRepo.findByDebtSourceVisitId(1L)).thenReturn(Optional.empty());
        when(clientDebtRepo.findOneBySourceVisitId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> visitService.deleteVisitById(1L));

        verify(visitRepo, never()).deleteById(any());
    }
}

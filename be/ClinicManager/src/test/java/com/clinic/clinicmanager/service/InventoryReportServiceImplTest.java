package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.EmployeeSummaryDTO;
import com.clinic.clinicmanager.DTO.InventoryReportDTO;
import com.clinic.clinicmanager.DTO.InventoryReportItemDTO;
import com.clinic.clinicmanager.DTO.ProductDTO;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.InventoryReport;
import com.clinic.clinicmanager.model.InventoryReportItem;
import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.model.constants.EmploymentType;
import com.clinic.clinicmanager.model.constants.RoleType;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.repo.InventoryReportRepo;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.service.impl.InventoryReportServiceImpl;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryReportServiceImplTest {

    @Mock InventoryReportRepo inventoryReportRepo;
    @Mock EmployeeRepo employeeRepo;
    @Mock ProductRepo productRepo;
    @Mock AuditLogService auditLogService;

    @InjectMocks
    InventoryReportServiceImpl inventoryReportService;

    private Employee employee;
    private Product product;
    private InventoryReport report;
    private UserDetailsImpl userDetails;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
                .id(1L).name("Anna").lastName("Kowalska")
                .isDeleted(false).employmentType(EmploymentType.FULL)
                .bonusPercent(0.0).saleBonusPercent(0.0).build();

        product = buildProduct(10L, "Krem", 20);

        report = InventoryReport.builder()
                .id(5L)
                .createdBy(employee)
                .createdAt(LocalDate.now())
                .approved(false)
                .build();

        EmployeeSummaryDTO employeeSummary = new EmployeeSummaryDTO(employee);
        userDetails = mock(UserDetailsImpl.class);
        lenient().when(userDetails.getEmployee()).thenReturn(employeeSummary);
    }

    private Product buildProduct(Long id, String name, int supply) {
        ProductCategory category = ProductCategory.builder().id(1L).name("Produkty").color("#000000").build();
        Brand brand = Brand.builder().id(1L).name("TestBrand").build();
        return Product.builder()
                .id(id).name(name).supply(supply).isDeleted(false)
                .category(category).brand(brand).build();
    }

    @Test
    void getReportById_shouldReturnDTO_whenReportExists() {
        when(inventoryReportRepo.findOneByIdWithDetails(5L)).thenReturn(Optional.of(report));

        InventoryReportDTO result = inventoryReportService.getReportById(5L);

        assertEquals(5L, result.getId());
    }

    @Test
    void getReportById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(inventoryReportRepo.findOneByIdWithDetails(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> inventoryReportService.getReportById(99L));
    }

    @Test
    void getReports_shouldHandleNullFilter_withDefaultDates() {
        Page<InventoryReport> emptyPage = new PageImpl<>(List.of());
        when(inventoryReportRepo.findAllWithFilters(isNull(), any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(emptyPage);

        Page<InventoryReportDTO> result = inventoryReportService.getReports(null, 0, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void getReports_shouldUseDateRangeForWholeYear_whenYearSetNoMonth() {
        var filter = new com.clinic.clinicmanager.DTO.request.InventoryReportFilterDTO();
        filter.setYear(2025);
        Page<InventoryReport> emptyPage = new PageImpl<>(List.of());
        when(inventoryReportRepo.findAllWithFilters(isNull(), eq(LocalDate.of(2025, 1, 1)), eq(LocalDate.of(2025, 12, 31)), any(Pageable.class)))
                .thenReturn(emptyPage);

        inventoryReportService.getReports(filter, 0, 10);

        verify(inventoryReportRepo).findAllWithFilters(isNull(), eq(LocalDate.of(2025, 1, 1)), eq(LocalDate.of(2025, 12, 31)), any(Pageable.class));
    }

    @Test
    void getReports_shouldUseDateRangeForSingleMonth_whenYearAndMonthSet() {
        var filter = new com.clinic.clinicmanager.DTO.request.InventoryReportFilterDTO();
        filter.setYear(2025);
        filter.setMonth(4);
        Page<InventoryReport> emptyPage = new PageImpl<>(List.of());
        when(inventoryReportRepo.findAllWithFilters(isNull(), eq(LocalDate.of(2025, 4, 1)), eq(LocalDate.of(2025, 4, 30)), any(Pageable.class)))
                .thenReturn(emptyPage);

        inventoryReportService.getReports(filter, 0, 10);

        verify(inventoryReportRepo).findAllWithFilters(isNull(), eq(LocalDate.of(2025, 4, 1)), eq(LocalDate.of(2025, 4, 30)), any(Pageable.class));
    }

    @Test
    void createReport_shouldThrowConflictException_whenUnapprovedReportExists() {
        when(inventoryReportRepo.existsByApprovedFalse()).thenReturn(true);

        InventoryReportDTO inputDTO = new InventoryReportDTO();
        inputDTO.setItems(List.of());

        assertThrows(ConflictException.class, () -> inventoryReportService.createReport(inputDTO));
    }

    @Test
    void createReport_shouldThrowResourceNotFoundException_whenNoEmployeeInSession() {
        when(inventoryReportRepo.existsByApprovedFalse()).thenReturn(false);

        InventoryReportDTO inputDTO = new InventoryReportDTO();
        inputDTO.setItems(List.of());

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);

            assertThrows(ResourceNotFoundException.class, () -> inventoryReportService.createReport(inputDTO));
        }
    }

    @Test
    void createReport_shouldSaveReportAndUpdateProductSupply() {
        when(inventoryReportRepo.existsByApprovedFalse()).thenReturn(false);
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(productRepo.findByIdNotDeleted(10L)).thenReturn(Optional.of(product));
        when(inventoryReportRepo.save(any(InventoryReport.class))).thenReturn(report);

        InventoryReportDTO inputDTO = new InventoryReportDTO();
        ProductDTO productRef = new ProductDTO();
        productRef.setId(10L);
        InventoryReportItemDTO itemDTO = new InventoryReportItemDTO();
        itemDTO.setProduct(productRef);
        itemDTO.setSupplyAfter(15);
        inputDTO.setItems(List.of(itemDTO));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserDetailsFromSession).thenReturn(userDetails);

            InventoryReportDTO result = inventoryReportService.createReport(inputDTO);

            assertEquals(15, product.getSupply());
            verify(productRepo).save(product);
            verify(inventoryReportRepo).save(any(InventoryReport.class));
            assertNotNull(result);
        }
    }

    @Test
    void createReport_shouldClampNegativeSupplyAfterToZero() {
        when(inventoryReportRepo.existsByApprovedFalse()).thenReturn(false);
        when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee));
        when(productRepo.findByIdNotDeleted(10L)).thenReturn(Optional.of(product));
        when(inventoryReportRepo.save(any(InventoryReport.class))).thenReturn(report);

        InventoryReportDTO inputDTO = new InventoryReportDTO();
        ProductDTO productRef = new ProductDTO();
        productRef.setId(10L);
        InventoryReportItemDTO itemDTO = new InventoryReportItemDTO();
        itemDTO.setProduct(productRef);
        itemDTO.setSupplyAfter(-5);
        inputDTO.setItems(List.of(itemDTO));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserDetailsFromSession).thenReturn(userDetails);

            inventoryReportService.createReport(inputDTO);

            assertEquals(0, product.getSupply());
        }
    }

    @Test
    void updateReport_shouldThrowConflictException_whenReportIsApproved() {
        report.setApproved(true);
        when(inventoryReportRepo.findOneByIdWithDetails(5L)).thenReturn(Optional.of(report));

        InventoryReportDTO inputDTO = new InventoryReportDTO();
        inputDTO.setItems(List.of());

        assertThrows(ConflictException.class, () -> inventoryReportService.updateReport(5L, inputDTO));
    }

    @Test
    void updateReport_shouldThrowConflictException_whenReportOlderThan30Days() {
        report.setCreatedAt(LocalDate.now().minusDays(31));
        when(inventoryReportRepo.findOneByIdWithDetails(5L)).thenReturn(Optional.of(report));

        InventoryReportDTO inputDTO = new InventoryReportDTO();
        inputDTO.setItems(List.of());

        assertThrows(ConflictException.class, () -> inventoryReportService.updateReport(5L, inputDTO));
    }

    @Test
    void updateReport_shouldThrowResourceNotFoundException_whenReportNotFound() {
        when(inventoryReportRepo.findOneByIdWithDetails(99L)).thenReturn(Optional.empty());

        InventoryReportDTO inputDTO = new InventoryReportDTO();
        inputDTO.setItems(List.of());

        assertThrows(ResourceNotFoundException.class, () -> inventoryReportService.updateReport(99L, inputDTO));
    }

    @Test
    void updateReport_shouldRevertOldSupplyAndApplyNew() {
        InventoryReportItem existingItem = InventoryReportItem.builder()
                .id(1L).product(product).supplyBefore(10).supplyAfter(20).build();
        product.setSupply(20);
        report.addItem(existingItem);

        Product newProduct = buildProduct(11L, "Serum", 5);

        when(inventoryReportRepo.findOneByIdWithDetails(5L)).thenReturn(Optional.of(report));
        when(productRepo.findByIdNotDeleted(11L)).thenReturn(Optional.of(newProduct));
        when(inventoryReportRepo.save(any(InventoryReport.class))).thenReturn(report);

        InventoryReportDTO inputDTO = new InventoryReportDTO();
        ProductDTO productRef = new ProductDTO();
        productRef.setId(11L);
        InventoryReportItemDTO itemDTO = new InventoryReportItemDTO();
        itemDTO.setProduct(productRef);
        itemDTO.setSupplyAfter(8);
        inputDTO.setItems(List.of(itemDTO));

        inventoryReportService.updateReport(5L, inputDTO);

        // old product reverted: 20 - (20-10) = 10
        assertEquals(10, product.getSupply());
        // new product: set 8
        assertEquals(8, newProduct.getSupply());
    }

    @Test
    void approveReport_shouldThrowConflictException_whenNotAdmin() {
        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasUserRole(RoleType.ROLE_ADMIN)).thenReturn(false);

            assertThrows(ConflictException.class, () -> inventoryReportService.approveReport(5L));
        }
    }

    @Test
    void approveReport_shouldThrowConflictException_whenAlreadyApproved() {
        report.setApproved(true);
        when(inventoryReportRepo.findOneByIdWithDetails(5L)).thenReturn(Optional.of(report));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasUserRole(RoleType.ROLE_ADMIN)).thenReturn(true);

            assertThrows(ConflictException.class, () -> inventoryReportService.approveReport(5L));
        }
    }

    @Test
    void approveReport_shouldSetApprovedTrue_whenAdminAndReportPending() {
        when(inventoryReportRepo.findOneByIdWithDetails(5L)).thenReturn(Optional.of(report));
        when(inventoryReportRepo.save(report)).thenReturn(report);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(() -> SessionUtils.hasUserRole(RoleType.ROLE_ADMIN)).thenReturn(true);

            inventoryReportService.approveReport(5L);

            assertTrue(report.getApproved());
            verify(inventoryReportRepo).save(report);
        }
    }

    @Test
    void deleteReportById_shouldThrowConflictException_whenApproved() {
        report.setApproved(true);
        when(inventoryReportRepo.findOneByIdWithDetails(5L)).thenReturn(Optional.of(report));

        assertThrows(ConflictException.class, () -> inventoryReportService.deleteReportById(5L));
    }

    @Test
    void deleteReportById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(inventoryReportRepo.findOneByIdWithDetails(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> inventoryReportService.deleteReportById(99L));
    }

    @Test
    void deleteReportById_shouldRevertSupplyAndDelete() {
        InventoryReportItem item = InventoryReportItem.builder()
                .id(1L).product(product).supplyBefore(10).supplyAfter(20).build();
        product.setSupply(20);
        report.addItem(item);

        when(inventoryReportRepo.findOneByIdWithDetails(5L)).thenReturn(Optional.of(report));

        inventoryReportService.deleteReportById(5L);

        assertEquals(10, product.getSupply());
        verify(inventoryReportRepo).delete(report);
        verify(auditLogService).logDelete(eq("InventoryReport"), eq(5L), anyString(), any());
    }

    @Test
    void areAllApproved_shouldReturnTrue_whenNoUnapprovedReports() {
        when(inventoryReportRepo.existsByApprovedFalse()).thenReturn(false);

        assertTrue(inventoryReportService.areAllApproved());
    }

    @Test
    void areAllApproved_shouldReturnFalse_whenUnapprovedReportExists() {
        when(inventoryReportRepo.existsByApprovedFalse()).thenReturn(true);

        assertFalse(inventoryReportService.areAllApproved());
    }
}

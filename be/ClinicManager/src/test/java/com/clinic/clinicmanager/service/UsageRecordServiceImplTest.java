package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.EmployeeSummaryDTO;
import com.clinic.clinicmanager.DTO.ProductDTO;
import com.clinic.clinicmanager.DTO.UsageRecordDTO;
import com.clinic.clinicmanager.exceptions.InsufficientSupplyException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.Brand;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.model.Product;
import com.clinic.clinicmanager.model.ProductCategory;
import com.clinic.clinicmanager.model.UsageRecord;
import com.clinic.clinicmanager.model.constants.UsageReason;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.repo.ProductRepo;
import com.clinic.clinicmanager.repo.UsageRecordRepo;
import com.clinic.clinicmanager.service.impl.OwnershipService;
import com.clinic.clinicmanager.service.impl.UsageRecordServiceImpl;
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
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsageRecordServiceImplTest {

    @Mock UsageRecordRepo usageRecordRepo;
    @Mock ProductRepo productRepo;
    @Mock EmployeeRepo employeeRepo;
    @Mock AuditLogService auditLogService;
    @Mock OwnershipService ownershipService;

    @InjectMocks
    UsageRecordServiceImpl usageRecordService;

    private Product product;
    private Employee employee;
    private UsageRecord usageRecord;

    @BeforeEach
    void setUp() {
        ProductCategory category = ProductCategory.builder().id(1L).name("Narzędzia").build();
        Brand brand = Brand.builder().id(1L).name("TestBrand").build();

        product = Product.builder()
                .id(1L).name("Frez").supply(10)
                .category(category).brand(brand)
                .isDeleted(false).build();

        employee = Employee.builder()
                .id(2L).name("Anna").lastName("Nowak").build();

        usageRecord = UsageRecord.builder()
                .id(5L)
                .product(product)
                .employee(employee)
                .usageDate(LocalDate.now())
                .quantity(3)
                .usageReason(UsageReason.REGULAR_USAGE)
                .createdByUserId(9L)
                .build();
    }

    @Test
    void getUsageRecordById_shouldReturnDTO_whenFound() {
        when(usageRecordRepo.findOneById(5L)).thenReturn(Optional.of(usageRecord));

        UsageRecordDTO result = usageRecordService.getUsageRecordById(5L);

        assertEquals(5L, result.getId());
        assertEquals(1L, result.getProduct().getId());
    }

    @Test
    void getUsageRecordById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(usageRecordRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> usageRecordService.getUsageRecordById(99L));
    }

    @Test
    void getUsageRecords_shouldUseDefaultDates_whenFilterIsNull() {
        Page<UsageRecord> page = new PageImpl<>(List.of(usageRecord));
        when(usageRecordRepo.findAllWithFilters(isNull(), isNull(), isNull(),
                eq(LocalDate.of(1900, 1, 1)), eq(LocalDate.of(2100, 12, 31)), any(Pageable.class)))
                .thenReturn(page);

        Page<UsageRecordDTO> result = usageRecordService.getUsageRecords(null, 0, 10);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void createUsageRecord_shouldReduceSupplyAndSave() {
        UsageRecordDTO input = buildInputDTO(3);
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));
        when(employeeRepo.findOneById(2L)).thenReturn(Optional.of(employee));
        when(usageRecordRepo.save(any(UsageRecord.class))).thenReturn(usageRecord);

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(9L);

            usageRecordService.createUsageRecord(input);

            assertEquals(7, product.getSupply());
            verify(productRepo).save(product);
            verify(usageRecordRepo).save(any(UsageRecord.class));
        }
    }

    @Test
    void createUsageRecord_shouldThrowInsufficientSupplyException_whenSupplyTooLow() {
        UsageRecordDTO input = buildInputDTO(999);
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));
        when(employeeRepo.findOneById(2L)).thenReturn(Optional.of(employee));

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(9L);

            assertThrows(InsufficientSupplyException.class, () -> usageRecordService.createUsageRecord(input));
            verify(usageRecordRepo, never()).save(any());
        }
    }

    @Test
    void createUsageRecord_shouldThrowResourceNotFoundException_whenProductNotFound() {
        UsageRecordDTO input = buildInputDTO(1);
        when(productRepo.findOneById(1L)).thenReturn(Optional.empty());

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(9L);

            assertThrows(ResourceNotFoundException.class, () -> usageRecordService.createUsageRecord(input));
        }
    }

    @Test
    void createUsageRecord_shouldThrowResourceNotFoundException_whenEmployeeNotFound() {
        UsageRecordDTO input = buildInputDTO(1);
        when(productRepo.findOneById(1L)).thenReturn(Optional.of(product));
        when(employeeRepo.findOneById(2L)).thenReturn(Optional.empty());

        try (MockedStatic<SessionUtils> mockedSession = mockStatic(SessionUtils.class)) {
            mockedSession.when(SessionUtils::getUserIdFromSession).thenReturn(9L);

            assertThrows(ResourceNotFoundException.class, () -> usageRecordService.createUsageRecord(input));
        }
    }

    @Test
    void deleteUsageRecordById_shouldAddToSupply_whenProductNotDeleted() {
        when(usageRecordRepo.findOneById(5L)).thenReturn(Optional.of(usageRecord));

        usageRecordService.deleteUsageRecordById(5L);

        assertEquals(13, product.getSupply());
        verify(productRepo).save(product);
        verify(usageRecordRepo).deleteById(5L);
        verify(auditLogService).logDelete(eq("UsageRecord"), eq(5L), anyString(), any());
    }

    @Test
    void deleteUsageRecordById_shouldRestoreProductAndAddSupply_whenProductIsDeleted() {
        product.softDelete();
        when(usageRecordRepo.findOneById(5L)).thenReturn(Optional.of(usageRecord));

        usageRecordService.deleteUsageRecordById(5L);

        assertFalse(product.getIsDeleted());
        assertEquals(3, product.getSupply());
        verify(productRepo).save(product);
        verify(usageRecordRepo).deleteById(5L);
    }

    @Test
    void deleteUsageRecordById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(usageRecordRepo.findOneById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> usageRecordService.deleteUsageRecordById(99L));
        verify(usageRecordRepo, never()).deleteById(any());
    }

    @Test
    void deleteUsageRecordById_shouldRethrowAccessDeniedException_whenNotOwner() {
        when(usageRecordRepo.findOneById(5L)).thenReturn(Optional.of(usageRecord));
        doThrow(new AccessDeniedException("denied")).when(ownershipService).checkOwnershipOrAdmin(9L);

        assertThrows(Exception.class, () -> usageRecordService.deleteUsageRecordById(5L));
        verify(usageRecordRepo, never()).deleteById(any());
    }

    private UsageRecordDTO buildInputDTO(int quantity) {
        ProductDTO productDTO = new ProductDTO();
        productDTO.setId(1L);
        EmployeeSummaryDTO employeeDTO = new EmployeeSummaryDTO();
        employeeDTO.setId(2L);
        UsageRecordDTO dto = new UsageRecordDTO();
        dto.setProduct(productDTO);
        dto.setEmployee(employeeDTO);
        dto.setQuantity(quantity);
        dto.setUsageDate(LocalDate.now());
        dto.setUsageReason(UsageReason.REGULAR_USAGE);
        return dto;
    }
}

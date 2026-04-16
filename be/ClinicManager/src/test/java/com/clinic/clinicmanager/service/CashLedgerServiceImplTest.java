package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.CashLedgerDTO;
import com.clinic.clinicmanager.DTO.EmployeeSummaryDTO;
import com.clinic.clinicmanager.DTO.request.CashLedgerFilterDTO;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.CashLedger;
import com.clinic.clinicmanager.model.Employee;
import com.clinic.clinicmanager.repo.CashLedgerRepo;
import com.clinic.clinicmanager.repo.EmployeeRepo;
import com.clinic.clinicmanager.service.impl.CashLedgerServiceImpl;
import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CashLedgerServiceImplTest {

    @Mock CashLedgerRepo cashLedgerRepo;
    @Mock EmployeeRepo employeeRepo;
    @Mock AuditLogService auditLogService;

    @InjectMocks
    CashLedgerServiceImpl cashLedgerService;

    private Employee employee() {
        return Employee.builder().id(1L).name("Anna").lastName("Kowalska").build();
    }

    private UserDetailsImpl userDetailsWithEmployee() {
        EmployeeSummaryDTO emp = new EmployeeSummaryDTO(1L, "Anna", "Kowalska");
        return new UserDetailsImpl(10L, "user", "pass", null, emp, List.of());
    }

    private CashLedger cashLedger(Long id, boolean isClosed) {
        return CashLedger.builder()
                .id(id)
                .date(LocalDate.of(2025, 4, 14))
                .openingAmount(100.0)
                .deposit(0.0)
                .cashOutAmount(0.0)
                .isClosed(isClosed)
                .build();
    }

    @Test
    void openCashLedger_shouldThrowConflictException_whenDateAlreadyExists() {
        CashLedgerDTO dto = new CashLedgerDTO();
        dto.setDate(LocalDate.of(2025, 4, 14));
        when(cashLedgerRepo.existsByDate(dto.getDate())).thenReturn(true);

        assertThrows(ConflictException.class, () -> cashLedgerService.openCashLedger(dto));
    }

    @Test
    void openCashLedger_shouldThrowConflictException_whenAnotherLedgerIsOpen() {
        CashLedgerDTO dto = new CashLedgerDTO();
        dto.setDate(LocalDate.of(2025, 4, 14));
        when(cashLedgerRepo.existsByDate(dto.getDate())).thenReturn(false);
        when(cashLedgerRepo.existsByIsClosedFalse()).thenReturn(true);

        assertThrows(ConflictException.class, () -> cashLedgerService.openCashLedger(dto));
    }

    @Test
    void openCashLedger_shouldThrowResourceNotFoundException_whenSessionHasNoEmployee() {
        CashLedgerDTO dto = new CashLedgerDTO();
        dto.setDate(LocalDate.of(2025, 4, 14));
        when(cashLedgerRepo.existsByDate(dto.getDate())).thenReturn(false);
        when(cashLedgerRepo.existsByIsClosedFalse()).thenReturn(false);

        try (MockedStatic<SessionUtils> mocked = mockStatic(SessionUtils.class)) {
            mocked.when(SessionUtils::getUserDetailsFromSession).thenReturn(null);

            assertThrows(ResourceNotFoundException.class, () -> cashLedgerService.openCashLedger(dto));
        }
    }

    @Test
    void openCashLedger_shouldDefaultDepositToZero_whenDepositIsNull() {
        CashLedgerDTO dto = new CashLedgerDTO();
        dto.setDate(LocalDate.of(2025, 4, 14));
        dto.setOpeningAmount(200.0);
        dto.setDeposit(null);

        try (MockedStatic<SessionUtils> mocked = mockStatic(SessionUtils.class)) {
            mocked.when(SessionUtils::getUserDetailsFromSession).thenReturn(userDetailsWithEmployee());
            when(cashLedgerRepo.existsByDate(dto.getDate())).thenReturn(false);
            when(cashLedgerRepo.existsByIsClosedFalse()).thenReturn(false);
            when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee()));
            when(cashLedgerRepo.save(any())).thenReturn(cashLedger(1L, false));

            cashLedgerService.openCashLedger(dto);

            ArgumentCaptor<CashLedger> captor = ArgumentCaptor.forClass(CashLedger.class);
            verify(cashLedgerRepo).save(captor.capture());
            assertEquals(0.0, captor.getValue().getDeposit());
            verify(auditLogService).logCreate(eq("CashLedger"), eq(1L), anyString(), any());
        }
    }

    @Test
    void openCashLedger_shouldSaveProvidedDeposit_whenDepositNotNull() {
        CashLedgerDTO dto = new CashLedgerDTO();
        dto.setDate(LocalDate.of(2025, 4, 14));
        dto.setOpeningAmount(200.0);
        dto.setDeposit(50.0);

        try (MockedStatic<SessionUtils> mocked = mockStatic(SessionUtils.class)) {
            mocked.when(SessionUtils::getUserDetailsFromSession).thenReturn(userDetailsWithEmployee());
            when(cashLedgerRepo.existsByDate(dto.getDate())).thenReturn(false);
            when(cashLedgerRepo.existsByIsClosedFalse()).thenReturn(false);
            when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee()));
            when(cashLedgerRepo.save(any())).thenReturn(cashLedger(1L, false));

            cashLedgerService.openCashLedger(dto);

            ArgumentCaptor<CashLedger> captor = ArgumentCaptor.forClass(CashLedger.class);
            verify(cashLedgerRepo).save(captor.capture());
            assertEquals(50.0, captor.getValue().getDeposit());
        }
    }


    @Test
    void closeCashLedger_shouldThrowResourceNotFoundException_whenNotFound() {
        when(cashLedgerRepo.findByIdWithDetails(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cashLedgerService.closeCashLedger(99L, new CashLedgerDTO()));
    }

    @Test
    void closeCashLedger_shouldThrowConflictException_whenAlreadyClosed() {
        when(cashLedgerRepo.findByIdWithDetails(1L)).thenReturn(Optional.of(cashLedger(1L, true)));

        assertThrows(ConflictException.class, () -> cashLedgerService.closeCashLedger(1L, new CashLedgerDTO()));
    }

    @Test
    void closeCashLedger_shouldDefaultCashOutToZero_whenCashOutAmountIsNull() {
        CashLedger ledger = cashLedger(1L, false);
        CashLedgerDTO dto = new CashLedgerDTO();
        dto.setClosingAmount(500.0);
        dto.setCashOutAmount(null);

        try (MockedStatic<SessionUtils> mocked = mockStatic(SessionUtils.class)) {
            mocked.when(SessionUtils::getUserDetailsFromSession).thenReturn(userDetailsWithEmployee());
            when(cashLedgerRepo.findByIdWithDetails(1L)).thenReturn(Optional.of(ledger));
            when(employeeRepo.findOneById(1L)).thenReturn(Optional.of(employee()));
            when(cashLedgerRepo.save(any())).thenReturn(cashLedger(1L, true));

            cashLedgerService.closeCashLedger(1L, dto);

            ArgumentCaptor<CashLedger> captor = ArgumentCaptor.forClass(CashLedger.class);
            verify(cashLedgerRepo).save(captor.capture());
            assertEquals(0.0, captor.getValue().getCashOutAmount());
            assertTrue(captor.getValue().getIsClosed());
        }
    }

    @Test
    void updateCashLedger_shouldThrowResourceNotFoundException_whenNotFound() {
        when(cashLedgerRepo.findByIdWithDetails(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cashLedgerService.updateCashLedger(99L, new CashLedgerDTO()));
    }

    @Test
    void updateCashLedger_shouldPatchOnlyNonNullFields() {
        CashLedger ledger = cashLedger(1L, false);
        ledger.setNote("stara notatka");

        CashLedgerDTO dto = new CashLedgerDTO();
        dto.setNote("nowa notatka");

        when(cashLedgerRepo.findByIdWithDetails(1L)).thenReturn(Optional.of(ledger));
        when(cashLedgerRepo.save(any())).thenReturn(ledger);

        cashLedgerService.updateCashLedger(1L, dto);

        ArgumentCaptor<CashLedger> captor = ArgumentCaptor.forClass(CashLedger.class);
        verify(cashLedgerRepo).save(captor.capture());
        assertEquals("nowa notatka", captor.getValue().getNote());
        assertEquals(100.0, captor.getValue().getOpeningAmount());
    }

    @Test
    void getCashLedgerById_shouldReturnDTO_whenFound() {
        when(cashLedgerRepo.findByIdWithDetails(1L)).thenReturn(Optional.of(cashLedger(1L, false)));

        assertEquals(1L, cashLedgerService.getCashLedgerById(1L).getId());
    }

    @Test
    void getCashLedgerById_shouldThrowResourceNotFoundException_whenNotFound() {
        when(cashLedgerRepo.findByIdWithDetails(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cashLedgerService.getCashLedgerById(99L));
    }

    @Test
    void getTodayOpenLedger_shouldReturnDTO_whenFound() {
        when(cashLedgerRepo.findByDate(any())).thenReturn(Optional.of(cashLedger(1L, false)));

        assertNotNull(cashLedgerService.getTodayOpenLedger());
    }

    @Test
    void getTodayOpenLedger_shouldReturnNull_whenNotFound() {
        when(cashLedgerRepo.findByDate(any())).thenReturn(Optional.empty());

        assertNull(cashLedgerService.getTodayOpenLedger());
    }

    @Test
    void getLastClosingAmount_shouldReturnAmount_whenFound() {
        CashLedger ledger = cashLedger(1L, true);
        ledger.setClosingAmount(300.0);
        when(cashLedgerRepo.findTopByIsClosedTrueOrderByDateDesc()).thenReturn(Optional.of(ledger));

        assertEquals(300.0, cashLedgerService.getLastClosingAmount());
    }

    @Test
    void getLastClosingAmount_shouldReturnNull_whenNotFound() {
        when(cashLedgerRepo.findTopByIsClosedTrueOrderByDateDesc()).thenReturn(Optional.empty());

        assertNull(cashLedgerService.getLastClosingAmount());
    }

    @Test
    void getLastOpenCashLedger_shouldReturnDTO_whenFound() {
        when(cashLedgerRepo.findTopByIsClosedFalseAndDateBeforeOrderByDateDesc(any())).thenReturn(Optional.of(cashLedger(1L, false)));

        assertNotNull(cashLedgerService.getLastOpenCashLedger());
    }

    @Test
    void getLastOpenCashLedger_shouldReturnNull_whenNotFound() {
        when(cashLedgerRepo.findTopByIsClosedFalseAndDateBeforeOrderByDateDesc(any())).thenReturn(Optional.empty());

        assertNull(cashLedgerService.getLastOpenCashLedger());
    }

    @Test
    void getCashLedgerByDate_shouldReturnDTO_whenFound() {
        when(cashLedgerRepo.findByDate(LocalDate.of(2025, 4, 14))).thenReturn(Optional.of(cashLedger(1L, false)));

        assertNotNull(cashLedgerService.getCashLedgerByDate(LocalDate.of(2025, 4, 14)));
    }

    @Test
    void getCashLedgerByDate_shouldThrowResourceNotFoundException_whenNotFound() {
        when(cashLedgerRepo.findByDate(LocalDate.of(2025, 4, 14))).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cashLedgerService.getCashLedgerByDate(LocalDate.of(2025, 4, 14)));
    }

    @Test
    void getCashLedgers_shouldUseFallbackDateFrom1900_whenYearIsNull() {
        when(cashLedgerRepo.findAllWithFilters(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        cashLedgerService.getCashLedgers(null, 0, 10);

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(cashLedgerRepo).findAllWithFilters(isNull(), fromCaptor.capture(), any(), isNull(), any());
        assertEquals(LocalDate.of(1900, 1, 1), fromCaptor.getValue());
    }

    @Test
    void getCashLedgers_shouldUseMonthRange_whenYearAndMonthProvided() {
        CashLedgerFilterDTO filter = new CashLedgerFilterDTO();
        filter.setYear(2025);
        filter.setMonth(4);
        when(cashLedgerRepo.findAllWithFilters(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        cashLedgerService.getCashLedgers(filter, 0, 10);

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(cashLedgerRepo).findAllWithFilters(isNull(), fromCaptor.capture(), toCaptor.capture(), isNull(), any());
        assertEquals(LocalDate.of(2025, 4, 1), fromCaptor.getValue());
        assertEquals(LocalDate.of(2025, 4, 30), toCaptor.getValue());
    }

    @Test
    void getCashLedgers_shouldUseYearRange_whenOnlyYearProvided() {
        CashLedgerFilterDTO filter = new CashLedgerFilterDTO();
        filter.setYear(2025);
        when(cashLedgerRepo.findAllWithFilters(any(), any(), any(), any(), any())).thenReturn(Page.empty());

        cashLedgerService.getCashLedgers(filter, 0, 10);

        ArgumentCaptor<LocalDate> fromCaptor = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(cashLedgerRepo).findAllWithFilters(isNull(), fromCaptor.capture(), toCaptor.capture(), isNull(), any());
        assertEquals(LocalDate.of(2025, 1, 1), fromCaptor.getValue());
        assertEquals(LocalDate.of(2025, 12, 31), toCaptor.getValue());
    }
}

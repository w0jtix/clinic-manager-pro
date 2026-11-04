package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.ExchangeRateDTO;
import com.clinic.clinicmanager.DTO.ExchangeRateWindowDTO;
import com.clinic.clinicmanager.exceptions.ExternalServiceUnavailableException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.ExchangeRate;
import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import com.clinic.clinicmanager.repo.ExchangeRateRepo;
import com.clinic.clinicmanager.service.impl.ExchangeRateServiceImpl;
import com.clinic.clinicmanager.service.impl.NbpExchangeRateClient;
import com.clinic.clinicmanager.service.impl.NbpExchangeRateClient.NbpRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-01-15T10:00:00Z"), ZoneId.of("Europe/Warsaw"));
    private static final LocalDate TODAY = LocalDate.now(CLOCK);
    private static final String FUTURE_DATE_MESSAGE = "Kurs NBP nie jest dostępny dla daty z przyszłości.";

    @Mock ExchangeRateRepo exchangeRateRepo;
    @Mock NbpExchangeRateClient nbpClient;

    ExchangeRateServiceImpl exchangeRateService;

    @BeforeEach
    void setUp() {
        exchangeRateService = new ExchangeRateServiceImpl(exchangeRateRepo, nbpClient, CLOCK);
    }

    private static NbpRate nbpRate(String no, LocalDate date, String mid) {
        return new NbpRate(no, date, new BigDecimal(mid));
    }

    private static List<NbpRate> octoberWeekRates() {
        return List.of(
                nbpRate("208/A/NBP/2026", LocalDate.of(2026, 10, 27), "4.2410"),
                nbpRate("209/A/NBP/2026", LocalDate.of(2026, 10, 28), "4.2455"),
                nbpRate("210/A/NBP/2026", LocalDate.of(2026, 10, 29), "4.2490"),
                nbpRate("211/A/NBP/2026", LocalDate.of(2026, 10, 30), "4.2512"));
    }

    private static void assertRate(String expectedDate, String expectedMid, String expectedTable, ExchangeRateDTO rate) {
        assertEquals(LocalDate.parse(expectedDate), rate.getRateDate());
        assertEquals(0, new BigDecimal(expectedMid).compareTo(rate.getMid()));
        assertEquals(expectedTable, rate.getTableNo());
    }

    @Test
    void resolveRate_shouldSelectLastWorkingDayBeforeDate_andCacheAllRates_whenMonday() {
        LocalDate monday = LocalDate.of(2026, 11, 2);
        when(exchangeRateRepo.findByCurrencyAndRateDate(ExpenseCurrency.EUR, LocalDate.of(2026, 11, 1)))
                .thenReturn(Optional.empty());
        when(nbpClient.fetchRates(ExpenseCurrency.EUR, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)))
                .thenReturn(octoberWeekRates());

        ExchangeRateDTO selected = exchangeRateService.resolveRate(ExpenseCurrency.EUR, monday).orElseThrow();

        assertRate("2026-10-30", "4.2512", "211/A/NBP/2026", selected);

        ArgumentCaptor<LocalDate> dates = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<BigDecimal> mids = ArgumentCaptor.forClass(BigDecimal.class);
        ArgumentCaptor<String> tables = ArgumentCaptor.forClass(String.class);
        verify(exchangeRateRepo, times(4)).upsert(eq("EUR"), dates.capture(), mids.capture(), tables.capture(),
                any(LocalDateTime.class));
        assertEquals(List.of(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28),
                LocalDate.of(2026, 10, 29), LocalDate.of(2026, 10, 30)), dates.getAllValues());
        assertEquals(List.of("208/A/NBP/2026", "209/A/NBP/2026", "210/A/NBP/2026", "211/A/NBP/2026"),
                tables.getAllValues());
        assertEquals(0, new BigDecimal("4.2410").compareTo(mids.getAllValues().get(0)));
        assertEquals(0, new BigDecimal("4.2512").compareTo(mids.getAllValues().get(3)));
    }

    @Test
    void resolveRate_shouldUseCachedRateWithoutCallingNbp_whenCacheHasPreviousDay() {
        LocalDate saturday = LocalDate.of(2026, 10, 31);
        when(exchangeRateRepo.findByCurrencyAndRateDate(ExpenseCurrency.EUR, LocalDate.of(2026, 10, 30)))
                .thenReturn(Optional.of(ExchangeRate.builder()
                        .currency(ExpenseCurrency.EUR)
                        .rateDate(LocalDate.of(2026, 10, 30))
                        .mid(new BigDecimal("4.2512"))
                        .tableNo("211/A/NBP/2026")
                        .fetchedAt(LocalDateTime.of(2026, 10, 30, 13, 0))
                        .build()));

        ExchangeRateDTO selected = exchangeRateService.resolveRate(ExpenseCurrency.EUR, saturday).orElseThrow();

        assertRate("2026-10-30", "4.2512", "211/A/NBP/2026", selected);
        verifyNoInteractions(nbpClient);
        verify(exchangeRateRepo, never()).upsert(anyString(), any(), any(), any(), any());
    }

    @Test
    void resolveRate_shouldSelectLastRateBeforeHolidays_whenMondayAfterChristmas() {
        LocalDate monday = LocalDate.of(2026, 12, 28);
        when(exchangeRateRepo.findByCurrencyAndRateDate(ExpenseCurrency.EUR, LocalDate.of(2026, 12, 27)))
                .thenReturn(Optional.empty());
        when(nbpClient.fetchRates(ExpenseCurrency.EUR, LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 27)))
                .thenReturn(List.of(
                        nbpRate("245/A/NBP/2026", LocalDate.of(2026, 12, 21), "4.2101"),
                        nbpRate("246/A/NBP/2026", LocalDate.of(2026, 12, 22), "4.2155"),
                        nbpRate("247/A/NBP/2026", LocalDate.of(2026, 12, 23), "4.2203")));

        ExchangeRateDTO selected = exchangeRateService.resolveRate(ExpenseCurrency.EUR, monday).orElseThrow();

        assertRate("2026-12-23", "4.2203", "247/A/NBP/2026", selected);
        verify(exchangeRateRepo, times(3)).upsert(eq("EUR"), any(), any(), any(), any());
    }

    @Test
    void resolveRate_shouldThrowValidationExceptionWithCurrencyAndDate_whenNbpHasNoRates() {
        LocalDate date = LocalDate.of(2026, 11, 2);
        when(exchangeRateRepo.findByCurrencyAndRateDate(ExpenseCurrency.EUR, LocalDate.of(2026, 11, 1)))
                .thenReturn(Optional.empty());
        when(nbpClient.fetchRates(ExpenseCurrency.EUR, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)))
                .thenReturn(List.of());

        ValidationException ex = assertThrows(ValidationException.class,
                () -> exchangeRateService.resolveRate(ExpenseCurrency.EUR, date));

        assertEquals("Brak kursu NBP EUR w 7 dniach przed 02.11.2026.", ex.getMessage());
        assertTrue(ex.getMessage().contains("EUR"));
        assertTrue(ex.getMessage().contains("02.11.2026"));
        verify(exchangeRateRepo, never()).upsert(anyString(), any(), any(), any(), any());
    }

    @Test
    void resolveRate_shouldPropagateServiceUnavailable_whenNbpFails() {
        LocalDate date = LocalDate.of(2026, 11, 2);
        when(exchangeRateRepo.findByCurrencyAndRateDate(ExpenseCurrency.USD, LocalDate.of(2026, 11, 1)))
                .thenReturn(Optional.empty());
        when(nbpClient.fetchRates(ExpenseCurrency.USD, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)))
                .thenThrow(new ExternalServiceUnavailableException(NbpExchangeRateClient.UNAVAILABLE_MESSAGE));

        ExternalServiceUnavailableException ex = assertThrows(ExternalServiceUnavailableException.class,
                () -> exchangeRateService.resolveRate(ExpenseCurrency.USD, date));

        assertEquals("Nie udało się pobrać kursu z NBP. Spróbuj ponownie później.", ex.getMessage());
        verify(exchangeRateRepo, never()).upsert(anyString(), any(), any(), any(), any());
    }

    @Test
    void resolveRate_shouldReturnEmptyWithoutCallingNbp_whenPln() {
        Optional<ExchangeRateDTO> result = exchangeRateService.resolveRate(ExpenseCurrency.PLN, LocalDate.of(2026, 11, 2));

        assertTrue(result.isEmpty());
        verifyNoInteractions(nbpClient, exchangeRateRepo);
    }

    @Test
    void resolveRate_shouldIgnoreRatesOutsideWindow_whenNbpReturnsThem() {
        LocalDate date = LocalDate.of(2026, 11, 2);
        when(exchangeRateRepo.findByCurrencyAndRateDate(ExpenseCurrency.EUR, LocalDate.of(2026, 11, 1)))
                .thenReturn(Optional.empty());
        when(nbpClient.fetchRates(ExpenseCurrency.EUR, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)))
                .thenReturn(List.of(
                        nbpRate("211/A/NBP/2026", LocalDate.of(2026, 10, 30), "4.2512"),
                        nbpRate("212/A/NBP/2026", LocalDate.of(2026, 11, 2), "4.3000")));

        ExchangeRateDTO selected = exchangeRateService.resolveRate(ExpenseCurrency.EUR, date).orElseThrow();

        assertRate("2026-10-30", "4.2512", "211/A/NBP/2026", selected);
        verify(exchangeRateRepo, times(1)).upsert(eq("EUR"), any(), any(), any(), any());
    }

    @Test
    void resolveRate_shouldThrowValidationException_whenForeignCurrencyWithoutDate() {
        assertThrows(ValidationException.class, () -> exchangeRateService.resolveRate(ExpenseCurrency.EUR, null));
        verifyNoInteractions(nbpClient, exchangeRateRepo);
    }

    @Test
    void getRateWindow_shouldReturnAllRatesSortedAscWithSelectedAndWindowBounds() {
        LocalDate monday = LocalDate.of(2026, 11, 2);
        List<NbpRate> unordered = List.of(
                octoberWeekRates().get(3), octoberWeekRates().get(0),
                octoberWeekRates().get(2), octoberWeekRates().get(1));
        when(nbpClient.fetchRates(ExpenseCurrency.EUR, LocalDate.of(2026, 10, 26), LocalDate.of(2026, 11, 1)))
                .thenReturn(unordered);

        ExchangeRateWindowDTO window = exchangeRateService.getRateWindow(ExpenseCurrency.EUR, monday);

        assertEquals(ExpenseCurrency.EUR, window.getCurrency());
        assertEquals(monday, window.getDate());
        assertEquals(LocalDate.of(2026, 10, 26), window.getWindowFrom());
        assertEquals(LocalDate.of(2026, 11, 1), window.getWindowTo());
        assertEquals(4, window.getRates().size());
        assertRate("2026-10-27", "4.2410", "208/A/NBP/2026", window.getRates().get(0));
        assertRate("2026-10-28", "4.2455", "209/A/NBP/2026", window.getRates().get(1));
        assertRate("2026-10-29", "4.2490", "210/A/NBP/2026", window.getRates().get(2));
        assertRate("2026-10-30", "4.2512", "211/A/NBP/2026", window.getRates().get(3));
        assertRate("2026-10-30", "4.2512", "211/A/NBP/2026", window.getSelected());
        verify(exchangeRateRepo, times(4)).upsert(eq("EUR"), any(), any(), any(), any());
    }

    @Test
    void getRateWindow_shouldThrowValidationException_whenPln() {
        assertThrows(ValidationException.class,
                () -> exchangeRateService.getRateWindow(ExpenseCurrency.PLN, LocalDate.of(2026, 11, 2)));
        verifyNoInteractions(nbpClient, exchangeRateRepo);
    }

    @Test
    void getRateWindow_shouldThrowValidationException_whenCurrencyOrDateMissing() {
        assertThrows(ValidationException.class,
                () -> exchangeRateService.getRateWindow(null, LocalDate.of(2026, 11, 2)));
        assertThrows(ValidationException.class,
                () -> exchangeRateService.getRateWindow(ExpenseCurrency.EUR, null));
        verifyNoInteractions(nbpClient, exchangeRateRepo);
    }

    @Test
    void getRateWindow_shouldThrowValidationException_whenNoRatesInWindow() {
        when(nbpClient.fetchRates(ExpenseCurrency.USD, LocalDate.of(2026, 12, 21), LocalDate.of(2026, 12, 27)))
                .thenReturn(List.of());

        ValidationException ex = assertThrows(ValidationException.class,
                () -> exchangeRateService.getRateWindow(ExpenseCurrency.USD, LocalDate.of(2026, 12, 28)));

        assertEquals("Brak kursu NBP USD w 7 dniach przed 28.12.2026.", ex.getMessage());
    }

    private static List<NbpRate> weekBeforeTodayRates() {
        return List.of(
                nbpRate("006/A/NBP/2027", LocalDate.of(2027, 1, 12), "4.2210"),
                nbpRate("007/A/NBP/2027", LocalDate.of(2027, 1, 13), "4.2250"),
                nbpRate("008/A/NBP/2027", LocalDate.of(2027, 1, 14), "4.2299"));
    }

    @Test
    void getRateWindow_shouldThrowValidationExceptionWithoutCallingNbp_whenDateIsTomorrow() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> exchangeRateService.getRateWindow(ExpenseCurrency.EUR, TODAY.plusDays(1)));

        assertEquals(FUTURE_DATE_MESSAGE, ex.getMessage());
        verifyNoInteractions(nbpClient, exchangeRateRepo);
    }

    @Test
    void resolveRate_shouldThrowValidationExceptionWithoutCallingNbpOrRepo_whenDateIsTenDaysAhead() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> exchangeRateService.resolveRate(ExpenseCurrency.USD, TODAY.plusDays(10)));

        assertEquals(FUTURE_DATE_MESSAGE, ex.getMessage());
        verifyNoInteractions(nbpClient, exchangeRateRepo);
    }

    @Test
    void resolveRate_shouldThrowValidationException_whenDateIsTomorrowInWarsawButTodayInUtc() {
        Clock lateEveningUtc = Clock.fixed(Instant.parse("2027-01-15T23:30:00Z"), ZoneId.of("Europe/Warsaw"));
        ExchangeRateServiceImpl service = new ExchangeRateServiceImpl(exchangeRateRepo, nbpClient, lateEveningUtc);
        when(exchangeRateRepo.findByCurrencyAndRateDate(ExpenseCurrency.EUR, LocalDate.of(2027, 1, 15)))
                .thenReturn(Optional.empty());
        when(nbpClient.fetchRates(ExpenseCurrency.EUR, LocalDate.of(2027, 1, 9), LocalDate.of(2027, 1, 15)))
                .thenReturn(List.of(nbpRate("009/A/NBP/2027", LocalDate.of(2027, 1, 15), "4.2301")));

        ExchangeRateDTO selected = service.resolveRate(ExpenseCurrency.EUR, LocalDate.of(2027, 1, 16)).orElseThrow();

        assertRate("2027-01-15", "4.2301", "009/A/NBP/2027", selected);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> service.resolveRate(ExpenseCurrency.EUR, LocalDate.of(2027, 1, 17)));
        assertEquals(FUTURE_DATE_MESSAGE, ex.getMessage());
    }

    @Test
    void resolveRate_shouldUseCachedPreviousDayRate_whenDateIsToday() {
        when(exchangeRateRepo.findByCurrencyAndRateDate(ExpenseCurrency.EUR, TODAY.minusDays(1)))
                .thenReturn(Optional.of(ExchangeRate.builder()
                        .currency(ExpenseCurrency.EUR)
                        .rateDate(LocalDate.of(2027, 1, 14))
                        .mid(new BigDecimal("4.2299"))
                        .tableNo("008/A/NBP/2027")
                        .fetchedAt(LocalDateTime.of(2027, 1, 14, 13, 0))
                        .build()));

        ExchangeRateDTO selected = exchangeRateService.resolveRate(ExpenseCurrency.EUR, TODAY).orElseThrow();

        assertRate("2027-01-14", "4.2299", "008/A/NBP/2027", selected);
        verifyNoInteractions(nbpClient);
    }

    @Test
    void resolveRate_shouldFetchWindowBeforeToday_whenDateIsTodayAndCacheEmpty() {
        when(exchangeRateRepo.findByCurrencyAndRateDate(ExpenseCurrency.EUR, TODAY.minusDays(1)))
                .thenReturn(Optional.empty());
        when(nbpClient.fetchRates(ExpenseCurrency.EUR, TODAY.minusDays(7), TODAY.minusDays(1)))
                .thenReturn(weekBeforeTodayRates());

        ExchangeRateDTO selected = exchangeRateService.resolveRate(ExpenseCurrency.EUR, TODAY).orElseThrow();

        assertRate("2027-01-14", "4.2299", "008/A/NBP/2027", selected);
        verify(exchangeRateRepo, times(3)).upsert(eq("EUR"), any(), any(), any(), any());
    }

    @Test
    void getRateWindow_shouldReturnWindowBeforeToday_whenDateIsToday() {
        when(nbpClient.fetchRates(ExpenseCurrency.EUR, TODAY.minusDays(7), TODAY.minusDays(1)))
                .thenReturn(weekBeforeTodayRates());

        ExchangeRateWindowDTO window = exchangeRateService.getRateWindow(ExpenseCurrency.EUR, TODAY);

        assertEquals(TODAY, window.getDate());
        assertEquals(LocalDate.of(2027, 1, 8), window.getWindowFrom());
        assertEquals(LocalDate.of(2027, 1, 14), window.getWindowTo());
        assertEquals(3, window.getRates().size());
        assertRate("2027-01-14", "4.2299", "008/A/NBP/2027", window.getSelected());
    }

    @Test
    void resolveRate_shouldReturnEmptyWithoutException_whenPlnAndDateIsTomorrow() {
        Optional<ExchangeRateDTO> result = exchangeRateService.resolveRate(ExpenseCurrency.PLN, TODAY.plusDays(1));

        assertTrue(result.isEmpty());
        verifyNoInteractions(nbpClient, exchangeRateRepo);
    }

    @Test
    void getRateWindow_shouldKeepPlnMessage_whenPlnAndDateIsTomorrow() {
        ValidationException ex = assertThrows(ValidationException.class,
                () -> exchangeRateService.getRateWindow(ExpenseCurrency.PLN, TODAY.plusDays(1)));

        assertEquals("Kurs NBP jest dostępny tylko dla walut obcych.", ex.getMessage());
        verifyNoInteractions(nbpClient, exchangeRateRepo);
    }
}

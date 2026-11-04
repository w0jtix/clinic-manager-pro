package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.ExchangeRateDTO;
import com.clinic.clinicmanager.DTO.ExchangeRateWindowDTO;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import com.clinic.clinicmanager.repo.ExchangeRateRepo;
import com.clinic.clinicmanager.service.ExchangeRateService;
import com.clinic.clinicmanager.service.impl.NbpExchangeRateClient.NbpRate;
import com.clinic.clinicmanager.utils.MoneyUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ExchangeRateServiceImpl implements ExchangeRateService {

    private static final int WINDOW_DAYS = 7;
    private static final int RATE_SCALE = 4;
    private static final DateTimeFormatter MESSAGE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final ExchangeRateRepo exchangeRateRepo;
    private final NbpExchangeRateClient nbpClient;
    private final Clock businessClock;

    @Override
    @Transactional
    public Optional<ExchangeRateDTO> resolveRate(ExpenseCurrency currency, LocalDate date) {
        if (currency == null || !currency.isForeign()) {
            return Optional.empty();
        }
        requireRateDate(date);

        Optional<ExchangeRateDTO> cached = exchangeRateRepo.findByCurrencyAndRateDate(currency, date.minusDays(1))
                .map(ExchangeRateDTO::new);
        if (cached.isPresent()) {
            return cached;
        }

        List<ExchangeRateDTO> rates = fetchWindow(currency, date);
        return Optional.of(rates.get(rates.size() - 1));
    }

    @Override
    @Transactional
    public ExchangeRateWindowDTO getRateWindow(ExpenseCurrency currency, LocalDate date) {
        if (currency == null) {
            throw new ValidationException("Waluta jest wymagana.");
        }
        if (!currency.isForeign()) {
            throw new ValidationException("Kurs NBP jest dostępny tylko dla walut obcych.");
        }
        requireRateDate(date);

        List<ExchangeRateDTO> rates = fetchWindow(currency, date);
        return new ExchangeRateWindowDTO(
                currency,
                date,
                windowFrom(date),
                windowTo(date),
                rates,
                rates.get(rates.size() - 1));
    }

    private List<ExchangeRateDTO> fetchWindow(ExpenseCurrency currency, LocalDate date) {
        LocalDate from = windowFrom(date);
        LocalDate to = windowTo(date);

        List<ExchangeRateDTO> rates = nbpClient.fetchRates(currency, from, to).stream()
                .filter(rate -> rate.effectiveDate() != null && rate.mid() != null && rate.no() != null)
                .filter(rate -> !rate.effectiveDate().isBefore(from) && !rate.effectiveDate().isAfter(to))
                .sorted(Comparator.comparing(NbpRate::effectiveDate))
                .map(rate -> new ExchangeRateDTO(rate.effectiveDate(), scaleRate(rate.mid()), rate.no()))
                .toList();

        if (rates.isEmpty()) {
            throw new ValidationException("Brak kursu NBP " + currency.name() + " w " + WINDOW_DAYS
                    + " dniach przed " + date.format(MESSAGE_DATE_FORMAT) + ".");
        }

        LocalDateTime fetchedAt = LocalDateTime.now();
        for (ExchangeRateDTO rate : rates) {
            exchangeRateRepo.upsert(currency.name(), rate.getRateDate(), rate.getMid(), rate.getTableNo(), fetchedAt);
        }
        return rates;
    }

    private void requireRateDate(LocalDate date) {
        if (date == null) {
            throw new ValidationException("Data kursu jest wymagana.");
        }
        if (date.isAfter(LocalDate.now(businessClock))) {
            throw new ValidationException("Kurs NBP nie jest dostępny dla daty z przyszłości.");
        }
    }

    private LocalDate windowFrom(LocalDate date) {
        return date.minusDays(WINDOW_DAYS);
    }

    private LocalDate windowTo(LocalDate date) {
        return date.minusDays(1);
    }

    private BigDecimal scaleRate(BigDecimal mid) {
        return mid.setScale(RATE_SCALE, MoneyUtils.ROUNDING);
    }
}

package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.ExchangeRate;
import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import com.clinic.clinicmanager.support.AbstractPostgresTest;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ExchangeRateRepoTest extends AbstractPostgresTest {

    private static final LocalDate RATE_DATE = LocalDate.of(2099, 10, 30);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ExchangeRateRepo exchangeRateRepo;

    @BeforeEach
    void setUp() {
        em.getEntityManager().createNativeQuery("DELETE FROM exchange_rate").executeUpdate();
    }

    private ExchangeRate rate(ExpenseCurrency currency, LocalDate date, String mid, String tableNo) {
        return ExchangeRate.builder()
                .currency(currency)
                .rateDate(date)
                .mid(new BigDecimal(mid))
                .tableNo(tableNo)
                .fetchedAt(LocalDateTime.of(2099, 10, 30, 13, 0))
                .build();
    }

    @Test
    void uniqueConstraint_shouldRejectDuplicateCurrencyAndDate() {
        em.persistAndFlush(rate(ExpenseCurrency.EUR, RATE_DATE, "4.2512", "211/A/NBP/2099"));

        assertThatThrownBy(() -> em.persistAndFlush(rate(ExpenseCurrency.EUR, RATE_DATE, "4.3000", "212/A/NBP/2099")))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void uniqueConstraint_shouldAllowSameDateForDifferentCurrency() {
        em.persistAndFlush(rate(ExpenseCurrency.EUR, RATE_DATE, "4.2512", "211/A/NBP/2099"));
        em.persistAndFlush(rate(ExpenseCurrency.USD, RATE_DATE, "3.9876", "211/A/NBP/2099"));

        assertThat(exchangeRateRepo.count()).isEqualTo(2);
    }

    @Test
    void upsert_shouldInsertThenUpdateExistingRow_forSameCurrencyAndDate() {
        exchangeRateRepo.upsert("EUR", RATE_DATE, new BigDecimal("4.2512"), "211/A/NBP/2099",
                LocalDateTime.of(2099, 10, 30, 13, 0));
        exchangeRateRepo.upsert("EUR", RATE_DATE, new BigDecimal("4.2600"), "211/A/NBP/2099",
                LocalDateTime.of(2099, 10, 31, 9, 0));
        em.clear();

        assertThat(exchangeRateRepo.count()).isEqualTo(1);
        ExchangeRate stored = exchangeRateRepo.findByCurrencyAndRateDate(ExpenseCurrency.EUR, RATE_DATE).orElseThrow();
        assertThat(stored.getMid()).isEqualByComparingTo("4.2600");
        assertThat(stored.getTableNo()).isEqualTo("211/A/NBP/2099");
        assertThat(stored.getFetchedAt()).isEqualTo(LocalDateTime.of(2099, 10, 31, 9, 0));
    }

    @Test
    void findByCurrencyAndRateDate_shouldReturnEmpty_whenOnlyOtherCurrencyCached() {
        em.persistAndFlush(rate(ExpenseCurrency.USD, RATE_DATE, "3.9876", "211/A/NBP/2099"));

        assertThat(exchangeRateRepo.findByCurrencyAndRateDate(ExpenseCurrency.EUR, RATE_DATE)).isEmpty();
    }
}

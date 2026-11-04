package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.ExchangeRate;
import com.clinic.clinicmanager.model.constants.ExpenseCurrency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface ExchangeRateRepo extends JpaRepository<ExchangeRate, Long> {

    Optional<ExchangeRate> findByCurrencyAndRateDate(ExpenseCurrency currency, LocalDate rateDate);

    @Modifying
    @Query(value = """
            INSERT INTO exchange_rate (currency, rate_date, mid, table_no, fetched_at)
            VALUES (:currency, :rateDate, :mid, :tableNo, :fetchedAt)
            ON CONFLICT (currency, rate_date)
            DO UPDATE SET mid = EXCLUDED.mid, table_no = EXCLUDED.table_no, fetched_at = EXCLUDED.fetched_at
            """, nativeQuery = true)
    void upsert(@Param("currency") String currency,
                @Param("rateDate") LocalDate rateDate,
                @Param("mid") BigDecimal mid,
                @Param("tableNo") String tableNo,
                @Param("fetchedAt") LocalDateTime fetchedAt);
}

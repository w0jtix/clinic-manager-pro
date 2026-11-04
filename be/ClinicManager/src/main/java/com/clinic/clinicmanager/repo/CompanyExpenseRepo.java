package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.CompanyExpense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.clinic.clinicmanager.repo.projection.CompanyExpenseProjection;
import com.clinic.clinicmanager.repo.projection.KsefMonthlySpendProjection;
import com.clinic.clinicmanager.repo.projection.SellerManualExpenseSummaryProjection;
import com.clinic.clinicmanager.repo.projection.UnifiedCategoryExpenseProjection;
import com.clinic.clinicmanager.repo.projection.UnifiedExpenseProjection;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface CompanyExpenseRepo extends JpaRepository<CompanyExpense, Long> {

    @EntityGraph(attributePaths = {"expenseItems", "seller"})
    @Query("SELECT ce FROM CompanyExpense ce WHERE ce.id = :id")
    Optional<CompanyExpense> findOneByIdWithItems(@Param("id") Long id);

    @Query("SELECT ce.id FROM CompanyExpense ce WHERE ce.id IN :ids")
    Set<Long> findExistingIds(@Param("ids") Collection<Long> ids);

    @Query(
            value = """
    SELECT DISTINCT ce FROM CompanyExpense ce
    WHERE (:categories IS NULL OR ce.category IN :categories)
    AND (ce.expenseDate >= :dateFrom)
    AND (ce.expenseDate <= :dateTo)
    """,
            countQuery = """
    SELECT COUNT(DISTINCT ce.id) FROM CompanyExpense ce
    WHERE (:categories IS NULL OR ce.category IN :categories)
    AND (ce.expenseDate >= :dateFrom)
    AND (ce.expenseDate <= :dateTo)
    """
    )
    Page<CompanyExpense> findAllWithFilters(
            @Param("categories") List<String> categories,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            Pageable pageable
    );

    Optional<CompanyExpense> findFirstByCategoryOrderByExpenseDateDescIdDesc(String category);

    @EntityGraph(attributePaths = {"expenseItems"})
    List<CompanyExpense> findByCategoryAndEmployeeIdOrderByExpenseDateAsc(String category, Long employeeId);

    @EntityGraph(attributePaths = {"seller"})
    @Query(
            value = """
    SELECT ce FROM CompanyExpense ce
    WHERE ce.seller.id = :sellerId
    ORDER BY ce.expenseDate DESC, ce.id DESC
    """,
            countQuery = """
    SELECT COUNT(ce) FROM CompanyExpense ce
    WHERE ce.seller.id = :sellerId
    """
    )
    Page<CompanyExpense> findBySellerId(@Param("sellerId") Long sellerId, Pageable pageable);

    @Query("""
            SELECT COUNT(ce) AS expenseCount,
                   SUM(ce.totalValue) AS totalValue
            FROM CompanyExpense ce
            WHERE ce.seller.id = :sellerId
            """)
    SellerManualExpenseSummaryProjection summarizeBySellerId(@Param("sellerId") Long sellerId);

    @Query("""
            SELECT EXTRACT(MONTH FROM ce.expenseDate) AS month,
                   SUM(ce.totalValue) AS gross,
                   COUNT(ce) AS cnt
            FROM CompanyExpense ce
            WHERE ce.seller.id = :sellerId
              AND ce.expenseDate >= :dateFrom
              AND ce.expenseDate <= :dateTo
            GROUP BY EXTRACT(MONTH FROM ce.expenseDate)
            """)
    List<KsefMonthlySpendProjection> sumMonthlySpendBySellerId(
            @Param("sellerId") Long sellerId,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo);

    @Query("SELECT MIN(ce.expenseDate) FROM CompanyExpense ce WHERE ce.seller.id = :sellerId")
    LocalDate findFirstExpenseDateBySellerId(@Param("sellerId") Long sellerId);

    @Query("SELECT MAX(ce.expenseDate) FROM CompanyExpense ce WHERE ce.seller.id = :sellerId")
    LocalDate findLastExpenseDateBySellerId(@Param("sellerId") Long sellerId);

    // ========== Unified Expense View (company_expense + KSeF invoices) ==========

    @Query(
            value = """
    SELECT eu.source_id AS sourceId,
           eu.source_type AS sourceType,
           eu.source AS source,
           eu.expense_date AS expenseDate,
           eu.category AS category,
           eu.total_net AS totalNet,
           eu.total_vat AS totalVat,
           eu.total_value AS totalValue,
           eu.awaiting_product_verification AS awaitingProductVerification,
           eu.seller_default_expense_category AS sellerDefaultExpenseCategory,
           eu.invoice_type AS invoiceType,
           eu.seller_id AS sellerId,
           eu.currency AS currency,
           eu.total_value_currency AS totalValueCurrency,
           CASE WHEN eu.source_type = 'MANUAL' AND eu.category = 'MIXED'
                THEN (SELECT string_agg(DISTINCT cei.category, ',' ORDER BY cei.category)
                      FROM company_expense_item cei
                      WHERE cei.company_expense_id = eu.source_id)
           END AS itemCategories
    FROM expense_unified eu
    WHERE eu.expense_date >= :dateFrom
      AND eu.expense_date <= :dateTo
      AND (:categoryCount = 0
           OR eu.category IN (:categories)
           OR (eu.source_type = 'MANUAL'
               AND eu.category = 'MIXED'
               AND EXISTS (SELECT 1
                           FROM company_expense_item cei
                           WHERE cei.company_expense_id = eu.source_id
                             AND cei.category IN (:categories))))
    ORDER BY (CASE WHEN eu.category IS NULL THEN 0 WHEN eu.source_type = 'MANUAL' THEN 1 ELSE 2 END),
             eu.expense_date DESC,
             eu.source_id DESC
    """,
            countQuery = """
    SELECT COUNT(*)
    FROM expense_unified eu
    WHERE eu.expense_date >= :dateFrom
      AND eu.expense_date <= :dateTo
      AND (:categoryCount = 0
           OR eu.category IN (:categories)
           OR (eu.source_type = 'MANUAL'
               AND eu.category = 'MIXED'
               AND EXISTS (SELECT 1
                           FROM company_expense_item cei
                           WHERE cei.company_expense_id = eu.source_id
                             AND cei.category IN (:categories))))
    """,
            nativeQuery = true
    )
    Page<UnifiedExpenseProjection> findUnifiedWithFilters(
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo,
            @Param("categories") List<String> categories,
            @Param("categoryCount") int categoryCount,
            Pageable pageable
    );

    @Query(value = """
        SELECT COALESCE(SUM(eca.total_value), 0)
        FROM expense_category_amount eca
        WHERE eca.expense_date BETWEEN :from AND :to
          AND eca.category IS NOT NULL
          AND eca.category <> 'UNRELATED'
    """, nativeQuery = true)
    Double sumUnifiedTotalExpenses(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    @Query(value = """
        SELECT CAST(EXTRACT(MONTH FROM eca.expense_date) AS INTEGER) AS period,
               COALESCE(SUM(eca.total_value), 0) AS totalExpense
        FROM expense_category_amount eca
        WHERE EXTRACT(YEAR FROM eca.expense_date) = :year
          AND eca.category IS NOT NULL
          AND eca.category <> 'UNRELATED'
        GROUP BY EXTRACT(MONTH FROM eca.expense_date)
        ORDER BY EXTRACT(MONTH FROM eca.expense_date)
    """, nativeQuery = true)
    List<CompanyExpenseProjection> findUnifiedMonthlyExpensesByYear(@Param("year") Integer year);

    @Query(value = """
        SELECT CAST(EXTRACT(DAY FROM eca.expense_date) AS INTEGER) AS period,
               COALESCE(SUM(eca.total_value), 0) AS totalExpense
        FROM expense_category_amount eca
        WHERE EXTRACT(YEAR FROM eca.expense_date) = :year
          AND EXTRACT(MONTH FROM eca.expense_date) = :month
          AND eca.category IS NOT NULL
          AND eca.category <> 'UNRELATED'
        GROUP BY EXTRACT(DAY FROM eca.expense_date)
        ORDER BY EXTRACT(DAY FROM eca.expense_date)
    """, nativeQuery = true)
    List<CompanyExpenseProjection> findUnifiedDailyExpensesByYearAndMonth(
            @Param("year") Integer year,
            @Param("month") Integer month
    );

    @Query(value = """
        SELECT eca.category AS category,
               COALESCE(SUM(eca.total_value), 0) AS totalAmount
        FROM expense_category_amount eca
        WHERE eca.expense_date BETWEEN :from AND :to
          AND eca.category IS NOT NULL
        GROUP BY eca.category
        ORDER BY SUM(eca.total_value) DESC
    """, nativeQuery = true)
    List<UnifiedCategoryExpenseProjection> findUnifiedExpensesByCategory(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );
}

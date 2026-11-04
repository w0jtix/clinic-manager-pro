package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.DTO.CategoryUsageDTO;
import com.clinic.clinicmanager.model.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExpenseCategoryRepo extends JpaRepository<ExpenseCategory, Long> {

    List<ExpenseCategory> findAllByOrderByIdAsc();

    boolean existsByCode(String code);

    @Query("SELECT ec FROM ExpenseCategory ec WHERE LOWER(TRIM(ec.name)) = LOWER(TRIM(:name)) ORDER BY ec.id")
    List<ExpenseCategory> findAllByCategoryName(@Param("name") String name);

    boolean existsByColor(String color);

    boolean existsByColorAndIdNot(String color, Long id);

    @Query("""
            SELECT new com.clinic.clinicmanager.DTO.CategoryUsageDTO(ec.id,
                (SELECT COUNT(ce) FROM CompanyExpense ce WHERE ce.category = ec.code)
                + (SELECT COUNT(cei) FROM CompanyExpenseItem cei WHERE cei.category = ec.code)
                + (SELECT COUNT(ki) FROM KsefInvoice ki WHERE ki.category = ec.code)
                + (SELECT COUNT(ks) FROM KsefSeller ks WHERE ks.defaultExpenseCategory = ec.code))
            FROM ExpenseCategory ec
            ORDER BY ec.id
            """)
    List<CategoryUsageDTO> findCategoryUsage();

    @Query("""
            SELECT (SELECT COUNT(ce) FROM CompanyExpense ce WHERE ce.category = :code)
                + (SELECT COUNT(cei) FROM CompanyExpenseItem cei WHERE cei.category = :code)
                + (SELECT COUNT(ki) FROM KsefInvoice ki WHERE ki.category = :code)
                + (SELECT COUNT(ks) FROM KsefSeller ks WHERE ks.defaultExpenseCategory = :code)
            FROM ExpenseCategory ec
            WHERE ec.code = :code
            """)
    long countUsageByCode(@Param("code") String code);
}

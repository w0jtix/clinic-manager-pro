package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.ClientDebt;
import com.clinic.clinicmanager.model.Voucher;
import com.clinic.clinicmanager.model.constants.DebtType;
import com.clinic.clinicmanager.model.constants.PaymentStatus;
import com.clinic.clinicmanager.model.constants.VoucherStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClientDebtRepo extends JpaRepository<ClientDebt, Long> {
    Optional<ClientDebt> findOneById(Long id);

    Optional<ClientDebt> findOneBySourceVisitId(Long id);

    List<ClientDebt> findAllByClientIdAndPaymentStatus(Long id, PaymentStatus paymentStatus);

    @Query("""
    SELECT cd
    FROM ClientDebt cd
    WHERE (:paymentStatus IS NULL OR cd.paymentStatus = :paymentStatus)
      AND (COALESCE(:keyword, '') = ''
           OR LOWER(cd.client.firstName) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(cd.client.lastName) LIKE LOWER(CONCAT('%', :keyword, '%')))
    """)
    List<ClientDebt> findAllWithFilters(
            @Param("paymentStatus")PaymentStatus paymentStatus,
            @Param("keyword") String keyword
    );

    boolean existsByClientId(Long clientId);

    @Query("SELECT COALESCE(SUM(cd.value), 0) FROM ClientDebt cd JOIN cd.sourceVisit v WHERE v.employee.id = :empId AND cd.createdAt BETWEEN :from AND :to")
    Double sumDebtsCreated(@Param("empId") Long empId, @Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);

    @Query("""
        SELECT COALESCE(SUM(cd.value), 0)
        FROM ClientDebt cd
        WHERE cd.createdAt BETWEEN :from AND :to
          AND cd.type IN (com.clinic.clinicmanager.model.constants.DebtType.PARTIAL_PAYMENT,
                          com.clinic.clinicmanager.model.constants.DebtType.UNPAID)
    """)
    Double sumCompanyUnpaidDebts(@Param("from") java.time.LocalDate from, @Param("to") java.time.LocalDate to);
}

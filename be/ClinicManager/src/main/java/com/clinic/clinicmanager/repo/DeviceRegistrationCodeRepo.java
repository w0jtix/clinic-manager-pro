package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.DeviceRegistrationCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DeviceRegistrationCodeRepo extends JpaRepository<DeviceRegistrationCode, Long> {

    @Query("""
            SELECT c FROM DeviceRegistrationCode c
            WHERE c.usedAt IS NULL
              AND c.cancelledAt IS NULL
              AND c.expiresAt > :now
            ORDER BY c.createdAt DESC, c.id DESC
            """)
    List<DeviceRegistrationCode> findActive(@Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM DeviceRegistrationCode c
            WHERE c.code = :code
              AND c.usedAt IS NULL
              AND c.cancelledAt IS NULL
              AND c.expiresAt > :now
            ORDER BY c.id ASC
            """)
    List<DeviceRegistrationCode> findActiveByCodeForUpdate(
            @Param("code") String code,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query(value = """
            UPDATE device_registration_codes
            SET cancelled_at = :now
            WHERE replace_device_id = :deviceId
              AND used_at IS NULL
              AND cancelled_at IS NULL
            """, nativeQuery = true)
    int cancelPendingForReplacedDevice(
            @Param("deviceId") Long deviceId,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query(value = "UPDATE device_registration_codes SET replace_device_id = NULL WHERE replace_device_id = :deviceId",
            nativeQuery = true)
    int clearReplaceDevice(@Param("deviceId") Long deviceId);

    @Query(value = "SELECT COUNT(*) FROM (SELECT pg_advisory_xact_lock(:key)) AS lock_holder", nativeQuery = true)
    long acquireTransactionLock(@Param("key") long key);

    @Query("""
            SELECT COUNT(c) > 0 FROM DeviceRegistrationCode c
            WHERE c.usedAt IS NULL
              AND c.cancelledAt IS NULL
              AND c.expiresAt > :now
            """)
    boolean existsActive(@Param("now") LocalDateTime now);

    @Query("""
            SELECT COUNT(c) > 0 FROM DeviceRegistrationCode c
            WHERE c.code = :code
              AND c.usedAt IS NULL
              AND c.cancelledAt IS NULL
              AND c.expiresAt > :now
            """)
    boolean existsActiveByCode(
            @Param("code") String code,
            @Param("now") LocalDateTime now
    );

    @Query("""
            SELECT COUNT(c) > 0 FROM DeviceRegistrationCode c
            WHERE LOWER(c.name) = LOWER(:name)
              AND c.usedAt IS NULL
              AND c.cancelledAt IS NULL
              AND c.expiresAt > :now
            """)
    boolean existsActiveByName(
            @Param("name") String name,
            @Param("now") LocalDateTime now
    );
}

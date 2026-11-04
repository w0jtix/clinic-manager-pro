package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.TrustedDevice;
import com.clinic.clinicmanager.model.constants.TrustedDeviceKind;
import com.clinic.clinicmanager.repo.projection.UserDeviceCountProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrustedDeviceRepo extends JpaRepository<TrustedDevice, Long> {

    Optional<TrustedDevice> findByTokenHashAndUserIdAndKind(String tokenHash, Long userId, TrustedDeviceKind kind);

    Optional<TrustedDevice> findFirstByPreviousTokenHashAndUserIdAndKind(String previousTokenHash, Long userId, TrustedDeviceKind kind);

    Optional<TrustedDevice> findByIdAndUserIdAndKind(Long id, Long userId, TrustedDeviceKind kind);

    Optional<TrustedDevice> findByTokenHashAndKind(String tokenHash, TrustedDeviceKind kind);

    Optional<TrustedDevice> findFirstByPreviousTokenHashAndKind(String previousTokenHash, TrustedDeviceKind kind);

    Optional<TrustedDevice> findByIdAndKind(Long id, TrustedDeviceKind kind);

    @Query("""
            SELECT d FROM TrustedDevice d
            WHERE d.kind = :kind
              AND (:includeRevoked = true OR d.revokedAt IS NULL)
            ORDER BY CASE WHEN d.revokedAt IS NULL THEN 0 ELSE 1 END,
                     d.lastUsedAt DESC NULLS LAST,
                     d.id DESC
            """)
    List<TrustedDevice> findAllByKindOrdered(
            @Param("kind") TrustedDeviceKind kind,
            @Param("includeRevoked") boolean includeRevoked
    );

    @Query("""
            SELECT COUNT(d) FROM TrustedDevice d
            WHERE d.kind = :kind
              AND d.revokedAt IS NULL
              AND d.suspectedCopyAt IS NULL
            """)
    long countAllowed(@Param("kind") TrustedDeviceKind kind);

    @Query("""
            SELECT d FROM TrustedDevice d
            WHERE d.kind = :kind
              AND d.revokedAt IS NULL
              AND LOWER(d.name) = LOWER(:name)
            """)
    List<TrustedDevice> findActiveByName(
            @Param("kind") TrustedDeviceKind kind,
            @Param("name") String name
    );

    @Query("""
            SELECT d FROM TrustedDevice d
            WHERE d.userId = :userId
              AND d.kind = :kind
              AND d.revokedAt IS NULL
              AND d.expiresAt > :now
            ORDER BY d.lastUsedAt DESC NULLS LAST, d.id DESC
            """)
    List<TrustedDevice> findActiveOrderByLastUsed(
            @Param("userId") Long userId,
            @Param("kind") TrustedDeviceKind kind,
            @Param("now") LocalDateTime now
    );

    @Query("""
            SELECT COUNT(d) FROM TrustedDevice d
            WHERE d.userId = :userId
              AND d.kind = :kind
              AND d.revokedAt IS NULL
              AND d.expiresAt > :now
            """)
    long countActive(
            @Param("userId") Long userId,
            @Param("kind") TrustedDeviceKind kind,
            @Param("now") LocalDateTime now
    );

    @Query("""
            SELECT d.userId AS userId, COUNT(d) AS deviceCount FROM TrustedDevice d
            WHERE d.userId IS NOT NULL
              AND d.kind = :kind
              AND d.revokedAt IS NULL
              AND d.expiresAt > :now
            GROUP BY d.userId
            """)
    List<UserDeviceCountProjection> countActiveGroupedByUser(
            @Param("kind") TrustedDeviceKind kind,
            @Param("now") LocalDateTime now
    );

    @Modifying
    @Query("UPDATE TrustedDevice d SET d.replacedById = NULL WHERE d.replacedById = :deviceId")
    int clearReplacedBy(@Param("deviceId") Long deviceId);

    @Modifying
    @Query("""
            UPDATE TrustedDevice d SET d.revokedAt = :now
            WHERE d.userId = :userId
              AND d.kind = :kind
              AND d.revokedAt IS NULL
            """)
    int revokeAll(
            @Param("userId") Long userId,
            @Param("kind") TrustedDeviceKind kind,
            @Param("now") LocalDateTime now
    );
}

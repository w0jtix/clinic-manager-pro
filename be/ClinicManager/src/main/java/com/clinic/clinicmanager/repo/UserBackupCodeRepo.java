package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.UserBackupCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserBackupCodeRepo extends JpaRepository<UserBackupCode, Long> {

    List<UserBackupCode> findAllByUserIdAndUsedAtIsNull(Long userId);

    int countByUserIdAndUsedAtIsNull(Long userId);

    @Modifying
    @Query("UPDATE UserBackupCode c SET c.usedAt = :usedAt WHERE c.id = :id AND c.usedAt IS NULL")
    int markUsed(@Param("id") Long id, @Param("usedAt") LocalDateTime usedAt);

    @Modifying
    @Query("DELETE FROM UserBackupCode c WHERE c.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}

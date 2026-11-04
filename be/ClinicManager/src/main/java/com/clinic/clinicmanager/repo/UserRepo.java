package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.repo.projection.EmployeeAccountRoleProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepo extends JpaRepository<User, Long> {

    Optional<User> findOneById(Long id);

    Optional<User> findByUsername(String username);

    Boolean existsByUsername(String username);

    Optional<User> findByEmployeeId(Long employeeId);

    @Query("SELECT u.avatar FROM User u WHERE u.employee.id = :employeeId")
    String findAvatarByEmployeeId(@Param("employeeId") Long employeeId);

    boolean existsByAvatarAndIdNot(String avatar, Long id);

    @Query("SELECT u.avatar FROM User u WHERE u.avatar IS NOT NULL")
    List<String> findAllTakenAvatars();

    @Query("SELECT u.avatar FROM User u WHERE u.id <> :userId AND u.avatar IS NOT NULL")
    List<String> findAvatarsTakenByOthers(@Param("userId") Long userId);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.employee WHERE u.id IN :ids")
    List<User> findAllWithEmployeeByIdIn(@Param("ids") Collection<Long> ids);

    @Query("""
            SELECT u.employee.id AS employeeId, u.id AS userId, u.avatar AS avatar, r.name AS roleName
            FROM User u JOIN u.roles r
            WHERE u.employee.id IN :employeeIds
            """)
    List<EmployeeAccountRoleProjection> findAccountRolesByEmployeeIdIn(@Param("employeeIds") Collection<Long> employeeIds);

}

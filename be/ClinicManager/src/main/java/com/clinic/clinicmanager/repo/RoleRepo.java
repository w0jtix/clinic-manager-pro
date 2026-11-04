package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.Role;
import com.clinic.clinicmanager.model.constants.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepo extends JpaRepository<Role, Long> {
    Optional<Role> findByName(RoleType name);
}

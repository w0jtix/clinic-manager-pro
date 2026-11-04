package com.clinic.clinicmanager.repo;

import com.clinic.clinicmanager.model.KsefCredentials;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KsefCredentialsRepo extends JpaRepository<KsefCredentials, Long> {

    default Optional<KsefCredentials> getCredentials() {
        return findById(1L);
    }
}

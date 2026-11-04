package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.DTO.KsefCredentialsDTO;
import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.CreationException;
import com.clinic.clinicmanager.exceptions.DeletionException;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.KsefCredentials;
import com.clinic.clinicmanager.repo.KsefCredentialsRepo;
import com.clinic.clinicmanager.service.AuditLogService;
import com.clinic.clinicmanager.service.KsefCredentialsService;
import com.clinic.clinicmanager.utils.AesEncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KsefCredentialsServiceImpl implements KsefCredentialsService {

    private final KsefCredentialsRepo credentialsRepo;
    private final AesEncryptionUtil encryptionUtil;
    private final AuditLogService auditLogService;
    private final KsefAuthService ksefAuthService;

    @Override
    public KsefCredentialsDTO getCredentials() {
        KsefCredentials credentials = credentialsRepo.getCredentials()
                .orElseThrow(() -> new ResourceNotFoundException("KSeF credentials not configured."));
        return new KsefCredentialsDTO(credentials);
    }

    @Override
    @Transactional
    public KsefCredentialsDTO saveCredentials(KsefCredentialsDTO dto) {
        try {
            KsefCredentials credentials = dto.toEntity(encryptionUtil);
            KsefCredentials saved = credentialsRepo.save(credentials);
            ksefAuthService.invalidateCache();
            auditLogService.logCreate("KsefCredentials", saved.getId(), null, new KsefCredentialsDTO(saved));
            return new KsefCredentialsDTO(saved);
        } catch (ResourceNotFoundException | ConflictException | ValidationException | AccessDeniedException
                 | CreationException | UpdateException | DeletionException e) {
            throw e;
        } catch (Exception e) {
            throw new UpdateException("Failed to save KSeF credentials.", e);
        }
    }
}

package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.DTO.KsefCredentialsDTO;
import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.exceptions.UpdateException;
import com.clinic.clinicmanager.model.KsefCredentials;
import com.clinic.clinicmanager.repo.KsefCredentialsRepo;
import com.clinic.clinicmanager.service.impl.KsefAuthService;
import com.clinic.clinicmanager.service.impl.KsefCredentialsServiceImpl;
import com.clinic.clinicmanager.utils.AesEncryptionUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KsefCredentialsServiceImplTest {

    @Mock KsefCredentialsRepo credentialsRepo;
    @Mock AesEncryptionUtil encryptionUtil;
    @Mock AuditLogService auditLogService;
    @Mock KsefAuthService ksefAuthService;
    @InjectMocks KsefCredentialsServiceImpl credentialsService;

    private KsefCredentials defaultCredentials() {
        return KsefCredentials.builder()
                .id(1L)
                .nip("1234567890")
                .encryptedToken("encrypted-token")
                .build();
    }

    @Test
    void getCredentials_shouldReturnDTOWithMaskedToken() {
        when(credentialsRepo.getCredentials()).thenReturn(Optional.of(defaultCredentials()));

        KsefCredentialsDTO result = credentialsService.getCredentials();

        assertNotNull(result);
        assertEquals("1234567890", result.getNip());
        assertEquals("****", result.getToken());
    }
    @Test
    void getCredentials_shouldThrowResourceNotFoundException_whenNotConfigured() {
        when(credentialsRepo.getCredentials()).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> credentialsService.getCredentials());
    }

    @Test
    void saveCredentials_shouldEncryptToken_andSave() {
        KsefCredentialsDTO dto = new KsefCredentialsDTO();
        dto.setNip("1234567890");
        dto.setToken("plain-token");

        when(encryptionUtil.encrypt("plain-token")).thenReturn("encrypted-token");
        when(credentialsRepo.save(any(KsefCredentials.class))).thenReturn(defaultCredentials());

        credentialsService.saveCredentials(dto);

        verify(encryptionUtil).encrypt("plain-token");
        verify(credentialsRepo).save(argThat(c ->
                c.getId().equals(1L) &&
                c.getEncryptedToken().equals("encrypted-token")
        ));
    }
    @Test
    void saveCredentials_shouldReturnDTOWithMaskedToken() {
        KsefCredentialsDTO dto = new KsefCredentialsDTO();
        dto.setNip("1234567890");
        dto.setToken("plain-token");

        when(encryptionUtil.encrypt(any())).thenReturn("encrypted-token");
        when(credentialsRepo.save(any(KsefCredentials.class))).thenReturn(defaultCredentials());

        KsefCredentialsDTO result = credentialsService.saveCredentials(dto);

        assertEquals("****", result.getToken());
        assertEquals("1234567890", result.getNip());
    }
    @Test
    void saveCredentials_shouldCallAuditLog() {
        KsefCredentialsDTO dto = new KsefCredentialsDTO();
        dto.setNip("1234567890");
        dto.setToken("plain-token");

        when(encryptionUtil.encrypt(any())).thenReturn("encrypted-token");
        when(credentialsRepo.save(any(KsefCredentials.class))).thenReturn(defaultCredentials());

        credentialsService.saveCredentials(dto);

        verify(auditLogService).logCreate(eq("KsefCredentials"), eq(1L), isNull(), any(KsefCredentialsDTO.class));
    }
    @Test
    void saveCredentials_shouldThrowUpdateException_whenSaveFails() {
        KsefCredentialsDTO dto = new KsefCredentialsDTO();
        dto.setNip("1234567890");
        dto.setToken("plain-token");

        when(encryptionUtil.encrypt(any())).thenReturn("encrypted-token");
        when(credentialsRepo.save(any(KsefCredentials.class))).thenThrow(new RuntimeException("DB error"));

        assertThrows(UpdateException.class, () -> credentialsService.saveCredentials(dto));
    }
}

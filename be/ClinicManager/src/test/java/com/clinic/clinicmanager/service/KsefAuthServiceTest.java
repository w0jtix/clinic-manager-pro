package com.clinic.clinicmanager.service;

import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.repo.KsefCredentialsRepo;
import com.clinic.clinicmanager.service.impl.KsefAuthService;
import com.clinic.clinicmanager.utils.AesEncryptionUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import pl.akmf.ksef.sdk.api.DefaultKsefClient;
import pl.akmf.ksef.sdk.api.services.DefaultCryptographyService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KsefAuthServiceTest {

    @Mock private KsefCredentialsRepo credentialsRepo;
    @Mock private AesEncryptionUtil aesEncryptionUtil;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private DefaultKsefClient ksefClient;
    @Mock private DefaultCryptographyService cryptographyService;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private KsefAuthService ksefAuthService;

    @Test
    void getAccessToken_returnsCachedToken_whenRedisHit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ksef:access_token")).thenReturn("encrypted-token");
        when(aesEncryptionUtil.decrypt("encrypted-token")).thenReturn("plain-access-token");

        String result = ksefAuthService.getAccessToken();

        assertEquals("plain-access-token", result);
        verifyNoInteractions(credentialsRepo, ksefClient, cryptographyService);
    }

    @Test
    void getAccessToken_throwsResourceNotFoundException_whenNoCachedTokenAndNoCredentials() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("ksef:access_token")).thenReturn(null);
        when(credentialsRepo.getCredentials()).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> ksefAuthService.getAccessToken());
    }
}

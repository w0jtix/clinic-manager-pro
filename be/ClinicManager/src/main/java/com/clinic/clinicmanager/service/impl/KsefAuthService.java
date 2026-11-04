package com.clinic.clinicmanager.service.impl;

import com.clinic.clinicmanager.exceptions.ResourceNotFoundException;
import com.clinic.clinicmanager.model.KsefCredentials;
import com.clinic.clinicmanager.repo.KsefCredentialsRepo;
import com.clinic.clinicmanager.utils.AesEncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import pl.akmf.ksef.sdk.api.DefaultKsefClient;
import pl.akmf.ksef.sdk.api.builders.auth.AuthKsefTokenRequestBuilder;
import pl.akmf.ksef.sdk.api.services.DefaultCryptographyService;
import pl.akmf.ksef.sdk.client.model.auth.AuthKsefTokenRequest;
import pl.akmf.ksef.sdk.client.model.auth.AuthOperationStatusResponse;
import pl.akmf.ksef.sdk.client.model.auth.AuthStatus;
import pl.akmf.ksef.sdk.client.model.auth.AuthenticationChallengeResponse;
import pl.akmf.ksef.sdk.client.model.auth.AuthenticationTokenRefreshResponse;
import pl.akmf.ksef.sdk.client.model.auth.ContextIdentifier;
import pl.akmf.ksef.sdk.client.model.auth.SignatureResponse;

import java.time.Duration;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class KsefAuthService {

    private static final String REDIS_ACCESS_KEY = "ksef:access_token";
    private static final String REDIS_REFRESH_KEY = "ksef:refresh_token";
    private static final int ACCESS_TOKEN_TTL_SECONDS = 850;
    private static final long REFRESH_TOKEN_TTL_SECONDS = 604000L;
    private static final int MAX_POLL_ATTEMPTS = 30;
    private static final long POLL_INTERVAL_MS = 2000L;

    private final KsefCredentialsRepo credentialsRepo;
    private final AesEncryptionUtil aesEncryptionUtil;
    private final StringRedisTemplate redisTemplate;
    private final DefaultKsefClient ksefClient;
    private final DefaultCryptographyService cryptographyService;

    public void invalidateCache() {
        redisTemplate.delete(REDIS_ACCESS_KEY);
        redisTemplate.delete(REDIS_REFRESH_KEY);
        log.info("KSeF token cache invalidated");
    }

    public String getAccessToken() {
        String encryptedAccess = redisTemplate.opsForValue().get(REDIS_ACCESS_KEY);
        if (encryptedAccess != null) {
            return aesEncryptionUtil.decrypt(encryptedAccess);
        }

        KsefCredentials credentials = credentialsRepo.getCredentials()
                .orElseThrow(() -> new ResourceNotFoundException("KSeF credentials not configured"));

        String encryptedRefresh = redisTemplate.opsForValue().get(REDIS_REFRESH_KEY);
        if (encryptedRefresh != null) {
            return refreshAndCache(aesEncryptionUtil.decrypt(encryptedRefresh));
        }

        return authenticate(credentials);
    }

    private String authenticate(KsefCredentials credentials) {
        try {
            String nip = credentials.getNip();
            String ksefToken = aesEncryptionUtil.decrypt(credentials.getEncryptedToken());

            AuthenticationChallengeResponse challenge = ksefClient.getAuthChallenge();

            byte[] encryptedBytes = cryptographyService.encryptKsefTokenUsingPublicKey(
                    ksefToken, challenge.getTimestamp());

            AuthKsefTokenRequest request = new AuthKsefTokenRequestBuilder()
                    .withChallenge(challenge.getChallenge())
                    .withContextIdentifier(new ContextIdentifier(ContextIdentifier.IdentifierType.NIP, nip))
                    .withEncryptedToken(Base64.getEncoder().encodeToString(encryptedBytes))
                    .withPublicKeyId(cryptographyService.getKsefToken().getPublicKeyId())
                    .build();

            SignatureResponse response = ksefClient.authenticateByKSeFToken(request);
            String tempToken = response.getAuthenticationToken().getToken();
            String referenceNumber = response.getReferenceNumber();

            pollUntilAccepted(referenceNumber, tempToken);

            AuthOperationStatusResponse tokens = ksefClient.redeemToken(tempToken);
            String accessToken = tokens.getAccessToken().getToken();
            String refreshToken = tokens.getRefreshToken().getToken();

            cacheTokens(accessToken, refreshToken);
            log.info("KSeF authenticated successfully");
            return accessToken;
        } catch (Exception e) {
            throw new RuntimeException("KSeF authentication failed", e);
        }
    }

    private String refreshAndCache(String refreshToken) {
        try {
            AuthenticationTokenRefreshResponse response = ksefClient.refreshAccessToken(refreshToken);
            String newAccessToken = response.getAccessToken().getToken();

            redisTemplate.opsForValue().set(
                    REDIS_ACCESS_KEY,
                    aesEncryptionUtil.encrypt(newAccessToken),
                    Duration.ofSeconds(ACCESS_TOKEN_TTL_SECONDS));

            log.info("KSeF access token refreshed");
            return newAccessToken;
        } catch (Exception e) {
            throw new RuntimeException("KSeF token refresh failed", e);
        }
    }

    private void pollUntilAccepted(String referenceNumber, String tempToken) throws Exception {
        for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
            Thread.sleep(POLL_INTERVAL_MS);
            AuthStatus status = ksefClient.getAuthStatus(referenceNumber, tempToken);
            if (status != null && status.getStatus().getCode() == 200) {
                return;
            }
        }
        throw new RuntimeException("KSeF auth: did not reach accepted status after " + MAX_POLL_ATTEMPTS + " attempts");
    }

    private void cacheTokens(String accessToken, String refreshToken) {
        redisTemplate.opsForValue().set(
                REDIS_ACCESS_KEY,
                aesEncryptionUtil.encrypt(accessToken),
                Duration.ofSeconds(ACCESS_TOKEN_TTL_SECONDS));
        redisTemplate.opsForValue().set(
                REDIS_REFRESH_KEY,
                aesEncryptionUtil.encrypt(refreshToken),
                Duration.ofSeconds(REFRESH_TOKEN_TTL_SECONDS));
    }
}

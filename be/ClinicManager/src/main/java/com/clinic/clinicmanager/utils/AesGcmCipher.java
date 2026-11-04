package com.clinic.clinicmanager.utils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

public class AesGcmCipher {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKey secretKey;

    public AesGcmCipher(String base64Key, String propertyName) {
        String trimmedKey = base64Key == null ? null : base64Key.strip();
        if (trimmedKey == null || trimmedKey.isEmpty()) {
            throw new IllegalStateException(
                    propertyName + " is not set. Generate one with: openssl rand -base64 32");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(trimmedKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    propertyName + " is not valid Base64. Generate one with: openssl rand -base64 32", e);
        }
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException(
                    propertyName + " must decode to 16, 24 or 32 bytes (AES-128/192/256), but was "
                            + keyBytes.length + " bytes. Generate one with: openssl rand -base64 32");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            return Base64.getEncoder().encodeToString(encryptRaw(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String ciphertext) {
        try {
            return new String(decryptRaw(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    public byte[] encryptBytes(byte[] plaintext) {
        try {
            return encryptRaw(plaintext);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public byte[] decryptBytes(byte[] ciphertext) {
        try {
            return decryptRaw(ciphertext);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    private byte[] encryptRaw(byte[] plaintext) throws Exception {
        byte[] iv = new byte[GCM_IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

        byte[] encrypted = cipher.doFinal(plaintext);
        byte[] result = new byte[GCM_IV_LENGTH + encrypted.length];
        System.arraycopy(iv, 0, result, 0, GCM_IV_LENGTH);
        System.arraycopy(encrypted, 0, result, GCM_IV_LENGTH, encrypted.length);
        return result;
    }

    private byte[] decryptRaw(byte[] ivAndEncrypted) throws Exception {
        if (ivAndEncrypted.length <= GCM_IV_LENGTH) {
            throw new IllegalArgumentException("Ciphertext too short to contain IV and payload");
        }
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] encrypted = new byte[ivAndEncrypted.length - GCM_IV_LENGTH];
        System.arraycopy(ivAndEncrypted, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(ivAndEncrypted, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
        return cipher.doFinal(encrypted);
    }
}

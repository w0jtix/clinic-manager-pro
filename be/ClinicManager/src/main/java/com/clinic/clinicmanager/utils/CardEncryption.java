package com.clinic.clinicmanager.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
public class CardEncryption {

    static final String PREFIX_V1 = "v1:";
    static final byte[] BYTES_HEADER_V1 = "PCv1".getBytes(StandardCharsets.US_ASCII);

    private final AesGcmCipher cipher;

    public CardEncryption(@Value("${card.encryption-key}") String base64Key) {
        this.cipher = new AesGcmCipher(base64Key, "card.encryption-key");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        return PREFIX_V1 + cipher.encrypt(plaintext);
    }

    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX_V1)) {
            throw new IllegalStateException("Unsupported card ciphertext format.");
        }
        return cipher.decrypt(stored.substring(PREFIX_V1.length()));
    }

    public byte[] encryptBytes(byte[] plaintext) {
        byte[] encrypted = cipher.encryptBytes(plaintext);
        byte[] result = new byte[BYTES_HEADER_V1.length + encrypted.length];
        System.arraycopy(BYTES_HEADER_V1, 0, result, 0, BYTES_HEADER_V1.length);
        System.arraycopy(encrypted, 0, result, BYTES_HEADER_V1.length, encrypted.length);
        return result;
    }

    public byte[] decryptBytes(byte[] stored) {
        if (stored == null || stored.length < BYTES_HEADER_V1.length
                || !Arrays.equals(stored, 0, BYTES_HEADER_V1.length, BYTES_HEADER_V1, 0, BYTES_HEADER_V1.length)) {
            throw new IllegalStateException("Unsupported card file format.");
        }
        return cipher.decryptBytes(Arrays.copyOfRange(stored, BYTES_HEADER_V1.length, stored.length));
    }
}

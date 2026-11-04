package com.clinic.clinicmanager.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MfaEncryption {

    private static final String PREFIX_V1 = "v1:";

    private final AesGcmCipher cipher;

    public MfaEncryption(@Value("${mfa.encryption-key}") String base64Key) {
        this.cipher = new AesGcmCipher(base64Key, "mfa.encryption-key");
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
            throw new IllegalStateException("Unsupported MFA ciphertext format.");
        }
        return cipher.decrypt(stored.substring(PREFIX_V1.length()));
    }
}

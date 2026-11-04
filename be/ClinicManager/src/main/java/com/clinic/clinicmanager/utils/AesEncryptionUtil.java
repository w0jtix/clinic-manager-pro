package com.clinic.clinicmanager.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AesEncryptionUtil {

    private final AesGcmCipher cipher;

    public AesEncryptionUtil(@Value("${ksef.encryption-key}") String base64Key) {
        this.cipher = new AesGcmCipher(base64Key, "ksef.encryption-key");
    }

    public String encrypt(String plaintext) {
        return cipher.encrypt(plaintext);
    }

    public String decrypt(String ciphertext) {
        return cipher.decrypt(ciphertext);
    }
}

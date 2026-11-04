package com.clinic.clinicmanager.utils;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmCipherTest {

    private static final String KSEF_TEST_KEY = "CcQ8fQS1QVmO8OZGummzyYg5SJVHWgSyp2II+haVOzc=";
    private static final String OTHER_KEY = "0M4x9vvId7mHSSU2GWach6D8+iwTN7Bp2JnvXW2T7Ao=";
    private static final String LEGACY_KSEF_CIPHERTEXT = "b+EU7Ng8S8CdGbicZVecBfOivuxJcF3Zw3o8qYyuBB+Ipa2jC3jCMBaIfWe59lZVJwk=";
    private static final String LEGACY_KSEF_PLAINTEXT = "legacy-ksef-token-ąę";

    @Test
    void encryptDecrypt_roundtrip() {
        AesGcmCipher cipher = new AesGcmCipher(KSEF_TEST_KEY, "test.key");

        String encrypted = cipher.encrypt("Cukrzyca typu 2, zażółć gęślą jaźń");

        assertThat(encrypted).isNotEqualTo("Cukrzyca typu 2, zażółć gęślą jaźń");
        assertThat(cipher.decrypt(encrypted)).isEqualTo("Cukrzyca typu 2, zażółć gęślą jaźń");
    }

    @Test
    void encrypt_usesRandomIv() {
        AesGcmCipher cipher = new AesGcmCipher(KSEF_TEST_KEY, "test.key");

        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    void decrypt_legacyKsefCiphertext() {
        AesGcmCipher cipher = new AesGcmCipher(KSEF_TEST_KEY, "ksef.encryption-key");

        assertThat(cipher.decrypt(LEGACY_KSEF_CIPHERTEXT)).isEqualTo(LEGACY_KSEF_PLAINTEXT);
    }

    @Test
    void constructor_trimsSurroundingWhitespaceAndNewlines() {
        AesGcmCipher padded = new AesGcmCipher("  " + KSEF_TEST_KEY + " \r\n", "test.key");
        AesGcmCipher clean = new AesGcmCipher(KSEF_TEST_KEY, "test.key");

        assertThat(clean.decrypt(padded.encrypt("klucz ze spacja"))).isEqualTo("klucz ze spacja");
        assertThat(padded.decrypt(LEGACY_KSEF_CIPHERTEXT)).isEqualTo(LEGACY_KSEF_PLAINTEXT);
    }

    @Test
    void constructor_rejectsWhitespaceOnlyKeyAsNotSet() {
        assertThatThrownBy(() -> new AesGcmCipher(" \n\t", "test.key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("test.key is not set");
    }

    @Test
    void aesEncryptionUtil_decryptsLegacyKsefCiphertext() {
        AesEncryptionUtil util = new AesEncryptionUtil(KSEF_TEST_KEY);

        assertThat(util.decrypt(LEGACY_KSEF_CIPHERTEXT)).isEqualTo(LEGACY_KSEF_PLAINTEXT);
        assertThat(util.decrypt(util.encrypt("token"))).isEqualTo("token");
    }

    @Test
    void decrypt_withWrongKey_throws() {
        AesGcmCipher other = new AesGcmCipher(OTHER_KEY, "test.key");

        assertThatThrownBy(() -> other.decrypt(LEGACY_KSEF_CIPHERTEXT))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Decryption failed");
    }

    @Test
    void decrypt_tamperedCiphertext_throws() {
        AesGcmCipher cipher = new AesGcmCipher(KSEF_TEST_KEY, "test.key");
        String encrypted = cipher.encrypt("payload");
        String tampered = encrypted.substring(0, encrypted.length() - 4) + (encrypted.endsWith("AAAA") ? "BBBB" : "AAAA");

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void constructor_rejectsInvalidBase64() {
        assertThatThrownBy(() -> new AesGcmCipher("not base64 !!", "card.encryption-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("card.encryption-key is not valid Base64");
    }

    @Test
    void constructor_rejectsWrongKeyLength() {
        assertThatThrownBy(() -> new AesGcmCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAA=", "card.encryption-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("card.encryption-key must decode to 16, 24 or 32 bytes");
    }

    @Test
    void constructor_rejectsMissingKey() {
        assertThatThrownBy(() -> new AesGcmCipher(null, "card.encryption-key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("card.encryption-key is not set");
        assertThatThrownBy(() -> new AesGcmCipher("  ", "card.encryption-key"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructor_acceptsAes128And192Keys() {
        AesGcmCipher aes128 = new AesGcmCipher("AAAAAAAAAAAAAAAAAAAAAA==", "x");
        AesGcmCipher aes192 = new AesGcmCipher("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "x");

        assertThat(aes128.decrypt(aes128.encrypt("a"))).isEqualTo("a");
        assertThat(aes192.decrypt(aes192.encrypt("b"))).isEqualTo("b");
    }

    @Test
    void encryptBytes_roundtrip_withIvPrefix() {
        AesGcmCipher cipher = new AesGcmCipher(KSEF_TEST_KEY, "test.key");
        byte[] plaintext = {(byte) 0x89, 'P', 'N', 'G', 0, 1, 2, 3, (byte) 0xFF};

        byte[] encrypted = cipher.encryptBytes(plaintext);

        assertThat(encrypted).hasSize(12 + plaintext.length + 16);
        assertThat(cipher.decryptBytes(encrypted)).isEqualTo(plaintext);
    }

    @Test
    void encryptBytes_usesRandomIv() {
        AesGcmCipher cipher = new AesGcmCipher(KSEF_TEST_KEY, "test.key");
        byte[] plaintext = {1, 2, 3};

        assertThat(cipher.encryptBytes(plaintext)).isNotEqualTo(cipher.encryptBytes(plaintext));
    }

    @Test
    void decryptBytes_tamperedOrWrongKey_throws() {
        AesGcmCipher cipher = new AesGcmCipher(KSEF_TEST_KEY, "test.key");
        AesGcmCipher other = new AesGcmCipher(OTHER_KEY, "test.key");
        byte[] encrypted = cipher.encryptBytes(new byte[]{1, 2, 3, 4});
        byte[] tampered = encrypted.clone();
        tampered[tampered.length - 1] ^= 0x01;

        assertThatThrownBy(() -> cipher.decryptBytes(tampered))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Decryption failed");
        assertThatThrownBy(() -> other.decryptBytes(encrypted)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> cipher.decryptBytes(new byte[12])).isInstanceOf(RuntimeException.class);
    }

    @Test
    void textAndBytesFormats_areCompatible() {
        AesGcmCipher cipher = new AesGcmCipher(KSEF_TEST_KEY, "test.key");

        byte[] raw = Base64.getDecoder().decode(cipher.encrypt("tekst"));

        assertThat(new String(cipher.decryptBytes(raw), StandardCharsets.UTF_8)).isEqualTo("tekst");
    }
}

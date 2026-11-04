package com.clinic.clinicmanager.utils;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardEncryptionTest {

    private static final String CARD_KEY = "0M4x9vvId7mHSSU2GWach6D8+iwTN7Bp2JnvXW2T7Ao=";
    private static final String KSEF_KEY = "CcQ8fQS1QVmO8OZGummzyYg5SJVHWgSyp2II+haVOzc=";

    private final CardEncryption cardEncryption = new CardEncryption(CARD_KEY);

    @Test
    void encrypt_addsV1Prefix_andRoundtrips() {
        String encrypted = cardEncryption.encrypt("Metformina 500 mg");

        assertThat(encrypted).startsWith("v1:");
        assertThat(encrypted).doesNotContain("Metformina");
        assertThat(cardEncryption.decrypt(encrypted)).isEqualTo("Metformina 500 mg");
    }

    @Test
    void nullStaysNull() {
        assertThat(cardEncryption.encrypt(null)).isNull();
        assertThat(cardEncryption.decrypt(null)).isNull();
    }

    @Test
    void decrypt_withoutPrefix_throws() {
        String raw = new AesGcmCipher(CARD_KEY, "x").encrypt("tekst");

        assertThatThrownBy(() -> cardEncryption.decrypt(raw))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_withUnknownPrefix_throws() {
        String encrypted = cardEncryption.encrypt("tekst");

        assertThatThrownBy(() -> cardEncryption.decrypt("v2:" + encrypted.substring(3)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_withDifferentKey_throws() {
        String encrypted = cardEncryption.encrypt("tekst");
        CardEncryption otherKey = new CardEncryption(KSEF_KEY);

        assertThatThrownBy(() -> otherKey.decrypt(encrypted)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void constructor_rejectsInvalidKey() {
        assertThatThrownBy(() -> new CardEncryption("short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("card.encryption-key");
        assertThatThrownBy(() -> new CardEncryption(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encryptBytes_addsHeader_andRoundtrips() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 7, 7, 7};

        byte[] encrypted = cardEncryption.encryptBytes(png);

        assertThat(new String(encrypted, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("PCv1");
        assertThat(encrypted).hasSize(4 + 12 + png.length + 16);
        assertThat(cardEncryption.decryptBytes(encrypted)).isEqualTo(png);
    }

    @Test
    void decryptBytes_withUnknownHeader_throws() {
        byte[] encrypted = cardEncryption.encryptBytes(new byte[]{1, 2, 3});
        byte[] otherHeader = encrypted.clone();
        otherHeader[3] = '2';
        byte[] raw = new AesGcmCipher(CARD_KEY, "x").encryptBytes(new byte[]{1, 2, 3});

        assertThatThrownBy(() -> cardEncryption.decryptBytes(otherHeader))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cardEncryption.decryptBytes(raw))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cardEncryption.decryptBytes(new byte[]{'P', 'C'}))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cardEncryption.decryptBytes(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decryptBytes_withDifferentKey_throws() {
        byte[] encrypted = cardEncryption.encryptBytes(new byte[]{1, 2, 3});

        assertThatThrownBy(() -> new CardEncryption(KSEF_KEY).decryptBytes(encrypted))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void textMethods_unchangedByBytesSupport() {
        String encrypted = cardEncryption.encrypt("Metformina");

        assertThat(encrypted).startsWith("v1:");
        assertThat(cardEncryption.decrypt(encrypted)).isEqualTo("Metformina");
    }
}

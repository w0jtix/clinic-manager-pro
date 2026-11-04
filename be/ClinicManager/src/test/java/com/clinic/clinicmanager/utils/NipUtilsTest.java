package com.clinic.clinicmanager.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class NipUtilsTest {

    @Test
    void normalize_trimsAndRemovesSpacesAndDashes() {
        assertEquals("5261040828", NipUtils.normalize("  526-104-08 28 "));
    }

    @Test
    void normalize_returnsNull_whenNull() {
        assertNull(NipUtils.normalize(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"5261040828", "7740001454", "1234563218"})
    void isValid_returnsTrue_forCorrectChecksum(String nip) {
        assertTrue(NipUtils.isValid(nip));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "5261040829", "526104082", "52610408281", "526104082A", ""})
    void isValid_returnsFalse_forWrongChecksumLengthOrCharacters(String nip) {
        assertFalse(NipUtils.isValid(nip));
    }

    @Test
    void isValid_returnsFalse_whenChecksumModuloIsTen() {
        assertFalse(NipUtils.isValid("0200000000"));
        assertFalse(NipUtils.isValid("0200000009"));
    }

    @Test
    void isValid_returnsFalse_whenNull() {
        assertFalse(NipUtils.isValid(null));
    }

    @Test
    void normalizeTaxId_removesSeparatorsAndUppercases_forForeignVatId() {
        assertEquals("DE123456789", NipUtils.normalizeTaxId(" de-123.456 789 "));
    }

    @Test
    void normalizeTaxId_stripsPolishPrefix_whenFollowedByTenDigits() {
        assertEquals("5260001246", NipUtils.normalizeTaxId("PL 526-000-12-46"));
        assertTrue(NipUtils.isValidTaxId(NipUtils.normalizeTaxId("PL 526-000-12-46")));
    }

    @Test
    void normalizeTaxId_stripsLowercasePolishPrefix() {
        assertEquals("5261040828", NipUtils.normalizeTaxId("pl5261040828"));
    }

    @Test
    void normalizeTaxId_keepsPolishPrefix_whenNotFollowedByTenDigits() {
        assertEquals("PL123", NipUtils.normalizeTaxId("pl 123"));
    }

    @Test
    void normalizeTaxId_keepsPlainPolishNip() {
        assertEquals("5261040828", NipUtils.normalizeTaxId("  526-104-08 28 "));
    }

    @Test
    void normalizeTaxId_returnsNull_whenNull() {
        assertNull(NipUtils.normalizeTaxId(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"5261040828", "5260001246", "DE123456789", "ATU12345678", "NL123456789B01",
            "FR12345678901", "DE12"})
    void isValidTaxId_returnsTrue_forPolishNipOrForeignVatId(String taxId) {
        assertTrue(NipUtils.isValidTaxId(taxId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234567890", "5261040829", "526104082", "PL123", "PL5261040828", "D1234", "DE1",
            "DE1234567890123", "DE12345#789", "de123456789", "1E123456789", ""})
    void isValidTaxId_returnsFalse_forInvalidIdentifiers(String taxId) {
        assertFalse(NipUtils.isValidTaxId(taxId));
    }

    @Test
    void isValidTaxId_returnsFalse_whenNull() {
        assertFalse(NipUtils.isValidTaxId(null));
    }
}

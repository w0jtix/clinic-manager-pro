package com.clinic.clinicmanager.utils;

import com.clinic.clinicmanager.exceptions.ValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyPinValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"482915", "739164", "102938", "123457", "890123", "009988"})
    void validate_acceptsStrongPins(String pin) {
        assertThatCode(() -> CompanyPinValidator.validate(pin)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345", "1234567", "12a456", " 48291", "48291 ", "４８２９１５"})
    void validate_rejectsInvalidFormat(String pin) {
        assertThatThrownBy(() -> CompanyPinValidator.validate(pin))
                .isInstanceOf(ValidationException.class)
                .hasMessage("PIN musi składać się dokładnie z 6 cyfr.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"000000", "111111", "999999"})
    void validate_rejectsRepeatedDigits(String pin) {
        assertThatThrownBy(() -> CompanyPinValidator.validate(pin))
                .isInstanceOf(ValidationException.class)
                .hasMessage("PIN nie może składać się z samych jednakowych cyfr.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456", "012345", "456789", "654321", "987654", "543210"})
    void validate_rejectsSequences(String pin) {
        assertThatThrownBy(() -> CompanyPinValidator.validate(pin))
                .isInstanceOf(ValidationException.class)
                .hasMessage("PIN nie może być ciągiem kolejnych cyfr (np. 123456 lub 654321).");
    }
}

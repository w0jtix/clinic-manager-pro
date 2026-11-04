package com.clinic.clinicmanager.utils;

import com.clinic.clinicmanager.exceptions.ValidationException;
import lombok.experimental.UtilityClass;

import java.util.regex.Pattern;

@UtilityClass
public class CompanyPinValidator {

    private static final Pattern PIN_PATTERN = Pattern.compile("[0-9]{6}");

    public static void validate(String pin) {
        if (pin == null || !PIN_PATTERN.matcher(pin).matches()) {
            throw new ValidationException("PIN musi składać się dokładnie z 6 cyfr.");
        }
        if (isSequence(pin, 0)) {
            throw new ValidationException("PIN nie może składać się z samych jednakowych cyfr.");
        }
        if (isSequence(pin, 1) || isSequence(pin, -1)) {
            throw new ValidationException("PIN nie może być ciągiem kolejnych cyfr (np. 123456 lub 654321).");
        }
    }

    private static boolean isSequence(String pin, int step) {
        for (int i = 1; i < pin.length(); i++) {
            if (pin.charAt(i) - pin.charAt(i - 1) != step) {
                return false;
            }
        }
        return true;
    }
}

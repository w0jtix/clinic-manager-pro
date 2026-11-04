package com.clinic.clinicmanager.utils;

import com.clinic.clinicmanager.exceptions.ConflictException;
import com.clinic.clinicmanager.exceptions.ValidationException;
import lombok.experimental.UtilityClass;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
public class CategoryRules {

    public static final int MAX_NAME_LENGTH = 50;
    public static final String NAME_REQUIRED = "Nazwa kategorii jest wymagana.";
    public static final String NAME_TOO_LONG = "Nazwa kategorii może mieć maksymalnie " + MAX_NAME_LENGTH + " znaków.";
    public static final String NAME_TAKEN = "Kategoria o tej nazwie już istnieje.";
    public static final String COLOR_INVALID = "Niepoprawny format koloru.";
    public static final String COLOR_TAKEN = "Ten kolor ma już inna kategoria.";

    private static final Pattern RGB_COLOR = Pattern.compile("(\\d{1,3}),(\\d{1,3}),(\\d{1,3})");
    private static final int MAX_COLOR_COMPONENT = 255;

    public static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new ValidationException(NAME_REQUIRED);
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(NAME_TOO_LONG);
        }
        return trimmed;
    }

    public static String normalizeColor(String color) {
        String normalized = color == null ? "" : color.replaceAll("\\s", "");
        Matcher matcher = RGB_COLOR.matcher(normalized);
        if (!matcher.matches()) {
            throw new ValidationException(COLOR_INVALID);
        }
        for (int group = 1; group <= matcher.groupCount(); group++) {
            if (Integer.parseInt(matcher.group(group)) > MAX_COLOR_COMPONENT) {
                throw new ValidationException(COLOR_INVALID);
            }
        }
        return normalized;
    }

    public static ConflictException uniqueViolationConflict(DataIntegrityViolationException e, String colorIndexName) {
        String message = e.getMostSpecificCause().getMessage();
        boolean colorViolation = message != null && message.contains(colorIndexName + "\"");
        return new ConflictException(colorViolation ? COLOR_TAKEN : NAME_TAKEN, e);
    }
}

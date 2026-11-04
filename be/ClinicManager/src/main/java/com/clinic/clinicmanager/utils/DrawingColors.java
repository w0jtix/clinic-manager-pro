package com.clinic.clinicmanager.utils;

import lombok.experimental.UtilityClass;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@UtilityClass
public class DrawingColors {

    public static final List<String> PALETTE = List.of(
            "#2563eb", "#dc2626", "#16a34a", "#9333ea", "#ea580c",
            "#0d9488", "#db2777", "#92400e", "#4f46e5", "#65a30d");

    public static final String DEFAULT_COLOR = "#6b7280";

    private static final Pattern HEX_COLOR = Pattern.compile("#[0-9a-fA-F]{6}");

    public static String pick(List<String> usedColors) {
        Map<String, Long> usage = usedColors.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return PALETTE.stream()
                .min(Comparator.comparingLong(color -> usage.getOrDefault(color, 0L)))
                .orElseThrow();
    }

    public static String normalizeHex(String color) {
        if (color == null || !HEX_COLOR.matcher(color).matches()) {
            return null;
        }
        return color.toLowerCase(Locale.ROOT);
    }
}

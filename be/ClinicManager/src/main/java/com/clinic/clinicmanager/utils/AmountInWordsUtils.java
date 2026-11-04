package com.clinic.clinicmanager.utils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class AmountInWordsUtils {

    private static final String[] UNITS = {
            "zero", "jeden", "dwa", "trzy", "cztery", "pięć", "sześć", "siedem", "osiem", "dziewięć",
            "dziesięć", "jedenaście", "dwanaście", "trzynaście", "czternaście", "piętnaście",
            "szesnaście", "siedemnaście", "osiemnaście", "dziewiętnaście"};

    private static final String[] TENS = {
            "", "", "dwadzieścia", "trzydzieści", "czterdzieści", "pięćdziesiąt",
            "sześćdziesiąt", "siedemdziesiąt", "osiemdziesiąt", "dziewięćdziesiąt"};

    private static final String[] HUNDREDS = {
            "", "sto", "dwieście", "trzysta", "czterysta", "pięćset",
            "sześćset", "siedemset", "osiemset", "dziewięćset"};

    private static final String[][] SCALES = {
            {"", "", ""},
            {"tysiąc", "tysiące", "tysięcy"},
            {"milion", "miliony", "milionów"},
            {"miliard", "miliardy", "miliardów"}};

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private AmountInWordsUtils() {
    }

    public static String amountInWords(BigDecimal amount) {
        BigDecimal value = MoneyUtils.money(amount);
        boolean negative = value.signum() < 0;
        value = value.abs();

        long zlote = value.longValue();
        int grosze = value.subtract(BigDecimal.valueOf(zlote)).multiply(ONE_HUNDRED).intValue();

        String words = numberToWords(zlote) + " PLN " + numberToWords(grosze) + " gr";
        return negative ? "minus " + words : words;
    }

    static String numberToWords(long number) {
        if (number == 0) {
            return UNITS[0];
        }

        List<Long> groups = new ArrayList<>();
        long rest = number;
        while (rest > 0) {
            groups.add(rest % 1000);
            rest /= 1000;
        }

        List<String> parts = new ArrayList<>();
        for (int scale = groups.size() - 1; scale >= 0; scale--) {
            long group = groups.get(scale);
            if (group == 0) {
                continue;
            }
            if (scale == 0) {
                parts.add(threeDigitsToWords(group));
                continue;
            }
            if (group != 1) {
                parts.add(threeDigitsToWords(group));
            }
            parts.add(SCALES[Math.min(scale, SCALES.length - 1)][pluralIndex(group)]);
        }

        return String.join(" ", parts);
    }

    private static String threeDigitsToWords(long number) {
        List<String> parts = new ArrayList<>();

        int hundreds = (int) (number / 100);
        if (hundreds > 0) {
            parts.add(HUNDREDS[hundreds]);
        }

        int remainder = (int) (number % 100);
        if (remainder >= 10 && remainder <= 19) {
            parts.add(UNITS[remainder]);
        } else {
            int tens = remainder / 10;
            int units = remainder % 10;
            if (tens > 0) {
                parts.add(TENS[tens]);
            }
            if (units > 0) {
                parts.add(UNITS[units]);
            }
        }

        return String.join(" ", parts);
    }

    private static int pluralIndex(long number) {
        if (number == 1) {
            return 0;
        }
        long lastDigit = number % 10;
        long lastTwoDigits = number % 100;
        if (lastDigit >= 2 && lastDigit <= 4 && !(lastTwoDigits >= 12 && lastTwoDigits <= 14)) {
            return 1;
        }
        return 2;
    }
}

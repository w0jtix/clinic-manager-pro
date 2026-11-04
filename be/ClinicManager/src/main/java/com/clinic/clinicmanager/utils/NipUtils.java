package com.clinic.clinicmanager.utils;

import java.util.Locale;
import java.util.regex.Pattern;

public final class NipUtils {

    private static final int[] WEIGHTS = {6, 5, 7, 2, 3, 4, 5, 6, 7};
    private static final int NIP_LENGTH = 10;
    private static final String POLISH_PREFIX = "PL";
    private static final Pattern PREFIXED_POLISH_NIP = Pattern.compile("^PL\\d{10}$");
    private static final Pattern FOREIGN_VAT_ID = Pattern.compile("^[A-Z]{2}[A-Z0-9]{2,12}$");
    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d+$");

    private NipUtils() {
    }

    public static String normalize(String nip) {
        if (nip == null) {
            return null;
        }
        return nip.trim().replaceAll("[\\s-]", "");
    }

    public static boolean isValid(String nip) {
        if (nip == null || nip.length() != NIP_LENGTH || !nip.chars().allMatch(c -> c >= '0' && c <= '9')) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += WEIGHTS[i] * (nip.charAt(i) - '0');
        }
        int checksum = sum % 11;
        return checksum != 10 && checksum == nip.charAt(NIP_LENGTH - 1) - '0';
    }

    public static String normalizeTaxId(String taxId) {
        if (taxId == null) {
            return null;
        }
        String normalized = taxId.trim().replaceAll("[\\s.-]", "").toUpperCase(Locale.ROOT);
        if (PREFIXED_POLISH_NIP.matcher(normalized).matches()) {
            return normalized.substring(POLISH_PREFIX.length());
        }
        return normalized;
    }

    public static boolean isValidTaxId(String taxId) {
        if (taxId == null || taxId.isEmpty()) {
            return false;
        }
        if (DIGITS_ONLY.matcher(taxId).matches()) {
            return isValid(taxId);
        }
        return FOREIGN_VAT_ID.matcher(taxId).matches() && !taxId.startsWith(POLISH_PREFIX);
    }
}

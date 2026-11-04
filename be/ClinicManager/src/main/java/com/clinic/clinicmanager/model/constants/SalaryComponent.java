package com.clinic.clinicmanager.model.constants;

public enum SalaryComponent {
    BASE,
    BONUS,
    SALE_BONUS;

    public static SalaryComponent fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return SalaryComponent.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

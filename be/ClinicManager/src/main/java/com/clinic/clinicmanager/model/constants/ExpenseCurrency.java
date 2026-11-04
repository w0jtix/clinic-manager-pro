package com.clinic.clinicmanager.model.constants;

public enum ExpenseCurrency {
    PLN,
    EUR,
    USD;

    public boolean isForeign() {
        return this != PLN;
    }
}

package com.clinic.clinicmanager.model.constants;

public enum KsefCorrectionMatchType {
    UU_ID,
    LINE,
    GTIN,
    NAME,
    AMBIGUOUS_CONSISTENT,
    AMBIGUOUS,
    NONE;

    public boolean isResolved() {
        return this != AMBIGUOUS && this != NONE;
    }
}

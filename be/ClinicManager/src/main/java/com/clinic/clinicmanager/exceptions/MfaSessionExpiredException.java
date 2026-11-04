package com.clinic.clinicmanager.exceptions;

public class MfaSessionExpiredException extends RuntimeException {

    public static final String CODE = "MFA_SESSION_EXPIRED";
    public static final String MESSAGE = "Sesja weryfikacji wygasła. Zaloguj się ponownie.";

    public MfaSessionExpiredException() {
        super(MESSAGE);
    }
}

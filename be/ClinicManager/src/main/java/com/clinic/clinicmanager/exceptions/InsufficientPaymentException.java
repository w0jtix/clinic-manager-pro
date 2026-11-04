package com.clinic.clinicmanager.exceptions;

public class InsufficientPaymentException extends RuntimeException {
    public InsufficientPaymentException(String message) {
        super(message);
    }
    public InsufficientPaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}

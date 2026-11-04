package com.clinic.clinicmanager.exceptions;

public class AuditException extends RuntimeException {
    public AuditException(String message) {
        super(message);
    }
    public AuditException(String message, Throwable cause) {
        super(message, cause);
    }
}

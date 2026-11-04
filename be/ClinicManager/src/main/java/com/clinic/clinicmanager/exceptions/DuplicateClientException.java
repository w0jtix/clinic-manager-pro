package com.clinic.clinicmanager.exceptions;

import lombok.Getter;

@Getter
public class DuplicateClientException extends RuntimeException {

    private final Long clientId;
    private final boolean deleted;
    private final boolean withoutPhone;
    private final String firstName;
    private final String lastName;

    public DuplicateClientException(Long clientId, boolean deleted, boolean withoutPhone, String firstName, String lastName) {
        super("Client with identical data already exists (id: " + clientId + ")");
        this.clientId = clientId;
        this.deleted = deleted;
        this.withoutPhone = withoutPhone;
        this.firstName = firstName;
        this.lastName = lastName;
    }
}

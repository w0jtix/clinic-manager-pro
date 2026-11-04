package com.clinic.clinicmanager.DTO.response;

import com.clinic.clinicmanager.exceptions.DuplicateClientException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DuplicateClientResponse {

    public static final String CODE = "DUPLICATE_CLIENT";

    private String code;
    private Long clientId;
    private boolean deleted;
    private boolean withoutPhone;
    private String firstName;
    private String lastName;

    public DuplicateClientResponse(DuplicateClientException ex) {
        this.code = CODE;
        this.clientId = ex.getClientId();
        this.deleted = ex.isDeleted();
        this.withoutPhone = ex.isWithoutPhone();
        this.firstName = ex.getFirstName();
        this.lastName = ex.getLastName();
    }
}

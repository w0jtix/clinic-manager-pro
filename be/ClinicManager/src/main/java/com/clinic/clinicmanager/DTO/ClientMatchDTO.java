package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.constants.ClientMatchReason;
import com.clinic.clinicmanager.repo.projection.ClientMatchProjection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClientMatchDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private String phoneNumber;
    private LocalDate lastVisitDate;
    private Boolean isDeleted;
    private ClientMatchReason matchReason;

    public ClientMatchDTO(ClientMatchProjection row) {
        this.id = row.getId();
        this.firstName = row.getFirstName();
        this.lastName = row.getLastName();
        this.birthDate = row.getBirthDate();
        this.phoneNumber = row.getPhoneNumber();
        this.lastVisitDate = row.getLastVisitDate();
        this.isDeleted = row.getIsDeleted();
        this.matchReason = ClientMatchReason.valueOf(row.getMatchReason());
    }
}

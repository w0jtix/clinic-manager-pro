package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.repo.projection.ClientCardListProjection;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClientCardListItemDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private String phoneNumber;
    private Boolean isDeleted;
    private LocalDate lastVisitDate;
    private Boolean lastVisitAbsence;

    public ClientCardListItemDTO(ClientCardListProjection row) {
        this.id = row.getId();
        this.firstName = row.getFirstName();
        this.lastName = row.getLastName();
        this.birthDate = row.getBirthDate();
        this.phoneNumber = row.getPhoneNumber();
        this.isDeleted = row.getIsDeleted();
        this.lastVisitDate = row.getLastVisitDate();
        this.lastVisitAbsence = row.getLastVisitAbsence();
    }
}

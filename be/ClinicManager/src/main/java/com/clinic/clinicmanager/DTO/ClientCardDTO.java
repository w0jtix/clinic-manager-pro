package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.Client;
import com.clinic.clinicmanager.model.ClientCard;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClientCardDTO {
    private Long clientId;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private String phoneNumber;
    private Boolean redFlag;
    private Boolean signedRegulations;
    private String remarks;
    private Boolean isDeleted;
    private LocalDate lastVisitDate;
    private LocalDate lastAbsenceUnpaidDate;
    private LocalDateTime cardCreatedAt;

    public ClientCardDTO(Client client, ClientCard card, LocalDate lastVisitDate, LocalDate lastAbsenceUnpaidDate) {
        this.clientId = client.getId();
        this.firstName = client.getFirstName();
        this.lastName = client.getLastName();
        this.birthDate = client.getBirthDate();
        this.phoneNumber = client.getPhoneNumber();
        this.redFlag = client.getRedFlag();
        this.signedRegulations = client.getSignedRegulations();
        this.remarks = client.getRemarks();
        this.isDeleted = client.getIsDeleted();
        this.lastVisitDate = lastVisitDate;
        this.lastAbsenceUnpaidDate = lastAbsenceUnpaidDate;
        this.cardCreatedAt = card != null ? card.getCreatedAt() : null;
    }
}

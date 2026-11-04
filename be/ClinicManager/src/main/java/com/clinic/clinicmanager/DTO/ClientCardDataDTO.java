package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.DTO.validation.NotBefore;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClientCardDataDTO {

    @NotBlank
    @Size(max = 255)
    private String firstName;

    @NotBlank
    @Size(max = 255)
    private String lastName;

    @PastOrPresent
    @NotBefore("1900-01-01")
    private LocalDate birthDate;

    @Size(max = 30)
    private String phoneNumber;

    private Boolean redFlag;

    private Boolean signedRegulations;

    @Size(max = 2000)
    private String remarks;

    public ClientDTO toClientDTO() {
        ClientDTO client = new ClientDTO();
        client.setFirstName(this.firstName);
        client.setLastName(this.lastName);
        client.setBirthDate(this.birthDate);
        client.setPhoneNumber(this.phoneNumber);
        client.setSignedRegulations(Boolean.TRUE.equals(this.signedRegulations));
        client.setBoostClient(false);
        client.setRedFlag(false);
        client.setIsDeleted(false);
        return client;
    }
}

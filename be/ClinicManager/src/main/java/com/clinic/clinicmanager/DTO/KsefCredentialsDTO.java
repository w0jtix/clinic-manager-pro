package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.KsefCredentials;
import com.clinic.clinicmanager.utils.AesEncryptionUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class KsefCredentialsDTO {

    private String nip;
    private String token;

    public KsefCredentialsDTO(KsefCredentials credentials) {
        this.nip = credentials.getNip();
        this.token = "****";
    }

    public KsefCredentials toEntity(AesEncryptionUtil encryptionUtil) {
        return KsefCredentials.builder()
                .id(1L)
                .nip(this.nip)
                .encryptedToken(encryptionUtil.encrypt(this.token))
                .build();
    }
}

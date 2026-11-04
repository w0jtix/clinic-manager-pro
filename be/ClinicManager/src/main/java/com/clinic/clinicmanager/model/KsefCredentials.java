package com.clinic.clinicmanager.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "ksef_credentials")
public class KsefCredentials {

    @Id
    private Long id;

    @Column(nullable = false, length = 10)
    private String nip;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String encryptedToken;
}

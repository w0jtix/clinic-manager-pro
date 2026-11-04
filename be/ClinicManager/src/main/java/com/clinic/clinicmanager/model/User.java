package com.clinic.clinicmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.clinic.clinicmanager.model.constants.CardTheme;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;
    @Column(nullable = false)
    private String password;
    @Column(nullable = true)
    private String avatar;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "users_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name="employee_id", nullable = true)
    private Employee employee;

    @JsonIgnore
    @Column(name = "company_pin_hash")
    private String companyPinHash;

    @JsonIgnore
    @Column(name = "mfa_secret_enc")
    private String mfaSecretEnc;

    @Column(name = "mfa_enabled_at")
    private LocalDateTime mfaEnabledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_theme", nullable = false, length = 10)
    @Builder.Default
    private CardTheme cardTheme = CardTheme.LIGHT;

    public boolean isMfaEnabled() {
        return mfaEnabledAt != null && mfaSecretEnc != null;
    }

}

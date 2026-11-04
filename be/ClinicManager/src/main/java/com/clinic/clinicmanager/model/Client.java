package com.clinic.clinicmanager.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.clinic.clinicmanager.model.converter.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "client")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = true)
    private String phoneNumber;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(nullable = true)
    private String email;

    @Column(nullable = true)
    private String address;

    @Column(name = "postal_code", nullable = true)
    private String postalCode;

    @Column(nullable = true)
    private String city;

    @Column (nullable = false)
    @Builder.Default
    private Boolean signedRegulations = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean boostClient = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean redFlag = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discount_id")
    private Discount discount;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @JsonIgnore
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "remarks", columnDefinition = "TEXT")
    private String remarks;

    public void softDelete() {
        this.isDeleted = true;
    }

    public void restore() {
        this.isDeleted = false;
    }
}

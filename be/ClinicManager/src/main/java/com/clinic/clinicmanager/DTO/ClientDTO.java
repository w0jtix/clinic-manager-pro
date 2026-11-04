package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.config.json.RoleRestricted;
import com.clinic.clinicmanager.model.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ClientDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private LocalDate birthDate;
    private String email;
    @RoleRestricted(AppRoleHierarchy.MASTER)
    private String address;
    @RoleRestricted(AppRoleHierarchy.MASTER)
    private String postalCode;
    @RoleRestricted(AppRoleHierarchy.MASTER)
    private String city;
    private Boolean signedRegulations;
    private Boolean boostClient;
    private Boolean redFlag;
    private DiscountDTO discount;
    private Boolean isDeleted;
    private Boolean hasDebts;
    private Long visitsCount;
    private Boolean hasActiveVoucher;
    private Boolean hasBooksyReview;
    private Boolean hasGoogleReview;
    private Boolean hasActiveGoogleReview;
    private Long createdBy; // user id

    public ClientDTO(Client client) {
        this.id = client.getId();
        this.firstName = client.getFirstName();
        this.lastName = client.getLastName();
        this.phoneNumber = client.getPhoneNumber();
        this.birthDate = client.getBirthDate();
        this.email = client.getEmail();
        this.address = client.getAddress();
        this.postalCode = client.getPostalCode();
        this.city = client.getCity();
        this.signedRegulations = client.getSignedRegulations();
        this.boostClient = client.getBoostClient();
        this.redFlag = client.getRedFlag();
        this.discount = client.getDiscount() != null ? new DiscountDTO(client.getDiscount()) : null;
        this.isDeleted = client.getIsDeleted();
        this.createdBy = client.getCreatedByUserId();
    }
    public ClientDTO(Client client,
                     Boolean hasDebts,
                     Long visitsCount,
                     Boolean hasActiveVoucher,
                     Boolean hasBooksyReview,
                     Boolean hasGoogleReview,
                     Boolean hasActiveGoogleReview) {
        this.id = client.getId();
        this.firstName = client.getFirstName();
        this.lastName = client.getLastName();
        this.phoneNumber = client.getPhoneNumber();
        this.birthDate = client.getBirthDate();
        this.email = client.getEmail();
        this.address = client.getAddress();
        this.postalCode = client.getPostalCode();
        this.city = client.getCity();
        this.signedRegulations = client.getSignedRegulations();
        this.boostClient = client.getBoostClient();
        this.redFlag = client.getRedFlag();
        this.discount = client.getDiscount() != null ? new DiscountDTO(client.getDiscount()) : null;
        this.isDeleted = client.getIsDeleted();
        this.hasDebts = hasDebts;
        this.visitsCount = visitsCount;
        this.hasActiveVoucher = hasActiveVoucher;
        this.hasBooksyReview = hasBooksyReview;
        this.hasGoogleReview = hasGoogleReview;
        this.hasActiveGoogleReview = hasActiveGoogleReview;
        this.createdBy = client.getCreatedByUserId();
    }

    public Client toEntity() {
        return Client.builder()
                .id(this.id)
                .firstName(this.firstName)
                .lastName(this.lastName)
                .phoneNumber(this.phoneNumber)
                .birthDate(this.birthDate)
                .email(this.email)
                .address(this.address)
                .postalCode(this.postalCode)
                .city(this.city)
                .signedRegulations(this.signedRegulations)
                .boostClient(this.boostClient)
                .redFlag(this.redFlag != null ? this.redFlag : false)
                .discount(this.discount != null ? this.discount.toEntity() : null)
                .isDeleted(this.isDeleted != null ? this.isDeleted : false)
                .createdByUserId(this.createdBy)
                .build();
    }

}

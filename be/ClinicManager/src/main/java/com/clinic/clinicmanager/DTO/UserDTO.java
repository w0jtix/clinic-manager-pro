package com.clinic.clinicmanager.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.clinic.clinicmanager.model.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserDTO {
    private Long id;
    private String username;
    private String avatar;
    private List<RoleDTO> roles = new ArrayList<>();
    private EmployeeSummaryDTO employee;
    private boolean hasCompanyPin;
    private boolean mfaEnabled;
    private LocalDateTime mfaEnabledAt;
    private int trustedDevicesCount;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String companyPin;

    public UserDTO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.avatar = user.getAvatar();
        if(nonNull(user.getRoles()))
            this.roles = user.getRoles().stream()
                    .map(RoleDTO::new)
                    .collect(Collectors.toList());
        if(nonNull(user.getEmployee()))
            this.employee = new EmployeeSummaryDTO(user.getEmployee());
        this.hasCompanyPin = nonNull(user.getCompanyPinHash());
        this.mfaEnabled = user.isMfaEnabled();
        this.mfaEnabledAt = this.mfaEnabled ? user.getMfaEnabledAt() : null;
    }

    public User toEntity() {
        return User.builder()
                .id(this.id)
                .username(this.username)
                .avatar(this.avatar)
                .roles(nonNull(this.roles) ? this.roles.stream().map(RoleDTO::toEntity).collect(Collectors.toSet()) : new HashSet<>())
                .employee(this.employee != null ? this.employee.toEntity() : null)
                .build();
    }
}
package com.clinic.clinicmanager.DTO.response;

import com.clinic.clinicmanager.DTO.EmployeeSummaryDTO;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class JwtResponse {
    private String token;
    private String type;
    private Long id;
    private String username;
    private String avatar;
    private List<String> roles;
    private EmployeeSummaryDTO employee;
    private boolean companyMode;
    private Integer remainingBackupCodes;

    public static JwtResponse of(String token, UserDetailsImpl userDetails, List<String> roles, boolean companyMode) {
        return JwtResponse.builder()
                .token(token)
                .type(JwtUtils.JWT_TYPE)
                .id(userDetails.getId())
                .username(userDetails.getUsername())
                .avatar(userDetails.getAvatar())
                .roles(roles)
                .employee(userDetails.getEmployee())
                .companyMode(companyMode)
                .build();
    }
}

package com.clinic.clinicmanager.DTO.response;

import com.clinic.clinicmanager.DTO.EmployeeSummaryDTO;
import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import lombok.Builder;
import lombok.Data;
import org.springframework.security.core.Authentication;

import java.util.List;

@Data
@Builder
public class CurrentUserResponse {
    private Long id;
    private String username;
    private String avatar;
    private List<String> roles;
    private EmployeeSummaryDTO employee;
    private boolean companyMode;

    public static CurrentUserResponse of(Authentication authentication) {
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        boolean companyMode = authentication.getAuthorities().stream()
                .anyMatch(authority -> AppRoleHierarchy.COMPANY.equals(authority.getAuthority()));
        return CurrentUserResponse.builder()
                .id(userDetails.getId())
                .username(userDetails.getUsername())
                .avatar(userDetails.getAvatar())
                .roles(AppRoleHierarchy.effectiveRoles(authentication.getAuthorities()))
                .employee(userDetails.getEmployee())
                .companyMode(companyMode)
                .build();
    }
}

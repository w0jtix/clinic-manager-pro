package com.clinic.clinicmanager.DTO.response;

import com.clinic.clinicmanager.DTO.EmployeeSummaryDTO;
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
}

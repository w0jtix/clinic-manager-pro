package com.clinic.clinicmanager.DTO;

import com.clinic.clinicmanager.model.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Objects.nonNull;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserAssignmentDTO {
    private Long id;
    private String username;
    private String avatar;
    private List<RoleDTO> roles = new ArrayList<>();
    private EmployeeSummaryDTO employee;

    public UserAssignmentDTO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.avatar = user.getAvatar();
        if (nonNull(user.getRoles()))
            this.roles = user.getRoles().stream()
                    .map(RoleDTO::new)
                    .collect(Collectors.toList());
        if (nonNull(user.getEmployee()))
            this.employee = new EmployeeSummaryDTO(user.getEmployee());
    }

    public UserAssignmentDTO(UserDTO user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.avatar = user.getAvatar();
        if (nonNull(user.getRoles()))
            this.roles = new ArrayList<>(user.getRoles());
        this.employee = user.getEmployee();
    }
}

package com.clinic.clinicmanager.repo.projection;

import com.clinic.clinicmanager.model.constants.RoleType;

public interface EmployeeAccountRoleProjection {
    Long getEmployeeId();
    Long getUserId();
    String getAvatar();
    RoleType getRoleName();
}

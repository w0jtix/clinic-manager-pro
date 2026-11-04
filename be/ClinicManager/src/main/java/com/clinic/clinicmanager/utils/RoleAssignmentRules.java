package com.clinic.clinicmanager.utils;

import com.clinic.clinicmanager.exceptions.ValidationException;
import com.clinic.clinicmanager.model.Role;
import com.clinic.clinicmanager.model.constants.RoleType;
import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass
public class RoleAssignmentRules {

    public static final String ADMIN_GRANT_FORBIDDEN = "Nie można nadać roli ADMIN.";
    public static final String ADMIN_REVOKE_FORBIDDEN = "Nie można zmienić roli ADMIN.";
    public static final String ADMIN_EDIT_FORBIDDEN = "Nie można edytować konta administratora.";
    public static final String BUSINESS_ROLE_REQUIRED = "Użytkownik musi mieć rolę USER lub MASTER.";

    public static boolean hasRole(Collection<Role> roles, RoleType roleType) {
        return roles != null && roles.stream().anyMatch(role -> role.getName() == roleType);
    }

    public static Set<Role> singleBusinessRole(Collection<Role> roles) {
        if (hasRole(roles, RoleType.ROLE_ADMIN)) {
            throw new ValidationException(ADMIN_GRANT_FORBIDDEN);
        }
        RoleType businessRole = hasRole(roles, RoleType.ROLE_MASTER) ? RoleType.ROLE_MASTER : RoleType.ROLE_USER;
        Set<Role> result = roles == null ? Set.of() : roles.stream()
                .filter(role -> role.getName() == businessRole)
                .limit(1)
                .collect(Collectors.toSet());
        if (result.isEmpty()) {
            throw new ValidationException(BUSINESS_ROLE_REQUIRED);
        }
        return result;
    }
}

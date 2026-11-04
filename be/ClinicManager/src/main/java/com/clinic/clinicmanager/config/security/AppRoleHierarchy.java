package com.clinic.clinicmanager.config.security;

import lombok.experimental.UtilityClass;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass
public class AppRoleHierarchy {

    public static final String ADMIN = "ROLE_ADMIN";
    public static final String COMPANY = "ROLE_COMPANY";
    public static final String MASTER = "ROLE_MASTER";
    public static final String USER = "ROLE_USER";

    private static final List<String> ORDERED_ROLES = List.of(ADMIN, COMPANY, MASTER, USER);

    public static final RoleHierarchy ROLE_HIERARCHY = RoleHierarchyImpl.withDefaultRolePrefix()
            .role("ADMIN").implies("COMPANY")
            .role("COMPANY").implies("MASTER")
            .role("MASTER").implies("USER")
            .build();

    public static boolean reaches(Collection<? extends GrantedAuthority> authorities, String role) {
        return reachableNames(authorities).contains(role);
    }

    public static boolean isGatedAccount(Collection<? extends GrantedAuthority> storedAuthorities) {
        if (storedAuthorities == null) {
            return true;
        }
        return storedAuthorities.stream()
                .map(GrantedAuthority::getAuthority)
                .noneMatch(authority -> ADMIN.equals(authority) || MASTER.equals(authority));
    }

    public static List<String> effectiveRoles(Collection<? extends GrantedAuthority> authorities) {
        Set<String> reachable = reachableNames(authorities);
        return ORDERED_ROLES.stream()
                .filter(reachable::contains)
                .collect(Collectors.toList());
    }

    public static String highestRole(Collection<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            return null;
        }
        return ORDERED_ROLES.stream()
                .filter(roleNames::contains)
                .findFirst()
                .orElse(null);
    }

    private static Set<String> reachableNames(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return Set.of();
        }
        return ROLE_HIERARCHY.getReachableGrantedAuthorities(authorities).stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}

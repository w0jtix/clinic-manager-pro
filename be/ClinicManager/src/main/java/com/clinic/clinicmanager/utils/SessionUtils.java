package com.clinic.clinicmanager.utils;

import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static java.util.Objects.nonNull;

@Slf4j
@UtilityClass
public class SessionUtils {

    public static Long getUserIdFromSession() {
        UserDetailsImpl principal = getUserDetailsFromSession();
        return principal != null ? principal.getId() : null;
    }

    public static UserDetailsImpl getUserDetailsFromSession() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        if (auth.getPrincipal() instanceof UserDetailsImpl principal) {
            return principal;
        }
        log.warn("Unexpected principal type in security context: {}",
                auth.getPrincipal() != null ? auth.getPrincipal().getClass().getName() : "null");
        return null;
    }

    public static UserDetailsImpl requirePrincipal() {
        UserDetailsImpl principal = getUserDetailsFromSession();
        if (principal == null) {
            throw new AccessDeniedException("Brak zalogowanego użytkownika.");
        }
        return principal;
    }

    public static boolean hasRole(String role) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return nonNull(auth) && AppRoleHierarchy.reaches(auth.getAuthorities(), role);
        } catch (Exception e) {
            log.error("hasRole broke by: ", e);
        }
        return false;
    }
}

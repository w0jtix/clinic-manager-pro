package com.clinic.clinicmanager.utils;

import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.model.User;
import com.clinic.clinicmanager.model.constants.RoleType;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static java.util.Objects.nonNull;

@Slf4j
@UtilityClass
public class SessionUtils {

    public static Long getUserIdFromSession() {
        try{
            return ((UserDetailsImpl) (SecurityContextHolder.getContext().getAuthentication().getPrincipal())).getId();
        } catch (Exception e) {
            log.error("getUserIdFromSession broke by: ", e);
        }
        return null;
    }

    public static UserDetailsImpl getUserDetailsFromSession() {
        try{
            return(UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        } catch(Exception e) {
            log.error("getUserDetailsFromSession broke by: ", e);
        }
        return null;
    }

    public static boolean hasUserRole(RoleType role) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return nonNull(auth) && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role.name()));
        } catch (Exception e) {
            log.error("hasUserRole broke by: ", e);
        }
        return false;
    }
}

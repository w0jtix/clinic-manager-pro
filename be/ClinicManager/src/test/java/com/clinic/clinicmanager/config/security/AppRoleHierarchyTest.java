package com.clinic.clinicmanager.config.security;

import com.clinic.clinicmanager.utils.SessionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AppRoleHierarchyTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static List<GrantedAuthority> authorities(String... roles) {
        return AuthorityUtils.createAuthorityList(roles);
    }

    @Test
    void admin_reachesCompanyMasterAndUser() {
        List<GrantedAuthority> admin = authorities(AppRoleHierarchy.ADMIN);

        assertThat(AppRoleHierarchy.reaches(admin, AppRoleHierarchy.COMPANY)).isTrue();
        assertThat(AppRoleHierarchy.reaches(admin, AppRoleHierarchy.MASTER)).isTrue();
        assertThat(AppRoleHierarchy.reaches(admin, AppRoleHierarchy.USER)).isTrue();
        assertThat(AppRoleHierarchy.effectiveRoles(admin))
                .containsExactly(AppRoleHierarchy.ADMIN, AppRoleHierarchy.COMPANY, AppRoleHierarchy.MASTER, AppRoleHierarchy.USER);
    }

    @Test
    void masterWithoutCompanyClaim_lacksCompanyAndAdmin() {
        List<GrantedAuthority> master = authorities(AppRoleHierarchy.MASTER);

        assertThat(AppRoleHierarchy.reaches(master, AppRoleHierarchy.COMPANY)).isFalse();
        assertThat(AppRoleHierarchy.reaches(master, AppRoleHierarchy.ADMIN)).isFalse();
        assertThat(AppRoleHierarchy.reaches(master, AppRoleHierarchy.USER)).isTrue();
        assertThat(AppRoleHierarchy.effectiveRoles(master))
                .containsExactly(AppRoleHierarchy.MASTER, AppRoleHierarchy.USER);
    }

    @Test
    void masterInCompanyMode_reachesCompanyButNotAdmin() {
        List<GrantedAuthority> company = authorities(AppRoleHierarchy.MASTER, AppRoleHierarchy.COMPANY);

        assertThat(AppRoleHierarchy.reaches(company, AppRoleHierarchy.COMPANY)).isTrue();
        assertThat(AppRoleHierarchy.reaches(company, AppRoleHierarchy.ADMIN)).isFalse();
        assertThat(AppRoleHierarchy.effectiveRoles(company))
                .containsExactly(AppRoleHierarchy.COMPANY, AppRoleHierarchy.MASTER, AppRoleHierarchy.USER);
    }

    @Test
    void user_reachesOnlyUser() {
        List<GrantedAuthority> user = authorities(AppRoleHierarchy.USER);

        assertThat(AppRoleHierarchy.reaches(user, AppRoleHierarchy.MASTER)).isFalse();
        assertThat(AppRoleHierarchy.effectiveRoles(user)).containsExactly(AppRoleHierarchy.USER);
    }

    @Test
    void effectiveRoles_returnsEmpty_whenNoAuthorities() {
        assertThat(AppRoleHierarchy.effectiveRoles(List.of())).isEmpty();
        assertThat(AppRoleHierarchy.reaches(null, AppRoleHierarchy.USER)).isFalse();
    }

    @Test
    void sessionHasRole_usesHierarchy() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null, authorities(AppRoleHierarchy.ADMIN, AppRoleHierarchy.USER)));

        assertThat(SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).isTrue();
        assertThat(SessionUtils.hasRole(AppRoleHierarchy.MASTER)).isTrue();
    }

    @Test
    void sessionHasRole_returnsFalse_forMasterWithoutCompany() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner", null, authorities(AppRoleHierarchy.MASTER)));

        assertThat(SessionUtils.hasRole(AppRoleHierarchy.MASTER)).isTrue();
        assertThat(SessionUtils.hasRole(AppRoleHierarchy.COMPANY)).isFalse();
    }

    @Test
    void sessionHasRole_returnsFalse_whenNotAuthenticated() {
        assertThat(SessionUtils.hasRole(AppRoleHierarchy.USER)).isFalse();
    }

    @Test
    void highestRole_returnsTopRoleByHierarchyOrder() {
        assertThat(AppRoleHierarchy.highestRole(Set.of(AppRoleHierarchy.USER, AppRoleHierarchy.MASTER))).isEqualTo(AppRoleHierarchy.MASTER);
        assertThat(AppRoleHierarchy.highestRole(Set.of(AppRoleHierarchy.USER, AppRoleHierarchy.ADMIN))).isEqualTo(AppRoleHierarchy.ADMIN);
        assertThat(AppRoleHierarchy.highestRole(Set.of(AppRoleHierarchy.MASTER, AppRoleHierarchy.COMPANY))).isEqualTo(AppRoleHierarchy.COMPANY);
        assertThat(AppRoleHierarchy.highestRole(Set.of(AppRoleHierarchy.USER))).isEqualTo(AppRoleHierarchy.USER);
    }

    @Test
    void highestRole_returnsNull_forEmptyNullOrUnknownRoles() {
        assertThat(AppRoleHierarchy.highestRole(Set.of())).isNull();
        assertThat(AppRoleHierarchy.highestRole(null)).isNull();
        assertThat(AppRoleHierarchy.highestRole(Set.of("ROLE_UNKNOWN"))).isNull();
    }
}

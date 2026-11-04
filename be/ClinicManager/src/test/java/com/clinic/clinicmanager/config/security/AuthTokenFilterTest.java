package com.clinic.clinicmanager.config.security;

import com.clinic.clinicmanager.config.security.jwt.AuthTokenFilter;
import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthTokenFilterTest {

    @Mock JwtUtils jwtUtils;
    @Mock UserDetailsServiceImpl userDetailsService;
    @Mock TokenBlacklistService tokenBlacklistService;

    @InjectMocks
    AuthTokenFilter filter;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Claims claims(boolean companyClaim) {
        Claims claims = Jwts.claims().setSubject("owner");
        if (companyClaim) {
            claims.put(JwtUtils.COMPANY_MODE_CLAIM, true);
        }
        return claims;
    }

    private Authentication authenticate(String token, boolean companyClaim, String... storedRoles) throws Exception {
        when(jwtUtils.parseValidClaims(token)).thenReturn(claims(companyClaim));
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(userDetailsService.loadUserByUsername("owner")).thenReturn(new UserDetailsImpl(7L, "owner", "hash", null, null,
                AuthorityUtils.createAuthorityList(storedRoles)));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }

    @Test
    void grantsCompany_whenClaimPresent_andMasterStored() throws Exception {
        Authentication auth = authenticate("elevated", true, AppRoleHierarchy.MASTER);

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(AppRoleHierarchy.MASTER, AppRoleHierarchy.COMPANY);
    }

    @Test
    void doesNotGrantCompany_whenMasterWithoutClaim() throws Exception {
        Authentication auth = authenticate("normal", false, AppRoleHierarchy.MASTER);

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly(AppRoleHierarchy.MASTER);
        assertThat(AppRoleHierarchy.reaches(auth.getAuthorities(), AppRoleHierarchy.COMPANY)).isFalse();
    }

    @Test
    void doesNotGrantCompany_whenClaimPresent_butMasterRoleRevoked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer elevated");
        when(jwtUtils.parseValidClaims("elevated")).thenReturn(claims(true));
        when(tokenBlacklistService.isBlacklisted("elevated")).thenReturn(false);
        when(userDetailsService.loadUserByUsername("owner")).thenReturn(new UserDetailsImpl(7L, "owner", "hash", null, null,
                AuthorityUtils.createAuthorityList(AppRoleHierarchy.USER)));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly(AppRoleHierarchy.USER);
    }

    @Test
    void doesNotAuthenticate_whenTokenBlacklisted() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer elevated");
        when(jwtUtils.parseValidClaims("elevated")).thenReturn(claims(true));
        when(tokenBlacklistService.isBlacklisted("elevated")).thenReturn(true);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doesNotAuthenticate_whenTokenInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer broken");
        when(jwtUtils.parseValidClaims("broken")).thenReturn(null);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(tokenBlacklistService, userDetailsService);
    }
}

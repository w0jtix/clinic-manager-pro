package com.clinic.clinicmanager.config.security.jwt;

import com.clinic.clinicmanager.config.security.AppRoleHierarchy;
import com.clinic.clinicmanager.config.security.services.UserDetailsServiceImpl;
import com.clinic.clinicmanager.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AuthTokenFilter extends OncePerRequestFilter {
    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    private static final Logger logger = LoggerFactory.getLogger(AuthTokenFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try{
            String jwt = parseJwt(request);
            Claims claims = jwt != null ? jwtUtils.parseValidClaims(jwt) : null;
            if(claims != null && !tokenBlacklistService.isBlacklisted(jwt)) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(claims.getSubject());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, resolveAuthorities(JwtUtils.isCompanyMode(claims), userDetails));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: ", e);
        }

        filterChain.doFilter(request, response);
    }

    private List<GrantedAuthority> resolveAuthorities(boolean companyModeToken, UserDetails userDetails) {
        List<GrantedAuthority> authorities = new ArrayList<>(userDetails.getAuthorities());
        boolean storedMaster = authorities.stream()
                .anyMatch(authority -> AppRoleHierarchy.MASTER.equals(authority.getAuthority()));
        if (storedMaster && companyModeToken) {
            authorities.add(new SimpleGrantedAuthority(AppRoleHierarchy.COMPANY));
        }
        return authorities;
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        if(StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}

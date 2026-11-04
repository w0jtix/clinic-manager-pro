package com.clinic.clinicmanager.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.DTO.response.CodedErrorResponse;
import com.clinic.clinicmanager.exceptions.AccessGateDeniedException;
import com.clinic.clinicmanager.service.impl.DeviceGate;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class DeviceGateFilter extends OncePerRequestFilter {

    private static final List<String> EXCLUDED_PATHS = List.of(
            "/api/auth/login",
            "/api/auth/logout",
            "/api/auth/device/register",
            "/api/auth/mfa/**",
            "/api/public/**"
    );
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final ObjectProvider<DeviceGate> deviceGate;
    private final ObjectMapper objectMapper;

    public DeviceGateFilter(ObjectProvider<DeviceGate> deviceGate, ObjectMapper objectMapper) {
        this.deviceGate = deviceGate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return EXCLUDED_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        DeviceGate gate = deviceGate.getIfAvailable();
        if (gate != null && gate.deniesRequest(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getOutputStream(), new CodedErrorResponse(
                    AccessGateDeniedException.DEVICE_NOT_ALLOWED, AccessGateDeniedException.MESSAGE));
            return;
        }
        filterChain.doFilter(request, response);
    }
}

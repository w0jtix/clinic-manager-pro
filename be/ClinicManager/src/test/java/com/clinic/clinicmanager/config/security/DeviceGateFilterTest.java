package com.clinic.clinicmanager.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clinic.clinicmanager.config.security.services.UserDetailsImpl;
import com.clinic.clinicmanager.service.AccessControlService;
import com.clinic.clinicmanager.service.impl.ClinicDeviceResolver;
import com.clinic.clinicmanager.service.impl.DeviceGate;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceGateFilterTest {

    @Mock AccessControlService accessControlService;
    @Mock ClinicDeviceResolver clinicDeviceResolver;
    @Mock ObjectProvider<DeviceGate> deviceGateProvider;
    @Mock FilterChain filterChain;

    private DeviceGateFilter filter;

    @BeforeEach
    void setUp() {
        lenient().when(deviceGateProvider.getIfAvailable()).thenReturn(new DeviceGate(accessControlService, clinicDeviceResolver));
        filter = new DeviceGateFilter(deviceGateProvider, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(String... roles) {
        UserDetailsImpl principal = new UserDetailsImpl(3L, "anna", "pass", null, null,
                Arrays.stream(roles).map(role -> new SimpleGrantedAuthority("ROLE_" + role)).collect(Collectors.toList()));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private static MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        return request;
    }

    @Test
    void shouldReturn403DeviceNotAllowed_whenGateActiveAndUserWithoutAllowedDevice() throws Exception {
        authenticateAs("USER");
        when(accessControlService.isGateActive()).thenReturn(true);
        when(clinicDeviceResolver.isAllowed(any())).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/api/clients/1"), response, filterChain);

        assertEquals(403, response.getStatus());
        assertEquals("{\"code\":\"DEVICE_NOT_ALLOWED\",\"message\":\"Brak dostępu\"}", response.getContentAsString());
        assertTrue(response.getContentType().startsWith("application/json"));
        verifyNoInteractions(filterChain);
    }

    @Test
    void shouldPass_whenUserHasAllowedDevice() throws Exception {
        authenticateAs("USER");
        when(accessControlService.isGateActive()).thenReturn(true);
        when(clinicDeviceResolver.isAllowed(any())).thenReturn(true);
        MockHttpServletRequest request = request("/api/cards/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldPass_whenGateInactive() throws Exception {
        authenticateAs("USER");
        when(accessControlService.isGateActive()).thenReturn(false);
        MockHttpServletRequest request = request("/api/clients/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(clinicDeviceResolver);
    }

    @Test
    void shouldExemptAdminAndMaster_evenWhenGateActive() throws Exception {
        for (String role : List.of("ADMIN", "MASTER")) {
            authenticateAs(role);
            MockHttpServletRequest request = request("/api/clients/1");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
        verifyNoInteractions(accessControlService, clinicDeviceResolver);
    }

    @Test
    void shouldPass_whenAnonymous() throws Exception {
        MockHttpServletRequest request = request("/api/clients/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(accessControlService, clinicDeviceResolver);
    }

    @Test
    void shouldSkipExcludedPaths() throws Exception {
        authenticateAs("USER");
        for (String path : List.of("/api/auth/login", "/api/auth/logout", "/api/auth/device/register",
                "/api/auth/mfa/verify", "/api/public/logo")) {
            MockHttpServletRequest request = request(path);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
        verifyNoInteractions(accessControlService, clinicDeviceResolver);
    }

    @Test
    void shouldGateAuthMe_forUserWithoutDevice() throws Exception {
        authenticateAs("USER");
        when(accessControlService.isGateActive()).thenReturn(true);
        when(clinicDeviceResolver.isAllowed(any())).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("/api/auth/me"), response, filterChain);

        assertEquals(403, response.getStatus());
    }

    @Test
    void shouldPass_whenGateComponentUnavailable() throws Exception {
        when(deviceGateProvider.getIfAvailable()).thenReturn(null);
        authenticateAs("USER");
        MockHttpServletRequest request = request("/api/clients/1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }
}

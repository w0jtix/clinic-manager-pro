package com.clinic.clinicmanager.config.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.time.Duration;

@Component
public class ClinicDeviceCookie {

    public static final String NAME = "pc_dev";
    public static final long MAX_AGE_DAYS = 400L;
    private static final String PATH = "/api";
    private static final String SAME_SITE = "Strict";

    private final boolean secure;

    public ClinicDeviceCookie(@Value("${app.device.cookie-secure:true}") boolean secure) {
        this.secure = secure;
    }

    public boolean isSecure() {
        return secure;
    }

    public String read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, NAME);
        return cookie != null ? cookie.getValue() : null;
    }

    public void write(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, Duration.ofDays(MAX_AGE_DAYS).getSeconds()).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", 0L).toString());
    }

    private ResponseCookie build(String value, long maxAgeSeconds) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE)
                .path(PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }
}

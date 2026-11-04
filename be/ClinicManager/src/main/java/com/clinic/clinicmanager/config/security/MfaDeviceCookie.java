package com.clinic.clinicmanager.config.security;

import com.clinic.clinicmanager.DTO.RememberedDeviceToken;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class MfaDeviceCookie {

    public static final String NAME_PREFIX = "pc_mfa_";
    private static final String PATH = "/api";
    private static final String SAME_SITE = "Strict";

    private final boolean secure;
    private final Clock businessClock;

    public MfaDeviceCookie(@Value("${app.mfa.cookie-secure:true}") boolean secure, Clock businessClock) {
        this.secure = secure;
        this.businessClock = businessClock;
    }

    public String name(Long userId) {
        return NAME_PREFIX + userId;
    }

    public String read(HttpServletRequest request, Long userId) {
        Cookie cookie = WebUtils.getCookie(request, name(userId));
        return cookie != null ? cookie.getValue() : null;
    }

    public void write(HttpServletResponse response, Long userId, RememberedDeviceToken token) {
        long maxAge = Math.max(0L, Duration.between(LocalDateTime.now(businessClock), token.expiresAt()).getSeconds());
        response.addHeader(HttpHeaders.SET_COOKIE, build(userId, token.token(), maxAge).toString());
    }

    public void clear(HttpServletResponse response, Long userId) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(userId, "", 0L).toString());
    }

    private ResponseCookie build(Long userId, String value, long maxAgeSeconds) {
        return ResponseCookie.from(name(userId), value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(SAME_SITE)
                .path(PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }
}

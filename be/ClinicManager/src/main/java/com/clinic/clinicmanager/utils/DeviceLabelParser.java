package com.clinic.clinicmanager.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DeviceLabelParser {

    public static final String UNKNOWN_DEVICE = "Nieznane urządzenie";

    public static String label(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN_DEVICE;
        }
        String ua = userAgent.toLowerCase();
        String browser = browser(ua);
        String system = system(ua);
        if (browser == null && system == null) {
            return UNKNOWN_DEVICE;
        }
        if (browser == null) {
            return system;
        }
        if (system == null) {
            return browser;
        }
        return browser + " · " + system;
    }

    private static String browser(String ua) {
        if (ua.contains("edg/") || ua.contains("edga/") || ua.contains("edgios/")) {
            return "Edge";
        }
        if (ua.contains("opr/") || ua.contains("opera")) {
            return "Opera";
        }
        if (ua.contains("samsungbrowser")) {
            return "Samsung Internet";
        }
        if (ua.contains("firefox/") || ua.contains("fxios/")) {
            return "Firefox";
        }
        if (ua.contains("chrome/") || ua.contains("crios/")) {
            return "Chrome";
        }
        if (ua.contains("safari/")) {
            return "Safari";
        }
        return null;
    }

    private static String system(String ua) {
        if (ua.contains("ipad")) {
            return "iPad";
        }
        if (ua.contains("iphone")) {
            return "iPhone";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("mac os x") || ua.contains("macintosh")) {
            return "macOS";
        }
        if (ua.contains("cros")) {
            return "ChromeOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }
        return null;
    }
}

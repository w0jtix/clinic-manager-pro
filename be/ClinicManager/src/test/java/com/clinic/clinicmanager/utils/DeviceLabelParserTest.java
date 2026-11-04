package com.clinic.clinicmanager.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeviceLabelParserTest {

    @Test
    void label_shouldRecognizeChromeOnWindows() {
        assertEquals("Chrome · Windows", DeviceLabelParser.label(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"));
    }

    @Test
    void label_shouldRecognizeSafariOnIpad() {
        assertEquals("Safari · iPad", DeviceLabelParser.label(
                "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"));
    }

    @Test
    void label_shouldRecognizeSafariOnIphone() {
        assertEquals("Safari · iPhone", DeviceLabelParser.label(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"));
    }

    @Test
    void label_shouldRecognizeEdgeBeforeChrome() {
        assertEquals("Edge · Windows", DeviceLabelParser.label(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0"));
    }

    @Test
    void label_shouldRecognizeFirefoxOnMac() {
        assertEquals("Firefox · macOS", DeviceLabelParser.label(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.5; rv:131.0) Gecko/20100101 Firefox/131.0"));
    }

    @Test
    void label_shouldFallBackToUnknownDevice() {
        assertEquals("Nieznane urządzenie", DeviceLabelParser.label(null));
        assertEquals("Nieznane urządzenie", DeviceLabelParser.label(""));
        assertEquals("Nieznane urządzenie", DeviceLabelParser.label("curl/8.4.0"));
    }
}

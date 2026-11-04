package com.clinic.clinicmanager.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ClinicDeviceSettings {

    private final Duration reuseGrace;

    public ClinicDeviceSettings(@Value("${app.device.reuse-grace-seconds:120}") long reuseGraceSeconds) {
        this.reuseGrace = Duration.ofSeconds(reuseGraceSeconds);
    }

    public Duration getReuseGrace() {
        return reuseGrace;
    }
}

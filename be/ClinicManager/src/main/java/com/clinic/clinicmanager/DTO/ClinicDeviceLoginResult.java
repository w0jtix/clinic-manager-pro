package com.clinic.clinicmanager.DTO;

public record ClinicDeviceLoginResult(Outcome outcome, String token, String deviceName) {

    public enum Outcome {
        NONE,
        ROTATED,
        REUSED,
        INVALID
    }

    public static ClinicDeviceLoginResult none() {
        return new ClinicDeviceLoginResult(Outcome.NONE, null, null);
    }

    public static ClinicDeviceLoginResult rotated(String token, String deviceName) {
        return new ClinicDeviceLoginResult(Outcome.ROTATED, token, deviceName);
    }

    public static ClinicDeviceLoginResult reused(String deviceName) {
        return new ClinicDeviceLoginResult(Outcome.REUSED, null, deviceName);
    }

    public static ClinicDeviceLoginResult invalid() {
        return new ClinicDeviceLoginResult(Outcome.INVALID, null, null);
    }
}

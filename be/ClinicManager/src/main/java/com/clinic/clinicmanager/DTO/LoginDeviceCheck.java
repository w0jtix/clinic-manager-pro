package com.clinic.clinicmanager.DTO;

public record LoginDeviceCheck(Status status, Long deviceId, String deviceName, boolean previousTokenReuse) {

    public enum Status {
        ALLOWED,
        UNKNOWN_DEVICE,
        SUSPECTED_COPY,
        REVOKED
    }

    public static LoginDeviceCheck unknown() {
        return new LoginDeviceCheck(Status.UNKNOWN_DEVICE, null, null, false);
    }

    public boolean allowed() {
        return status == Status.ALLOWED;
    }
}

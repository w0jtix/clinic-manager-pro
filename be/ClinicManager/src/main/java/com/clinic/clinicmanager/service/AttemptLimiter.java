package com.clinic.clinicmanager.service;

import java.util.OptionalInt;

public interface AttemptLimiter {

    boolean isBlocked(Long userId);

    long getBlockRemainingMinutes(Long userId);

    OptionalInt reserveAttempt(Long userId);

    int recordFailure(Long userId, String username, int attempt);

    void recordSuccess(Long userId);

    void reset(Long userId);

}

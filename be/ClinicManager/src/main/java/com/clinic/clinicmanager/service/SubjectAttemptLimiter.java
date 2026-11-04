package com.clinic.clinicmanager.service;

import java.util.OptionalInt;

public interface SubjectAttemptLimiter {

    static String userSubject(Long userId) {
        return "user:" + userId;
    }

    static String ipSubject(String ip) {
        return "ip:" + ip;
    }

    boolean isBlocked(String subject);

    long getBlockRemainingMinutes(String subject);

    OptionalInt reserveAttempt(String subject);

    int recordFailure(String subject, int attempt);

    void recordSuccess(String subject);

    void reset(String subject);

}

package com.clinic.clinicmanager.utils;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.util.Utils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.OptionalLong;

@Component
@RequiredArgsConstructor
public class TotpUtil {

    public static final String ISSUER = "ClinicManager";
    public static final int PERIOD_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int ALLOWED_DRIFT_STEPS = 1;
    private static final HashingAlgorithm ALGORITHM = HashingAlgorithm.SHA1;

    private final Clock businessClock;
    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator(ALGORITHM, DIGITS);

    public String generateSecret() {
        return secretGenerator.generate();
    }

    public String otpauthUri(String secret, String username) {
        return qrData(secret, username).getUri();
    }

    public String qrDataUri(String secret, String username) {
        QrGenerator generator = new ZxingPngQrGenerator();
        try {
            return Utils.getDataUriForImage(generator.generate(qrData(secret, username)), generator.getImageMimeType());
        } catch (QrGenerationException e) {
            throw new IllegalStateException("Failed to generate TOTP QR code", e);
        }
    }

    public OptionalLong matchStep(String secret, String code) {
        if (secret == null || code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        byte[] given = code.getBytes(StandardCharsets.US_ASCII);
        long current = currentStep();
        for (long step = current - ALLOWED_DRIFT_STEPS; step <= current + ALLOWED_DRIFT_STEPS; step++) {
            if (MessageDigest.isEqual(given, codeForStep(secret, step).getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    public long currentStep() {
        return businessClock.instant().getEpochSecond() / PERIOD_SECONDS;
    }

    public String codeForStep(String secret, long step) {
        try {
            return codeGenerator.generate(secret, step);
        } catch (CodeGenerationException e) {
            throw new IllegalStateException("Failed to generate TOTP code", e);
        }
    }

    private QrData qrData(String secret, String username) {
        return new QrData.Builder()
                .label(username)
                .secret(secret)
                .issuer(ISSUER)
                .algorithm(ALGORITHM)
                .digits(DIGITS)
                .period(PERIOD_SECONDS)
                .build();
    }
}

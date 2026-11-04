package com.clinic.clinicmanager.config.security;

import com.clinic.clinicmanager.config.security.jwt.JwtUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", "dGVzdC1qd3Qtc2VjcmV0LWZvci11bml0LXRlc3RzLW9ubHk=");
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 60000);
    }

    @Test
    void companyModeToken_carriesClaim() {
        String token = jwtUtils.generateJwtToken("owner", true);

        Claims claims = jwtUtils.parseValidClaims(token);

        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo("owner");
        assertThat(JwtUtils.isCompanyMode(claims)).isTrue();
        assertThat(jwtUtils.isCompanyModeToken(token)).isTrue();
    }

    @Test
    void normalToken_hasNoCompanyClaim() {
        String token = jwtUtils.generateJwtToken("owner", false);

        assertThat(jwtUtils.isCompanyModeToken(token)).isFalse();
        assertThat(JwtUtils.isCompanyMode(jwtUtils.parseValidClaims(token))).isFalse();
        assertThat(jwtUtils.getRemainingExpirationMs(token)).isPositive();
    }

    @Test
    void parseValidClaims_returnsNull_whenTokenTampered() {
        String token = jwtUtils.generateJwtToken("owner", false);

        assertThat(jwtUtils.parseValidClaims(token + "x")).isNull();
        assertThat(jwtUtils.parseValidClaims("not-a-jwt")).isNull();
        assertThat(jwtUtils.parseValidClaims("")).isNull();
    }

    @Test
    void parseValidClaims_returnsNull_whenTokenExpired() {
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", -1000);
        String token = jwtUtils.generateJwtToken("owner", false);

        assertThat(jwtUtils.parseValidClaims(token)).isNull();
    }

    @Test
    void tokensIssuedInSameSecond_areUnique() {
        String first = jwtUtils.generateJwtToken("owner", false);
        String second = jwtUtils.generateJwtToken("owner", false);

        assertThat(first).isNotEqualTo(second);
    }
}
